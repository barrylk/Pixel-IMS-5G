package dev.bluehouse.enablevolte.pages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CarrierConfigManager
import android.telephony.CellInfo
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import dev.bluehouse.enablevolte.ui.theme.NumericSmallTextStyle
import dev.bluehouse.enablevolte.components.statusToneColor
import dev.bluehouse.enablevolte.MobileRadioIsolation
import dev.bluehouse.enablevolte.MobileRadioIsolationSession
import dev.bluehouse.enablevolte.PrivilegeManager
import dev.bluehouse.enablevolte.PrivilegeMode
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.components.Panel
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.components.OnLifecycleEvent
import dev.bluehouse.enablevolte.components.PremiumPageIntro
import dev.bluehouse.enablevolte.components.PremiumStatusChip
import dev.bluehouse.enablevolte.components.StatusTone
import dev.bluehouse.enablevolte.uniqueName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MonitorSection(val label: String) {
    LIVE("Live"),
    ATTACH("5G attach trace"),
    RADIO("PCC / SCC"),
    CONFIG("CarrierConfig diff"),
    CAPABILITIES("NR capabilities"),
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun MonitoringHub(subscriptions: List<SubscriptionInfo>) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf(MonitorSection.LIVE) }
    var isolationSession by remember { mutableStateOf<MobileRadioIsolationSession?>(null) }
    var isolationMessage by remember { mutableStateOf<String?>(null) }
    var isolationError by remember { mutableStateOf<String?>(null) }
    var isolationGeneration by remember { mutableStateOf(0) }

    fun restoreWifi() {
        val restored = isolationSession?.restore() ?: true
        isolationSession = null
        if (!restored) {
            isolationError = context.getString(R.string.mobile_only_wifi_restore_failed)
        }
    }

    LaunchedEffect(isolationGeneration) {
        if (isolationSession == null) {
            isolationError = null
            isolationMessage = context.getString(R.string.mobile_only_disabling_wifi)
            val result = withContext(Dispatchers.IO) { MobileRadioIsolation.begin() }
            isolationSession = result.session
            isolationMessage = result.message
            if (!result.active) isolationError = result.message
        }
    }
    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                if (isolationSession == null) isolationGeneration += 1
            }
            Lifecycle.Event.ON_STOP -> restoreWifi()
            else -> Unit
        }
    }
    DisposableEffect(Unit) {
        onDispose { isolationSession?.restore() }
    }

    Column {
        PremiumPageIntro(
            eyebrow = context.getString(R.string.premium_monitor_eyebrow),
            title = context.getString(R.string.premium_monitor_title),
            description = context.getString(R.string.premium_monitor_description),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        Panel(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            context.getString(R.string.premium_mobile_path),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (isolationError != null) {
                                context.getString(R.string.premium_monitor_paused)
                            } else {
                                isolationMessage ?: context.getString(R.string.mobile_only_disabling_wifi)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PremiumStatusChip(
                        label = when {
                            isolationSession != null -> context.getString(R.string.premium_active)
                            isolationError != null -> context.getString(R.string.premium_attention)
                            else -> context.getString(R.string.premium_isolating)
                        },
                        tone = when {
                            isolationSession != null -> StatusTone.SUCCESS
                            isolationError != null -> StatusTone.DANGER
                            else -> StatusTone.ACCENT
                        },
                    )
                }
                isolationError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { isolationGeneration += 1 }) {
                        Text(context.getString(R.string.retry))
                    }
                }
            }
        }
        if (isolationSession != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonitorSection.entries.forEach {
                    FilterChip(
                        selected = section == it,
                        onClick = { section = it },
                        label = { Text(it.label) },
                    )
                }
            }
            when (section) {
                MonitorSection.LIVE -> Monitor(subscriptions)
                MonitorSection.ATTACH -> AttachTracePage(subscriptions)
                MonitorSection.RADIO -> PhysicalChannelsPage()
                MonitorSection.CONFIG -> CarrierConfigDiffPage(subscriptions)
                MonitorSection.CAPABILITIES -> NrCapabilitiesPage(subscriptions)
            }
        }
    }
}

@Composable
private fun PhysicalChannelsPage() {
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun capture() {
        if (PrivilegeManager.activeMode != PrivilegeMode.ROOT || !PrivilegeManager.isRootReady()) return
        scope.launch {
            loading = true
            output = withContext(Dispatchers.IO) {
                PrivilegeManager.getRootTelephonyDiagnostic("physical")
                    ?: "Root telephony service is unavailable."
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { capture() }
    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText("Physical Channels — PCC / SCC")
        Text(
            "Root mode reads Android’s active PhysicalChannelConfig list: primary/secondary status, " +
                "RAT, band, PCI, channel, bandwidth, and frequency. Entries cover all active modems.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
            Button(enabled = !loading, onClick = { capture() }) {
                Text(if (loading) "Reading physical channels…" else "Refresh physical channels")
            }
            output?.lineSequence()?.filter { it.isNotBlank() }?.forEachIndexed { index, line ->
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val role = when {
                            line.contains("PrimaryServing") -> "PCell"
                            line.contains("SecondaryServing") -> "SCell"
                            else -> "Channel"
                        }
                        Text("$role ${index + 1}", style = MaterialTheme.typography.titleMedium)
                        Text(line, style = NumericSmallTextStyle)
                    }
                }
            }
        } else {
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    "Root required. Shizuku cannot read READ_PRECISE_PHONE_STATE physical-channel " +
                        "internals on current Tensor Pixel builds.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        Text(
            "Carrier aggregation is confirmed when at least one SecondaryServing LTE channel appears. " +
                "NSA is confirmed only when an NR SecondaryServing channel or NR connected state appears.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubscriptionPicker(
    subscriptions: List<SubscriptionInfo>,
    selectedSubId: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        subscriptions.forEach {
            FilterChip(
                selected = it.subscriptionId == selectedSubId,
                onClick = { onSelected(it.subscriptionId) },
                label = { Text(it.uniqueName) },
            )
        }
    }
}

private data class RegistryEvent(val time: String, val text: String)

private class RegistryCallback(private val event: (String) -> Unit) :
    TelephonyCallback(),
    TelephonyCallback.ServiceStateListener,
    TelephonyCallback.DisplayInfoListener,
    TelephonyCallback.CellInfoListener,
    TelephonyCallback.SignalStrengthsListener {
    override fun onServiceStateChanged(serviceState: ServiceState) {
        val nrState = runCatching {
            serviceState.javaClass.getMethod("getNrState").invoke(serviceState) as Int
        }.getOrDefault(0)
        event("ServiceState changed: state=${serviceState.state}, NR-state=$nrState")
    }

    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
        event(
            "DisplayInfo changed: network=${telephonyDisplayInfo.networkType}, " +
                "override=${telephonyDisplayInfo.overrideNetworkType}",
        )
    }

    override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
        event("CellInfo changed: ${cellInfo.size} framework-visible cells")
    }

    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
        event("Signal changed: ${signalStrength.level}/4, ${signalStrength.dbm} dBm")
    }
}

@Composable
private fun AttachTracePage(subscriptions: List<SubscriptionInfo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSubId by rememberSaveable { mutableStateOf(subscriptions.firstOrNull()?.subscriptionId ?: -1) }
    var radio by remember { mutableStateOf<SubscriptionModer.RadioDiagnostics?>(null) }
    var report by remember { mutableStateOf<SubscriptionModer.RootForceReport?>(null) }
    var registryState by remember { mutableStateOf("Waiting for monitoring permission") }
    var events by remember { mutableStateOf(emptyList<RegistryEvent>()) }
    var rootEvidence by remember { mutableStateOf<String?>(null) }
    var rootCaptureRunning by remember { mutableStateOf(false) }
    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPhonePermission = result[Manifest.permission.READ_PHONE_STATE] == true
    }

    DisposableEffect(selectedSubId, hasPhonePermission) {
        if (selectedSubId < 0 || !hasPhonePermission) {
            onDispose { }
        } else {
            val manager = context.getSystemService(TelephonyManager::class.java)
                .createForSubscriptionId(selectedSubId)
            val callback = RegistryCallback { message ->
                val now = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                events = (listOf(RegistryEvent(now, message)) + events).take(80)
            }
            runCatching {
                manager.registerTelephonyCallback(context.mainExecutor, callback)
                registryState = "Active — Android TelephonyRegistry callbacks"
            }.onFailure {
                registryState = "Unavailable: ${it.message ?: it.javaClass.simpleName}"
            }
            onDispose { runCatching { manager.unregisterTelephonyCallback(callback) } }
        }
    }

    LaunchedEffect(selectedSubId) {
        while (selectedSubId >= 0) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val moder = SubscriptionModer(context, selectedSubId)
                    val current = moder.getRadioDiagnostics()
                    current to moder.getRootForceReport(current)
                }
            }.onSuccess {
                radio = it.first
                report = it.second
            }
            delay(2_500)
        }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText("5G Attach Trace")
        SubscriptionPicker(subscriptions, selectedSubId) {
            selectedSubId = it
            radio = null
            report = null
            events = emptyList()
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Callback sources", style = MaterialTheme.typography.titleMedium)
                DiagnosticRow("TelephonyRegistry", registryState, registryState.startsWith("Active"))
                DiagnosticRow(
                    "ServiceStateTracker",
                    if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
                        "Root snapshot available; direct phone-process hook is not used"
                    } else {
                        "Root required for phone-process internals"
                    },
                    PrivilegeManager.activeMode == PrivilegeMode.ROOT,
                )
                DiagnosticRow(
                    "NetworkRegistrationManager",
                    if (PrivilegeManager.activeMode == PrivilegeMode.ROOT) {
                        "Root dumpsys/radio evidence available"
                    } else {
                        "Shizuku exposes framework registration state only; root required for internals"
                    },
                    PrivilegeManager.activeMode == PrivilegeMode.ROOT,
                )
                if (!hasPhonePermission) {
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION),
                        )
                    }) { Text("Allow live monitoring callbacks") }
                }
            }
        }

        radio?.let { current ->
            val gates = report
            val carrierNr = gates?.carrierNsa == true || gates?.carrierSa == true
            val localNrOpen = gates?.gates?.filter { it.mask != null }?.all { it.nrAllowed } == true
            val nsaConnected = current.displayTechnology == "5G NSA" || current.nrState == 3
            val reason = attachReason(current, carrierNr, localNrOpen)
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Live attach sequence", style = MaterialTheme.typography.titleMedium)
                    TraceStep("LTE registered", current.serviceState == "In service" && current.dataRat in setOf("LTE", "NR"))
                    TraceStep("IMS registered", current.imsRegistered)
                    TraceStep("CarrierConfig allows NR", carrierNr)
                    TraceStep("Allowed-network NR gates open", localNrOpen)
                    TraceStep("Network reports NR available", current.nrAvailable == true)
                    TraceStep("DCNR unrestricted", current.dcNrRestricted == false)
                    TraceStep("Serving registration reports EN-DC", current.endcAvailable == true)
                    TraceStep("NR neighbor exposed by Android", current.nrBands.isNotEmpty())
                    TraceStep("NSA secondary-cell group connected", nsaConnected)
                    Text("Current diagnosis", style = MaterialTheme.typography.titleSmall)
                    Text(reason, color = if (nsaConnected) statusToneColor(StatusTone.SUCCESS) else MaterialTheme.colorScheme.error)
                    Text(
                        "Android does not expose the RRC SCG request/reject cause through public APIs. " +
                            "A network rejection is only reported when root radio evidence contains one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Root attach evidence", style = MaterialTheme.typography.titleMedium)
                if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                    Button(
                        enabled = !rootCaptureRunning,
                        onClick = {
                            scope.launch {
                                rootCaptureRunning = true
                                rootEvidence = withContext(Dispatchers.IO) {
                                    listOf("registry", "phone", "radio").joinToString("\n\n") {
                                        "[$it]\n" +
                                            PrivilegeManager.getRootTelephonyDiagnostic(it)
                                                .orEmpty()
                                                .ifBlank { "No matching events" }
                                    }
                                }
                                rootCaptureRunning = false
                            }
                        }
                    ) {
                        Text(if (rootCaptureRunning) "Capturing…" else "Capture sanitized root evidence")
                    }
                    rootEvidence?.let { Text(it.take(12_000), style = NumericSmallTextStyle) }
                } else {
                    Text(
                        "Not available in Shizuku mode. Switch the app to Root mode to read sanitized " +
                            "TelephonyRegistry, phone-service, and radio-log evidence.",
                    )
                }
            }
        }

        if (events.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("TelephonyRegistry event log", style = MaterialTheme.typography.titleMedium)
                    events.forEach { Text("${it.time}  ${it.text}", style = NumericSmallTextStyle) }
                }
            }
        }
    }
}

private fun attachReason(
    radio: SubscriptionModer.RadioDiagnostics,
    carrierNr: Boolean,
    localNrOpen: Boolean,
): String = when {
    radio.dataRat == "NR" -> "5G SA is registered."
    radio.displayTechnology == "5G NSA" || radio.nrState == 3 -> "5G NSA is connected; SCG addition succeeded."
    !carrierNr -> "CarrierConfig disables NR modes on this subscription."
    !localNrOpen -> "One or more Android allowed-network reasons exclude NR."
    radio.dcNrRestricted == true -> "The network registration marks dual-connectivity NR as restricted."
    radio.nrAvailable == true && radio.endcAvailable == false ->
        "NR is advertised/eligible, but this LTE registration reports EN-DC unavailable. " +
            "This matches the captured Dialog B41 case: no NSA SCG can be formed on the current anchor."
    radio.nrAvailable == false -> "The current registration does not advertise NR availability."
    radio.endcAvailable == true ->
        "EN-DC is available but no SCG is connected. Generate root evidence during data traffic to identify a rejection or radio failure."
    else -> "The framework has not exposed enough state to determine the attach failure."
}

@Composable
private fun TraceStep(label: String, passed: Boolean) {
    Text(
        "${if (passed) "✓" else "✕"}  $label",
        style = MaterialTheme.typography.bodyMedium,
        color = if (passed) statusToneColor(StatusTone.SUCCESS) else statusToneColor(StatusTone.DANGER),
    )
}

/**
 * A label and the state the framework reports for it.
 *
 * The value is monospaced: these are readings, and a column of them should line
 * up rather than drift as each one changes length.
 */
@Composable
private fun DiagnosticRow(label: String, value: String, good: Boolean?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f))
        Text(
            value,
            style = NumericSmallTextStyle,
            modifier = Modifier.weight(0.58f),
            color = when (good) {
                true -> statusToneColor(StatusTone.SUCCESS)
                false -> statusToneColor(StatusTone.DANGER)
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private data class ConfigGate(
    val label: String,
    val key: String,
    val current: String,
    val androidDefault: String,
    val open: Boolean?,
)

@Composable
private fun CarrierConfigDiffPage(subscriptions: List<SubscriptionInfo>) {
    val context = LocalContext.current
    var selectedSubId by rememberSaveable { mutableStateOf(subscriptions.firstOrNull()?.subscriptionId ?: -1) }
    var rows by remember { mutableStateOf(emptyList<ConfigGate>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedSubId) {
        if (selectedSubId < 0) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) { carrierConfigRows(context, selectedSubId) }
        }.onSuccess {
            rows = it
            error = null
        }.onFailure { error = it.message }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText("CarrierConfig Diff Viewer")
        SubscriptionPicker(subscriptions, selectedSubId) { selectedSubId = it }
        Text(
            "Effective per-SIM values are compared with Android’s built-in defaults. Carrier defaults " +
                "are not automatically “better”; green means the effective value leaves that feature gate open.",
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        rows.forEach {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DiagnosticRow(it.label, it.current, it.open)
                    Text(it.key, style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Android default: ${it.androidDefault}" +
                            if (it.current != it.androidDefault) "  •  overridden/different" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun carrierConfigRows(context: Context, subId: Int): List<ConfigGate> {
    val moder = SubscriptionModer(context, subId)
    val defaults = CarrierConfigManager.getDefaultConfig()
    fun value(key: String): Any? = runCatching { moder.getValue(key) }.getOrNull()
    fun text(value: Any?): String = when (value) {
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is BooleanArray -> value.joinToString(prefix = "[", postfix = "]")
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
        null -> "not exposed"
        else -> value.toString()
    }
    fun defaultText(key: String): String = text(defaults[key])
    val nrKey = CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY
    val nrModes = moder.getIntArrayValue(nrKey)
    val gates = moder.getRootForceReport().gates
    val nrAllowed = gates.filter { it.mask != null }.all { it.nrAllowed }
    val hide5gInferred = nrModes.isEmpty()
    val entries = listOf(
        ConfigGate("allow_nr (effective)", "allowed_network_types + $nrKey", nrAllowed.toString(), "true", nrAllowed),
        ConfigGate("carrier_nr_availabilities", nrKey, text(nrModes), defaultText(nrKey), nrModes.isNotEmpty()),
        ConfigGate(
            "hide_enable_5g (inferred)",
            "inferred: carrier NR modes empty",
            hide5gInferred.toString(),
            "false",
            !hide5gInferred,
        ),
        ConfigGate(
            "VoNR enabled",
            CarrierConfigManager.KEY_VONR_ENABLED_BOOL,
            text(value(CarrierConfigManager.KEY_VONR_ENABLED_BOOL)),
            defaultText(CarrierConfigManager.KEY_VONR_ENABLED_BOOL),
            moder.getBooleanValue(CarrierConfigManager.KEY_VONR_ENABLED_BOOL),
        ),
        ConfigGate(
            "VoLTE available",
            CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL,
            text(value(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)),
            defaultText(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL),
            moder.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL),
        ),
        ConfigGate(
            "VoWiFi available",
            CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL,
            text(value(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)),
            defaultText(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL),
            moder.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL),
        ),
        ConfigGate(
            "LTE+ icon hidden",
            CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL,
            text(value(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL)),
            defaultText(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL),
            !moder.getBooleanValue(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL),
        ),
        ConfigGate(
            "VoWiFi status icon",
            CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL,
            text(value(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL)),
            defaultText(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL),
            moder.getBooleanValue(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL),
        ),
    )
    return entries
}

@Composable
private fun NrCapabilitiesPage(subscriptions: List<SubscriptionInfo>) {
    val context = LocalContext.current
    var selectedSubId by rememberSaveable { mutableStateOf(subscriptions.firstOrNull()?.subscriptionId ?: -1) }
    var radio by remember { mutableStateOf<SubscriptionModer.RadioDiagnostics?>(null) }
    var gates by remember { mutableStateOf<SubscriptionModer.RootForceReport?>(null) }
    var endcControl by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(selectedSubId) {
        if (selectedSubId < 0) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val moder = SubscriptionModer(context, selectedSubId)
                val current = moder.getRadioDiagnostics()
                val manager = context.getSystemService(TelephonyManager::class.java)
                    .createForSubscriptionId(selectedSubId)
                Triple(
                    current,
                    moder.getRootForceReport(current),
                    runCatching {
                        manager.isRadioInterfaceCapabilitySupported(
                            "CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE",
                        )
                    }.getOrNull(),
                )
            }
        }.onSuccess {
            radio = it.first
            gates = it.second
            endcControl = it.third
        }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText("NR Capability Decoder")
        SubscriptionPicker(subscriptions, selectedSubId) { selectedSubId = it }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device and subscription", style = MaterialTheme.typography.titleMedium)
                DiagnosticRow("NSA configured", stateText(gates?.carrierNsa), gates?.carrierNsa)
                DiagnosticRow("SA configured", stateText(gates?.carrierSa), gates?.carrierSa)
                DiagnosticRow(
                    "EN-DC control capability",
                    endcControl?.let { if (it) "Supported by radio HAL" else "Not reported by radio HAL" } ?: "Unknown",
                    endcControl,
                )
                DiagnosticRow(
                    "EN-DC available now",
                    stateText(radio?.endcAvailable),
                    radio?.endcAvailable,
                )
                DiagnosticRow(
                    "DCNR unrestricted now",
                    radio?.dcNrRestricted?.let { if (it) "Restricted" else "Unrestricted" } ?: "Unknown",
                    radio?.dcNrRestricted?.not(),
                )
                DiagnosticRow(
                    "VoNR configured",
                    if (selectedSubId >= 0) {
                        stateText(
                            runCatching {
                                SubscriptionModer(context, selectedSubId)
                                    .getBooleanValue(CarrierConfigManager.KEY_VONR_ENABLED_BOOL)
                            }.getOrNull(),
                        )
                    } else {
                        "Unknown"
                    },
                    null,
                )
                DiagnosticRow(
                    "NR connection",
                    radio?.displayTechnology ?: "Unknown",
                    radio?.displayTechnology?.startsWith("5G"),
                )
            }
        }
        Text(
            "“Configured” is not the same as connected. NSA also requires an LTE anchor advertising " +
                "EN-DC and successful network SCG admission. VoNR additionally requires SA registration, " +
                "IMS provisioning, and carrier support.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun stateText(value: Boolean?): String = when (value) {
    true -> "Supported / enabled"
    false -> "Not enabled"
    null -> "Unknown"
}
