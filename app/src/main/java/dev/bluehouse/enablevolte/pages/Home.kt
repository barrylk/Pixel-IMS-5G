package dev.bluehouse.enablevolte.pages

import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.CarrierModer
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.ShizukuStatus
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.checkShizukuPermission
import dev.bluehouse.enablevolte.components.BooleanPropertyView
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.components.GlassSurface
import dev.bluehouse.enablevolte.components.StringPropertyView
import dev.bluehouse.enablevolte.uniqueName
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
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

    Column(modifier = Modifier.padding(Dp(16f)).verticalScroll(scrollState)) {
        HeaderText(text = stringResource(R.string.version))
        StringPropertyView(label = BuildConfig.VERSION_NAME, value = stringResource(R.string.app_name))
        HeaderText(text = stringResource(R.string.permissions_capabilities))
        BooleanPropertyView(label = stringResource(R.string.shizuku_service_running), toggled = shizukuEnabled)
        BooleanPropertyView(label = stringResource(R.string.shizuku_permission_granted), toggled = shizukuGranted)
        BooleanPropertyView(label = stringResource(R.string.sim_detected), toggled = subscriptions.isNotEmpty())
        BooleanPropertyView(label = stringResource(R.string.volte_supported_by_device), toggled = deviceIMSEnabled)

        for (idx in subscriptions.indices) {
            var isRegistered = false
            if (isIMSRegistered.isNotEmpty()) {
                isRegistered = isIMSRegistered[idx]
            }
            HeaderText(text = stringResource(R.string.ims_status_for, subscriptions[idx].uniqueName))
            BooleanPropertyView(
                label = stringResource(R.string.ims_status),
                toggled = isRegistered,
                trueLabel = stringResource(R.string.registered),
                falseLabel = stringResource(R.string.unregistered),
            )
            if (!isRegistered && idx < imsIssues.size) {
                val reason = when (imsIssues[idx]) {
                    SubscriptionModer.ImsIssue.NO_CELLULAR_SERVICE -> stringResource(R.string.ims_issue_no_service)
                    SubscriptionModer.ImsIssue.VOLTE_DISABLED_BY_CONFIG -> stringResource(R.string.ims_issue_volte_disabled)
                    SubscriptionModer.ImsIssue.LTE_NR_NOT_ALLOWED -> stringResource(R.string.ims_issue_radio_disabled)
                    SubscriptionModer.ImsIssue.CARRIER_PROVISIONING_OR_NETWORK -> stringResource(R.string.ims_issue_carrier)
                    SubscriptionModer.ImsIssue.STATUS_UNAVAILABLE -> stringResource(R.string.ims_issue_unknown)
                    else -> stringResource(R.string.registered)
                }
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Dp(16f)), verticalArrangement = Arrangement.spacedBy(Dp(10f))) {
                        Text(stringResource(R.string.ims_not_available_reason), style = MaterialTheme.typography.titleMedium)
                        Text(reason)
                        Button(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    SubscriptionModer(context, subscriptions[idx].subscriptionId).restoreGoogleDefaults()
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
