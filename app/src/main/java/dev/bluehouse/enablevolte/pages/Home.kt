package dev.bluehouse.enablevolte.pages

import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.CarrierModer
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.PrivilegeManager
import dev.bluehouse.enablevolte.PrivilegeMode
import dev.bluehouse.enablevolte.ShizukuStatus
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.checkShizukuPermission
import dev.bluehouse.enablevolte.components.Panel
import dev.bluehouse.enablevolte.components.PremiumActionRow
import dev.bluehouse.enablevolte.components.PremiumMetric
import dev.bluehouse.enablevolte.components.PremiumPageIntro
import dev.bluehouse.enablevolte.components.PremiumSectionLabel
import dev.bluehouse.enablevolte.components.PremiumStatusChip
import dev.bluehouse.enablevolte.components.StatusTone
import dev.bluehouse.enablevolte.uniqueName
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG = "HomeActivity:Home"

@Suppress("ktlint:standard:function-naming")
@Composable
fun Home(navController: NavController) {
    val carrierModer = CarrierModer(LocalContext.current)
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var shizukuEnabled by rememberSaveable { mutableStateOf(false) }
    var shizukuGranted by rememberSaveable { mutableStateOf(false) }
    var subscriptions by rememberSaveable { mutableStateOf(listOf<SubscriptionInfo>()) }
    var deviceIMSEnabled by rememberSaveable { mutableStateOf(false) }

    var isIMSRegistered by rememberSaveable { mutableStateOf(listOf<Boolean>()) }
    var imsIssues by remember { mutableStateOf(listOf<SubscriptionModer.ImsIssue>()) }
    val scope = rememberCoroutineScope()

    fun loadFlags() {
        shizukuGranted = true
        subscriptions = carrierModer.subscriptions
        deviceIMSEnabled = carrierModer.deviceSupportsIMS

        if (subscriptions.isNotEmpty() && deviceIMSEnabled) {
            val diagnoses = subscriptions.map { SubscriptionModer(context, it.subscriptionId).diagnoseIms() }
            isIMSRegistered = diagnoses.map { it.registered }
            imsIssues = diagnoses.map { it.issue }
        }
    }

    LaunchedEffect(Unit) {
        if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
            repeat(20) {
                if (PrivilegeManager.isRootReady()) {
                    shizukuEnabled = true
                    shizukuGranted = true
                    loadFlags()
                    return@LaunchedEffect
                }
                delay(250)
            }
            return@LaunchedEffect
        }
        try {
            when (checkShizukuPermission(0)) {
                ShizukuStatus.GRANTED -> {
                    shizukuEnabled = true
                    loadFlags()
                }
                ShizukuStatus.NOT_GRANTED -> {
                    shizukuEnabled = true
                    Shizuku.addRequestPermissionResultListener { _, grantResult ->
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            loadFlags()
                        }
                    }
                }
                else -> {
                    shizukuEnabled = false
                }
            }
        } catch (e: IllegalStateException) {
            shizukuEnabled = false
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PremiumPageIntro(
            eyebrow = stringResource(R.string.premium_home_eyebrow),
            title = stringResource(R.string.premium_home_title),
            description = stringResource(R.string.premium_home_description),
        )

        PremiumSectionLabel(stringResource(R.string.premium_system_readiness))
        Panel(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PremiumStatusChip(
                        label = if (shizukuGranted && subscriptions.isNotEmpty()) {
                            stringResource(R.string.premium_ready)
                        } else {
                            stringResource(R.string.premium_attention)
                        },
                        tone = if (shizukuGranted && subscriptions.isNotEmpty()) {
                            StatusTone.SUCCESS
                        } else {
                            StatusTone.WARNING
                        },
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    PremiumMetric(
                        label = stringResource(R.string.premium_access_mode),
                        value = PrivilegeManager.activeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                        tone = StatusTone.ACCENT,
                        modifier = Modifier.weight(1f),
                    )
                    PremiumMetric(
                        label = stringResource(R.string.premium_service),
                        value = if (shizukuEnabled) {
                            stringResource(R.string.premium_ready)
                        } else {
                            stringResource(R.string.premium_unavailable)
                        },
                        tone = if (shizukuEnabled) StatusTone.SUCCESS else StatusTone.DANGER,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    PremiumMetric(
                        label = stringResource(R.string.premium_sim),
                        value = if (subscriptions.isNotEmpty()) {
                            "${subscriptions.size} detected"
                        } else {
                            stringResource(R.string.premium_unavailable)
                        },
                        tone = if (subscriptions.isNotEmpty()) StatusTone.SUCCESS else StatusTone.WARNING,
                        modifier = Modifier.weight(1f),
                    )
                    PremiumMetric(
                        label = stringResource(R.string.premium_ims),
                        value = if (deviceIMSEnabled) {
                            stringResource(R.string.premium_supported)
                        } else {
                            stringResource(R.string.premium_unavailable)
                        },
                        tone = if (deviceIMSEnabled) StatusTone.SUCCESS else StatusTone.DANGER,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        PremiumSectionLabel(stringResource(R.string.premium_quick_start))
        PremiumActionRow(
            title = stringResource(R.string.open_how_to_use),
            subtitle = stringResource(R.string.how_to_use_summary),
            icon = Icons.Filled.MenuBook,
            onClick = { navController.navigate("home/how-to") },
        )

        for (idx in subscriptions.indices) {
            val subscription = subscriptions[idx]
            var isRegistered = false
            if (isIMSRegistered.isNotEmpty()) {
                isRegistered = isIMSRegistered[idx]
            }
            val reason = if (!isRegistered && idx < imsIssues.size) {
                when (imsIssues[idx]) {
                    SubscriptionModer.ImsIssue.NO_CELLULAR_SERVICE -> stringResource(R.string.ims_issue_no_service)
                    SubscriptionModer.ImsIssue.VOLTE_DISABLED_BY_CONFIG -> stringResource(R.string.ims_issue_volte_disabled)
                    SubscriptionModer.ImsIssue.LTE_NR_NOT_ALLOWED -> stringResource(R.string.ims_issue_radio_disabled)
                    SubscriptionModer.ImsIssue.CARRIER_PROVISIONING_OR_NETWORK -> stringResource(R.string.ims_issue_carrier)
                    SubscriptionModer.ImsIssue.STATUS_UNAVAILABLE -> stringResource(R.string.ims_issue_unknown)
                    else -> stringResource(R.string.registered)
                }
            } else {
                null
            }
            Panel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(subscription.uniqueName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.ims_status),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PremiumStatusChip(
                            label = if (isRegistered) {
                                stringResource(R.string.registered)
                            } else {
                                stringResource(R.string.unregistered)
                            },
                            tone = if (isRegistered) StatusTone.SUCCESS else StatusTone.DANGER,
                        )
                    }
                    reason?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    SubscriptionModer(context, subscription.subscriptionId).restoreGoogleDefaults()
                                }
                                loadFlags()
                            }
                        }) { Text(stringResource(R.string.fix_restore_google)) }
                    }
                }
            }
        }
    }
}
