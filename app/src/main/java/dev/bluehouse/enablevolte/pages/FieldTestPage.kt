package dev.bluehouse.enablevolte.pages

import android.content.Intent
import android.telephony.SubscriptionInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.FieldTestReporter
import dev.bluehouse.enablevolte.FieldTestResult
import dev.bluehouse.enablevolte.MobileRadioIsolation
import dev.bluehouse.enablevolte.MobileRadioIsolationSession
import dev.bluehouse.enablevolte.PrivilegeManager
import dev.bluehouse.enablevolte.PrivilegeMode
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.components.Panel
import dev.bluehouse.enablevolte.components.PremiumPageIntro
import dev.bluehouse.enablevolte.components.PremiumSectionLabel
import dev.bluehouse.enablevolte.components.PremiumStatusChip
import dev.bluehouse.enablevolte.components.StatusTone
import dev.bluehouse.enablevolte.uniqueName
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("ktlint:standard:function-naming")
@Composable
fun FieldTestPage(subscriptions: List<SubscriptionInfo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSubId by rememberSaveable { mutableStateOf(subscriptions.firstOrNull()?.subscriptionId ?: -1) }
    val selectedSubscription = subscriptions.firstOrNull { it.subscriptionId == selectedSubId }
    var runningMode by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by rememberSaveable { mutableStateOf(0) }
    var result by remember { mutableStateOf<FieldTestResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmAggressive by rememberSaveable { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    fun share(report: FieldTestResult) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, report.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_field_test)))
    }

    fun start(aggressive: Boolean) {
        val subscription = selectedSubscription ?: return
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            runningMode = if (aggressive) "aggressive" else "standard"
            progress = 0
            error = null
            status = null
            result = null
            val moder = SubscriptionModer(context, subscription.subscriptionId)
            var pulseJob: Job? = null
            var restoreRootForce = false
            var restoreShizukuSnapshot = false
            var restoreShizukuBands = true
            var wifiIsolationSession: MobileRadioIsolationSession? = null
            try {
                status = context.getString(R.string.mobile_only_disabling_wifi)
                val isolation = withContext(Dispatchers.IO) {
                    MobileRadioIsolation.begin()
                }
                wifiIsolationSession = isolation.session
                check(isolation.active) { isolation.message }
                status = isolation.message
                if (aggressive) {
                    status = context.getString(R.string.aggressive_opening_gates)
                    withContext(Dispatchers.IO) {
                        if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                            val alreadyActive = moder.getRootForceReport().active
                            if (!alreadyActive) {
                                moder.applyRootForce()
                                restoreRootForce = true
                            }
                        } else {
                            val applied = moder.applyShizukuRegionalCompatibility()
                            restoreShizukuSnapshot = true
                            restoreShizukuBands = applied.limitations.none {
                                it.startsWith("Automatic bands unchanged")
                            }
                            if (!applied.applied && applied.failedGates.isNotEmpty()) {
                                error = context.getString(
                                    R.string.aggressive_partial,
                                    applied.failedGates.joinToString(),
                                )
                            }
                        }
                    }
                    status = context.getString(R.string.aggressive_testing)
                    pulseJob = launch(Dispatchers.IO) {
                        while (isActive) {
                            runCatching { sendConnectivityPulse() }
                            delay(10_000)
                        }
                    }
                }
                result = FieldTestReporter.capture(
                    context = context,
                    subscription = subscription,
                    modeLabel = if (aggressive) {
                        "Aggressive 5G test; temporary Android-side gates plus opt-in traffic"
                    } else {
                        "Standard diagnostic; radio policy unchanged; Wi-Fi temporarily disabled"
                    },
                    wifiIsolation = isolation.message,
                ) { completed, _ ->
                    progress = completed
                }
                status = context.getString(R.string.field_test_complete)
            } catch (_: CancellationException) {
                status = context.getString(R.string.aggressive_stopped)
            } catch (failure: Throwable) {
                error = failure.message ?: context.getString(R.string.field_test_failed)
            } finally {
                // Stop cancels this coroutine. Keep cleanup non-cancellable so a
                // cancelled traffic pulse cannot skip the radio-settings rollback.
                withContext(NonCancellable) {
                    pulseJob?.cancelAndJoin()
                    if (aggressive) {
                        status = context.getString(R.string.aggressive_restoring)
                        val restored = withContext(Dispatchers.IO) {
                            when {
                                restoreRootForce -> moder.restoreRootForce()
                                restoreShizukuSnapshot -> moder.undoLastChange(restoreBands = restoreShizukuBands)
                                else -> true
                            }
                        }
                        status = if (restored) {
                            context.getString(R.string.aggressive_restored)
                        } else {
                            context.getString(R.string.aggressive_restore_incomplete)
                        }
                    }
                    val wifiRestored = wifiIsolationSession?.restore() ?: true
                    if (!wifiRestored) {
                        error = context.getString(R.string.mobile_only_wifi_restore_failed)
                    }
                    runningMode = null
                    activeJob = null
                }
            }
        }
    }

    if (confirmAggressive) {
        AlertDialog(
            onDismissRequest = { confirmAggressive = false },
            title = { Text(stringResource(R.string.aggressive_confirm_title)) },
            text = { Text(stringResource(R.string.aggressive_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    confirmAggressive = false
                    start(aggressive = true)
                }) {
                    Text(stringResource(R.string.start_aggressive_test))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmAggressive = false }) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PremiumPageIntro(
            eyebrow = stringResource(R.string.premium_field_eyebrow),
            title = stringResource(R.string.premium_field_title),
            description = stringResource(R.string.premium_field_description),
        )
        PremiumSectionLabel(stringResource(R.string.sim_detected))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subscriptions.forEach { subscription ->
                FilterChip(
                    selected = selectedSubId == subscription.subscriptionId,
                    onClick = { if (runningMode == null) selectedSubId = subscription.subscriptionId },
                    label = { Text(subscription.uniqueName) },
                )
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Science, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.standard_field_test), style = MaterialTheme.typography.titleLarge)
                    }
                    PremiumStatusChip(stringResource(R.string.premium_safe), StatusTone.SUCCESS)
                }
                Text(
                    stringResource(R.string.field_test_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (runningMode == "standard") {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() / FieldTestReporter.SAMPLE_COUNT },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    enabled = selectedSubscription != null && runningMode == null,
                    onClick = { start(aggressive = false) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (runningMode == "standard") {
                            stringResource(R.string.field_test_progress, progress, FieldTestReporter.SAMPLE_COUNT)
                        } else {
                            stringResource(R.string.start_field_test)
                        },
                    )
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            stringResource(R.string.aggressive_5g_test),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    PremiumStatusChip(stringResource(R.string.premium_expert), StatusTone.WARNING)
                }
                Text(
                    stringResource(R.string.aggressive_5g_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (runningMode == "aggressive") {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() / FieldTestReporter.SAMPLE_COUNT },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Button(
                        onClick = { activeJob?.cancel() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.stop_and_restore))
                    }
                    Text(stringResource(R.string.field_test_progress, progress, FieldTestReporter.SAMPLE_COUNT))
                } else {
                    Button(
                        enabled = selectedSubscription != null && runningMode == null,
                        onClick = { confirmAggressive = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.start_aggressive_test))
                    }
                }
                Text(
                    stringResource(R.string.aggressive_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (status != null || error != null) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    PremiumStatusChip(
                        label = if (error == null) {
                            stringResource(R.string.premium_active)
                        } else {
                            stringResource(R.string.premium_attention)
                        },
                        tone = if (error == null) StatusTone.ACCENT else StatusTone.DANGER,
                    )
                    status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        result?.let { report ->
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumStatusChip(stringResource(R.string.field_test_complete), StatusTone.SUCCESS)
                    Text(
                        stringResource(R.string.field_test_saved, report.fileName),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(report.summary, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { share(report) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text(stringResource(R.string.share_field_test))
                    }
                }
            }
        }
    }
}

private fun sendConnectivityPulse() {
    val connection = URL("https://connectivitycheck.gstatic.com/generate_204")
        .openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.useCaches = false
        connection.inputStream.use { it.read() }
    } finally {
        connection.disconnect()
    }
}
