package dev.bluehouse.enablevolte.pages

import android.content.Intent
import android.telephony.SubscriptionInfo
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import dev.bluehouse.enablevolte.components.BottomBarSpacer
import dev.bluehouse.enablevolte.components.Chevron
import dev.bluehouse.enablevolte.components.PageEnd
import dev.bluehouse.enablevolte.ui.theme.Mono
import dev.bluehouse.enablevolte.components.ClearSwitch
import dev.bluehouse.enablevolte.components.ContentCard
import dev.bluehouse.enablevolte.components.GroupCard
import dev.bluehouse.enablevolte.components.GroupDivider
import dev.bluehouse.enablevolte.components.GroupLabel
import dev.bluehouse.enablevolte.components.HeroCard
import dev.bluehouse.enablevolte.components.ListRow
import dev.bluehouse.enablevolte.components.NoteBox
import dev.bluehouse.enablevolte.components.PageTitle
import dev.bluehouse.enablevolte.components.PrimaryPillButton
import dev.bluehouse.enablevolte.components.SimPicker
import dev.bluehouse.enablevolte.components.StatusPill
import dev.bluehouse.enablevolte.components.TonalPillButton
import dev.bluehouse.enablevolte.components.statusToneColor
import dev.bluehouse.enablevolte.FieldTestReporter
import dev.bluehouse.enablevolte.FieldTestResult
import dev.bluehouse.enablevolte.MobileRadioIsolation
import dev.bluehouse.enablevolte.MobileRadioIsolationSession
import dev.bluehouse.enablevolte.ModemLoggingStatus
import dev.bluehouse.enablevolte.PrivilegeManager
import dev.bluehouse.enablevolte.PrivilegeMode
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.RadioLogCaptureResult
import dev.bluehouse.enablevolte.RadioLogDigest
import dev.bluehouse.enablevolte.RadioLogExport
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
    // Subscriptions can arrive (or change) after this page opens; never stay pinned to a missing SIM.
    LaunchedEffect(subscriptions) {
        if (subscriptions.none { it.subscriptionId == selectedSubId }) {
            selectedSubId = subscriptions.firstOrNull()?.subscriptionId ?: -1
        }
    }
    // Subscriptions arrive after the first frame, so a saved -1 must not pin the page to "no SIM".
    val selectedSubscription = subscriptions.firstOrNull { it.subscriptionId == selectedSubId } ?: subscriptions.firstOrNull()
    // Not saveable: the test's coroutine does not survive the page being recreated, and a
    // restored "running" flag would leave both start buttons disabled with nothing running.
    var runningMode by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }
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
        modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle(stringResource(R.string.field_test_short))
        SimPicker(
            items = subscriptions.map { it.subscriptionId to it.uniqueName },
            selectedId = selectedSubscription?.subscriptionId,
            onSelect = { if (runningMode == null) selectedSubId = it },
            enabled = runningMode == null,
        )

        HeroCard(
            title = "Test this SIM",
            eyebrow = "Timed diagnostic report",
            body = stringResource(R.string.premium_field_description) + " " + stringResource(R.string.field_test_privacy),
        ) {
            if (runningMode != null) {
                LinearProgressIndicator(
                    progress = { progress.toFloat() / FieldTestReporter.SAMPLE_COUNT },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = if (runningMode == "aggressive") statusToneColor(StatusTone.WARNING) else MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (runningMode == "aggressive") {
                    TonalPillButton(
                        stringResource(R.string.stop_and_restore) + " · " + stringResource(R.string.field_test_progress, progress, FieldTestReporter.SAMPLE_COUNT),
                        { activeJob?.cancel() },
                        Modifier.weight(1f),
                        danger = true,
                    )
                } else {
                    PrimaryPillButton(
                        text = if (runningMode == "standard") {
                            stringResource(R.string.field_test_progress, progress, FieldTestReporter.SAMPLE_COUNT)
                        } else {
                            "Start test"
                        },
                        onClick = { start(aggressive = false) },
                        enabled = selectedSubscription != null && runningMode == null,
                        modifier = Modifier.weight(1f),
                    )
                    TonalPillButton(
                        "Aggressive",
                        { confirmAggressive = true },
                        enabled = selectedSubscription != null && runningMode == null,
                    )
                }
            }
            Text(
                "Aggressive opens every supported LTE/NR gate for two minutes with small data checks, then restores your settings. You are asked first.",
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        status?.let { NoteBox(it, StatusTone.ACCENT) }
        error?.let { NoteBox(it, StatusTone.DANGER) }
        result?.let { report ->
            GroupLabel("Last report")
            GroupCard {
                ListRow(report.fileName, subtitle = report.summary) {
                    StatusPill(stringResource(R.string.field_test_complete), StatusTone.SUCCESS)
                }
                GroupDivider()
                ListRow(stringResource(R.string.share_field_test), onClick = { share(report) }) { Chevron() }
            }
        }

        RadioLogCapturePanel()
        PageEnd()
    }
}

/**
 * Root-only capture of the vendor RIL and modem logs that explain an EN-DC decision.
 *
 * The capture is read-only. Modem DM logging is a separate switch that changes one vendor
 * property, only after the user confirms. The archive is saved to
 * Downloads next to Field Test reports and can be shared from here.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun RadioLogCapturePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootReady = PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()
    var redact by rememberSaveable { mutableStateOf(true) }
    // Not saveable, like the field test: the capture coroutine does not survive recreation.
    var busy by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<RadioLogCaptureResult?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var includeModem by rememberSaveable { mutableStateOf(false) }
    var modemStatus by remember { mutableStateOf<ModemLoggingStatus?>(null) }
    var modemBusy by remember { mutableStateOf(false) }
    var modemMessage by remember { mutableStateOf<String?>(null) }
    var confirmModem by remember { mutableStateOf(false) }
    val modemState = modemStatus?.state
    val modemSupported = modemState != null && modemState != RadioLogDigest.ModemLoggingState.UNSUPPORTED
    val modemOn = modemState == RadioLogDigest.ModemLoggingState.RUNNING ||
        modemState == RadioLogDigest.ModemLoggingState.STARTING

    LaunchedEffect(rootReady) {
        if (rootReady) modemStatus = runCatching { RadioLogExport.modemLoggingStatus() }.getOrNull()
    }

    fun switchModemLogging(enable: Boolean) {
        modemBusy = true
        modemMessage = null
        scope.launch {
            try {
                val status = RadioLogExport.setModemLogging(enable)
                modemStatus = status
                modemMessage = status.message.ifBlank { null }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                modemMessage = failure.message ?: failure.javaClass.simpleName
            } finally {
                modemBusy = false
            }
        }
    }

    if (confirmModem) {
        AlertDialog(
            onDismissRequest = { confirmModem = false },
            title = { Text(stringResource(R.string.modem_logging_confirm_title)) },
            text = { Text(stringResource(R.string.modem_logging_confirm_text)) },
            confirmButton = {
                Button(onClick = {
                    confirmModem = false
                    switchModemLogging(true)
                }) {
                    Text(stringResource(R.string.modem_logging_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmModem = false }) {
                    Text(stringResource(R.string.modem_logging_cancel))
                }
            },
        )
    }

    GroupLabel(stringResource(R.string.radio_logs_title) + " · " + stringResource(R.string.radio_logs_root_chip).lowercase())
    GroupCard {
        ListRow(
            title = stringResource(R.string.radio_logs_redact),
            subtitle = stringResource(R.string.radio_logs_redact_summary),
        ) {
            ClearSwitch(checked = redact, enabled = !busy, onCheckedChange = { redact = it })
        }
        if (rootReady) {
            GroupDivider()
            ListRow(
                title = stringResource(R.string.modem_logging_title),
                subtitle = stringResource(R.string.modem_logging_summary, modemStatus?.directory?.ifBlank { null } ?: "/data/vendor/slog"),
                below = {
                    Spacer(Modifier.size(4.dp))
                    StatusPill(
                        stringResource(
                            when (modemState) {
                                null -> R.string.modem_logging_state_unknown
                                RadioLogDigest.ModemLoggingState.UNSUPPORTED -> R.string.modem_logging_state_unsupported
                                RadioLogDigest.ModemLoggingState.OFF -> R.string.modem_logging_state_off
                                RadioLogDigest.ModemLoggingState.STARTING -> R.string.modem_logging_state_starting
                                RadioLogDigest.ModemLoggingState.RUNNING -> R.string.modem_logging_state_running
                                RadioLogDigest.ModemLoggingState.STOPPING -> R.string.modem_logging_state_stopping
                            },
                        ),
                        when (modemState) {
                            RadioLogDigest.ModemLoggingState.RUNNING -> StatusTone.SUCCESS
                            RadioLogDigest.ModemLoggingState.STARTING, RadioLogDigest.ModemLoggingState.STOPPING -> StatusTone.WARNING
                            else -> StatusTone.NEUTRAL
                        },
                        live = modemState == RadioLogDigest.ModemLoggingState.RUNNING,
                    )
                    modemStatus?.takeIf { modemSupported }?.let { status ->
                        Text(
                            stringResource(R.string.modem_logging_files, status.files, Formatter.formatShortFileSize(context, status.bytes)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (modemBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    modemMessage?.let { Text(it, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
            ) {
                ClearSwitch(
                    checked = modemOn,
                    enabled = modemSupported && !busy && !modemBusy,
                    onCheckedChange = { want -> if (want) confirmModem = true else switchModemLogging(false) },
                )
            }
            GroupDivider()
            ListRow(
                title = stringResource(R.string.modem_logging_include),
                subtitle = stringResource(R.string.modem_logging_include_summary),
            ) {
                ClearSwitch(
                    checked = includeModem && modemSupported,
                    enabled = modemSupported && !busy,
                    onCheckedChange = { includeModem = it },
                )
            }
        }
    }
    if (!redact) NoteBox(stringResource(R.string.radio_logs_unredacted_warning), StatusTone.DANGER)
    if (rootReady && includeModem && modemSupported) NoteBox(stringResource(R.string.modem_logging_include_warning), StatusTone.DANGER)
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    PrimaryPillButton(
        text = stringResource(if (busy) R.string.radio_logs_capturing else R.string.radio_logs_capture),
        onClick = {
            busy = true
            captureError = null
            capture = null
            scope.launch {
                try {
                    capture = RadioLogExport.capture(context, redact, includeModem && modemSupported)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    captureError = failure.message ?: failure.javaClass.simpleName
                } finally {
                    busy = false
                    modemStatus = runCatching { RadioLogExport.modemLoggingStatus() }.getOrNull() ?: modemStatus
                }
            }
        },
        enabled = rootReady && !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.radio_logs_description),
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
    if (!rootReady) NoteBox(stringResource(R.string.radio_logs_root_only), StatusTone.NEUTRAL)
    captureError?.let { NoteBox(it, StatusTone.DANGER) }
    capture?.let { result ->
        ContentCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.radio_logs_saved,
                        result.fileName,
                        result.fileCount,
                        Formatter.formatShortFileSize(context, result.bytes),
                    ),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(stringResource(R.string.field_test_complete), StatusTone.SUCCESS)
            }
            if (result.bootTime.isNotBlank()) {
                Text(stringResource(R.string.radio_logs_last_boot, result.bootTime), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result.rilFiles == 0) NoteBox(stringResource(R.string.radio_logs_no_ril, result.rilDirectory), StatusTone.WARNING)
            Text(stringResource(R.string.radio_logs_endc_events), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            if (result.endcEvents.isEmpty()) {
                Text(stringResource(R.string.radio_logs_endc_none), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                result.endcEvents.takeLast(8).forEach {
                    Text(it, fontSize = 10.5.sp, lineHeight = 15.sp, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.uecap.isNotEmpty()) {
                Text(stringResource(R.string.radio_logs_uecap), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                result.uecap.takeLast(4).forEach {
                    Text(it, fontSize = 10.5.sp, lineHeight = 15.sp, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.modemFiles > 0) {
                NoteBox(
                    stringResource(R.string.modem_logging_captured, result.modemFiles, Formatter.formatShortFileSize(context, result.modemBytes)),
                    StatusTone.DANGER,
                )
            }
            if (modemOn) NoteBox(stringResource(R.string.modem_logging_turn_off_reminder), StatusTone.WARNING)
            if (result.skipped > 0) {
                Text(stringResource(R.string.radio_logs_skipped, result.skipped), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TonalPillButton(
                stringResource(R.string.radio_logs_share),
                {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, result.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.radio_logs_share)))
                },
                small = true,
            )
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
