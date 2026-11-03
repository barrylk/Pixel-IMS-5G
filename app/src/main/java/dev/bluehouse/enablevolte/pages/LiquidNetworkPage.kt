package dev.bluehouse.enablevolte.pages

import android.app.Application
import android.telephony.SubscriptionInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.*
import dev.bluehouse.enablevolte.components.GlassDepth
import dev.bluehouse.enablevolte.components.Sparkline
import dev.bluehouse.enablevolte.components.liquidGlass
import dev.bluehouse.enablevolte.components.LiquidPane as LivePane
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Reading(val name: String, val value: String, val meaning: String)
private data class ReadingSheet(val title: String, val explanation: String, val readings: List<Reading>, val raw: String? = null, val sourceKey: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPage(subscriptions: List<SubscriptionInfo>, navController: NavController) {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableIntStateOf(-1) }
    val sub = subscriptions.firstOrNull { it.subscriptionId == selectedId } ?: subscriptions.firstOrNull()
    if (sub == null) {
        LivePane(Modifier.padding(20.dp)) { Text("No active SIM. Insert or enable a SIM to monitor its network.") }
        return
    }
    val vm: NetworkMonitorViewModel = viewModel(key = "network-${sub.subscriptionId}",
        factory = NetworkMonitorViewModel.factory(context.applicationContext as Application, sub.subscriptionId))
    val ui by vm.state.collectAsState()
    var userPaused by rememberSaveable(sub.subscriptionId) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val pausedNow by rememberUpdatedState(userPaused)
    DisposableEffect(vm, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && !pausedNow) vm.start()
            if (event == Lifecycle.Event.ON_STOP) vm.stop()
        }
        owner.lifecycle.addObserver(observer)
        if (!userPaused && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.start()
        onDispose { owner.lifecycle.removeObserver(observer); vm.stop() }
    }
    var sheet by remember(sub.subscriptionId) { mutableStateOf<ReadingSheet?>(null) }


    val connection = ui.connection
    val radio = ui.radio
    val name = liveCarrierName(radio?.operatorName.orEmpty(), radio?.operatorNumeric ?: "${sub.mccString}${sub.mncString}", sub.carrierName.toString())
    LaunchedEffect(ui.lastSampleAt) {
        val current = sheet ?: return@LaunchedEffect
        sheet = when {
            current.title == "Understand your connection" -> connectionSheet(ui)
            current.title == "Understand your signal" -> signalSheet(ui.serving)
            current.title == "All measurements" -> allMeasurementsSheet(ui, name)
            current.sourceKey != null -> ui.connection?.carriers?.firstOrNull { it.key == current.sourceKey }?.let(::carrierSheet)
                ?: ui.neighbours.firstOrNull { cellKey(it) == current.sourceKey }?.let(::nearbySheet)
                ?: current.copy(explanation = "This carrier is no longer reported in the current sample. These are its last available measurements.")
            else -> current
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val good = if (LocalInstrument.current.isDark) Color(0xFF52D99B) else Color(0xFF087B4F)
    val caution = if (LocalInstrument.current.isDark) Color(0xFFFFC466) else Color(0xFF8C5300)

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Live network", fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp, modifier = Modifier.weight(1f))
            Row(Modifier.liquidGlass(RoundedCornerShape(24.dp), GlassDepth.THICK).clickable { userPaused = !userPaused; if (userPaused) vm.stop() else vm.start() }.padding(horizontal = 15.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (ui.sampling && ui.available) good else caution, CircleShape))
                Text(if (ui.sampling) "Live" else "Paused", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(28.dp), GlassDepth.THICK).horizontalScroll(rememberScrollState()).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            subscriptions.forEach { sim ->
                val selected = sim.subscriptionId == sub.subscriptionId
                val label = liveCarrierName(sim.carrierName.toString(), "${sim.mccString}${sim.mncString}", "SIM ${sim.simSlotIndex + 1}")
                Box(Modifier.then(if (selected) Modifier.liquidGlass(RoundedCornerShape(23.dp), GlassDepth.TINTED, accent) else Modifier).clickable { selectedId = sim.subscriptionId }.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Text("$label · SIM ${sim.simSlotIndex + 1}", fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) accent else muted)
                }
            }
        }
        ui.message?.let { LivePane { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) } }
        LivePane {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(radio?.operatorNumeric?.let { if (it.length >= 5) "${it.take(3)}-${it.drop(3)}" else it }.orEmpty(), color = muted, fontSize = 12.sp)
            }
            val title = when {
                !ui.available && !ui.loading -> "Readings unavailable"
                radio?.serviceState == "Radio off" -> "Mobile radio is off"
                radio?.serviceState in listOf("Out of service", "Emergency only") -> radio?.serviceState.orEmpty()
                else -> connection?.title ?: "Reading your network…"
            }
            Text(title, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
            Text(if (!ui.sampling) "Paused. The values below are the last captured reading." else connection?.explanation ?: "Waiting for current radio information from your phone.", color = muted, fontSize = 14.sp, lineHeight = 20.sp)
            HorizontalDivider(color = muted.copy(alpha = .13f), modifier = Modifier.padding(vertical = 3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(if (ui.nrAttached) good else caution, CircleShape))
                Text(connection?.fiveGStatus ?: "5G state not reported", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text("What does this mean?", color = accent, fontSize = 13.sp, modifier = Modifier.clickable { sheet = connectionSheet(ui) }.padding(vertical = 5.dp))
        }
        LivePane {
            val bandwidth = connection?.caBandwidth
            val count = bandwidth?.componentCount ?: 0
            SectionTitle(when { count > 1 -> "Carrier aggregation · $count carriers"; connection?.caActive == true -> "CA reported · details incomplete"; else -> "LTE carrier report" })
            Text(connection?.bandCombination?.takeIf { it.isNotBlank() } ?: "Bands not reported", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(when { count > 1 -> "$count identified LTE carriers are reported together. The primary carrier handles the main connection; secondary carriers add capacity."; count == 1 -> "One LTE carrier is identified in this sample."; else -> "No LTE component details are available in this sample." }, fontSize = 12.sp, lineHeight = 17.sp, color = muted)
            if (count > 0) Text(bandwidth!!.componentWidthsKhz.joinToString(" + ") { it?.let { width -> formatNumber(width / 1000.0) } ?: "?" } +
                " MHz" + (bandwidth.totalKhz?.let { " = ${formatNumber(it / 1000.0)} MHz identified LTE bandwidth" } ?: " · total not reported"),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent)
            if (bandwidth != null && ui.lastSampleAt > 0) Text("${bandwidth.source} · ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ui.lastSampleAt))}", fontSize = 11.sp, color = muted)
            Text(if (ui.nrAttached) connection?.explanation.orEmpty() else "5G: not connected. There is currently no active 5G link alongside these LTE carriers.", fontSize = 12.sp, lineHeight = 17.sp, color = muted)
            if (connection?.caActive == true && count < 2) Text("Android's registration state reports CA, but extra components are not identified in this sample. Its bandwidth summary may be retained between radio updates; it is shown separately under Network registration.", fontSize = 12.sp, color = caution)
        }
        connection?.carriers?.forEachIndexed { index, carrier ->
            LivePane {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${carrier.band} · ${carrier.rat}", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.weight(1f))
                    Text(when { carrier.rat == "NR" -> "5G"; carrier.role == "4G anchor" -> "LTE anchor"; carrier.role == "Extra 4G carrier" -> "SCC · secondary"; else -> "PCC · primary" }, fontSize = 12.sp, color = muted)
                }
                Text(when { carrier.rat == "NR" -> "The 5G radio link. NSA uses this alongside the LTE anchor."; carrier.role == "4G anchor" -> "The 4G anchor keeps the NSA connection registered while 5G adds data capacity."; carrier.role == "Extra 4G carrier" -> "An additional LTE carrier combined with the primary carrier for data capacity."; else -> "The main LTE carrier handles registration and coordinates the other carriers." }, fontSize = 12.sp, lineHeight = 17.sp, color = muted)
                ReadingGrid(carrierSheet(carrier).readings.distinctBy { it.name })
            }
        }
        LivePane {
            SectionTitle("Signal")
            val cell = ui.serving
            val dbm = (cell?.rsrp ?: cell?.dbm)?.takeIf { it != Int.MAX_VALUE }
            val clarity = cell?.sinr ?: cell?.csiSinr
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).clickable { sheet = signalSheet(cell) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Strength", color = muted, fontSize = 13.sp)
                    Text(radioReading(dbm, "dBm"), fontWeight = FontWeight.Bold, fontSize = if (dbm == null) 17.sp else 24.sp)
                    val word = when { dbm == null -> "No reading"; dbm >= -95 -> "Good"; dbm >= -105 -> "Fair"; else -> "Weak" }
                    Text(word, color = if (word == "Good") good else caution, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f).clickable { sheet = signalSheet(cell) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Clarity", color = muted, fontSize = 13.sp)
                    Text(radioReading(clarity, "dB"), fontWeight = FontWeight.Bold, fontSize = if (clarity == null) 17.sp else 24.sp)
                    Text(when { clarity == null -> "No reading"; clarity >= 10 -> "Good"; clarity >= 0 -> "Fair"; else -> "Noisy" }, color = if ((clarity ?: -1) >= 10) good else caution, fontSize = 13.sp)
                }
            }
            if (ui.rsrpHistory.size > 1) {
                Text("Recent strength · ${ui.rsrpHistory.size} samples", color = muted, fontSize = 11.sp)
                Sparkline(ui.rsrpHistory, modifier = Modifier.fillMaxWidth().height(46.dp))
                Text("Low ${radioReading(ui.historyMin)} · average ${radioReading(ui.historyMean)} · high ${radioReading(ui.historyMax)} dBm", color = muted, fontSize = 11.sp)
            }
            Text("Signal is not a speed test. Network load, bandwidth and carrier policy also affect performance.", color = muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        LivePane {
            SectionTitle("Network registration & IMS")
            Text("Live service, calling and 5G registration details for this SIM.", fontSize = 12.sp, color = muted)
            ReadingGrid(connectionSheet(ui).readings, explain = true)
        }
        LivePane {
            SectionTitle("Nearby & other measured cells · ${ui.neighbours.size}")
            Text("Measured cells without a confirmed role in this SIM's carrier combination. They are not counted as CA. Another SIM may also appear here.", fontSize = 12.sp, lineHeight = 17.sp, color = muted)
            if (ui.neighbours.isEmpty()) Text("No other cells reported.", fontSize = 13.sp)
        }
        ui.neighbours.forEach { c ->
            LivePane {
                SectionTitle("${c.type} ${c.band} · PCI ${radioReading(c.pci)}")
                Text("Measured cell · operator ${c.operator.ifBlank { "not reported" }}", fontSize = 12.sp, color = muted)
                ReadingGrid(cellReadings(c))
            }
        }
        LivePane {
            SectionTitle("How to read the measurements")
            Text("RSRP: useful signal strength. Less negative is stronger.\nRSRQ: signal quality; less negative is usually better.\nSINR: signal clarity; higher is better.\nRSSI: total received power including noise.\nCQI: radio quality used to select coding, not a speed score.\nEARFCN / NR-ARFCN: the exact LTE / 5G channel.\nPCI: a reusable radio cell identity, not a unique tower ID.\nTAC: the tracking area used for mobility and paging.\nCell ID: the network's cell identifier. eNB / sector is derived from the LTE cell ID.\nBandwidth: radio spectrum width, not download speed.\nTiming advance: timing compensation, not an accurate tower distance.\nNot reported: the phone supplied no valid value; it does not mean zero.", fontSize = 12.sp, lineHeight = 20.sp, color = muted)
        }
        LivePane {
            SectionTitle("Connection history · ${ui.events.size}")
            if (ui.events.isEmpty()) Text("No changes recorded in this session.", fontSize = 13.sp)
            ui.events.take(30).forEach { event ->
                Text("${event.clock} · ${event.title}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(event.detail, fontSize = 12.sp, color = muted)
            }
            TextButton(onClick = vm::clearLog) { Text("Clear history") }
        }
        Disclosure("Deep diagnostics", "Attach trace, physical channels and carrier settings") { navController.navigate("network/diagnostics") }
        if (ui.lastSampleAt > 0) Text("${if (ui.sampling && ui.available) "Updated" else "Last reading"} ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ui.lastSampleAt))} · ${ui.serviceState}", fontSize = 11.sp, color = muted, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(18.dp))
    }
    sheet?.let { details ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(details.title, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                Text(details.explanation, fontSize = 14.sp, lineHeight = 20.sp, color = muted)
                details.readings.forEach { reading ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(reading.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(reading.value, fontSize = 14.sp, color = accent, modifier = Modifier.weight(1f))
                        }
                        Text(reading.meaning, fontSize = 12.sp, color = muted, lineHeight = 17.sp)
                    }
                    HorizontalDivider(color = muted.copy(alpha = .12f))
                }
                details.raw?.let { raw ->
                    var expanded by remember(details.title) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide Android source data" else "Show Android source data") }
                    if (expanded) SelectionContainer { Text(raw, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                }
                TextButton(onClick = { sheet = null }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-.3).sp)
}
@Composable private fun ReadingGrid(readings: List<Reading>, explain: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    readings.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { reading ->
                Column(Modifier.weight(1f).background(accent.copy(alpha = .055f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(reading.name, fontSize = 11.sp, lineHeight = 15.sp, color = muted)
                    Text(reading.value, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (explain) Text(reading.meaning, fontSize = 11.sp, lineHeight = 15.sp, color = muted)
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
@Composable private fun Disclosure(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().liquidGlass(RoundedCornerShape(23.dp), GlassDepth.THICK).clickable(onClick = onClick).padding(horizontal = 17.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
private fun formatNumber(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)
private fun flag(value: Boolean?) = when (value) { true -> "Yes"; false -> "No"; null -> "Not reported" }
private fun cellKey(c: SubscriptionModer.CellSnapshot) = "${c.type}:${c.channel}:${c.pci}:${c.band}"
private fun nearbySheet(c: SubscriptionModer.CellSnapshot) = ReadingSheet("${c.type} ${c.band}", "Measured cell, not confirmed as part of this SIM's active carrier combination.", cellReadings(c), sourceKey = cellKey(c))
private fun allMeasurementsSheet(ui: NetworkState, name: String) = ReadingSheet("All measurements", "Live readings for $name. Missing values mean the modem did not expose them; they are not zero.",
    connectionSheet(ui).readings + (ui.serving?.let(::cellReadings) ?: emptyList()) + ui.physicalChannels.map { p ->
        Reading("Physical ${p.rat} ${p.band?.toString() ?: "band hidden"}", "Channel ${radioReading(p.channel)} · PCI ${radioReading(p.pci)}",
            "Current physical-channel report for this SIM slot. Registration state determines whether 5G is counted as connected; conflicting observations are retained for diagnosis.")
    }, ui.radio?.serviceStateSummary)
private fun connectionSheet(ui: NetworkState): ReadingSheet {
    val r = ui.radio
    return ReadingSheet("Understand your connection", "4G+ combines radio carriers. NSA 5G adds a 5G link alongside a 4G anchor; SA connects directly to 5G. Availability alone does not establish an active 5G link.", listOf(
        Reading("Connection", ui.connection?.title ?: "Not reported", ui.connection?.explanation.orEmpty()),
        Reading("Service", ui.serviceState.ifBlank { "Not reported" }, "Whether the phone is registered for mobile service."),
        Reading("Data radio", r?.dataRat ?: "Not reported", "The data access technology reported by the selected SIM's registration state."),
        Reading("NR registration state", when (r?.nrState) { 0 -> "None"; 1 -> "Restricted"; 2 -> "Available, not connected"; 3 -> "Connected"; else -> "Not reported" }, "This distinguishes NR availability from an active 5G secondary connection."),
        Reading("Registration channel", radioReading(r?.channelNumber), "The channel reported by ServiceState for the selected SIM."),
        Reading("Duplex mode", when(r?.duplexMode) { 1 -> "FDD"; 2 -> "TDD"; else -> "Not reported" }, "FDD separates upload and download by frequency; TDD separates them by time."),
        Reading("Network services", r?.registrationServices?.joinToString { when(it) {1 -> "Voice"; 2 -> "Data"; 3 -> "SMS"; 4 -> "Video"; 5 -> "Emergency"; else -> "Code $it"} }?.ifBlank { "Not reported" } ?: "Not reported", "Services advertised by the current packet-service registration. These do not guarantee IMS provisioning."),
        Reading("Carrier aggregation", flag(ui.connection?.caActive), "Extra LTE carriers can increase capacity. Android sometimes confirms CA without exposing each extra carrier."),
        Reading("Registration bandwidth summary", r?.cellBandwidthsKhz?.takeIf { it.isNotEmpty() }?.joinToString(" + ", postfix = " MHz") { formatNumber(it / 1000.0) } ?: "Not reported", "ServiceState may retain this list between radio updates. Current identified component widths are shown in the carrier section above."),
        Reading("5G state", ui.connection?.fiveGStatus ?: "Not reported", "Connected NR state or serving NR evidence establishes 5G. A status-bar icon alone does not."),
        Reading("NR available", flag(r?.nrAvailable), "The network advertises NR availability. This does not mean a 5G carrier is connected."),
        Reading("EN-DC available", flag(r?.endcAvailable), "The network advertises the ability to combine LTE and NR. This is a capability, not an active attachment."),
        Reading("NR restricted", flag(r?.dcNrRestricted), "Whether the network reports a restriction on LTE/NR dual connectivity."),
        Reading("IMS", if (r?.imsRegistered == true) "Registered · ${r.imsTransport}" else "Not registered", "IMS provides calling services. IWLAN means Wi-Fi calling; LTE/NR means cellular IMS."),
        Reading("Roaming", flag(r?.roaming), "Whether Android reports registration on a roaming network."),
        Reading("Network / PLMN", r?.registeredPlmn ?: r?.operatorNumeric ?: "Not reported", "The country and operator identifiers of the serving network."),
        Reading("Registration reject cause", radioReading(r?.registrationRejectCause), "A network rejection code when reported. Zero normally means no rejection; missing is not a rejection."),
        Reading("Network searching", flag(r?.networkSearching), "Whether registration reports that the phone is searching for service."),
        Reading("Non-terrestrial network", flag(r?.nonTerrestrialNetwork), "Whether Android reports a satellite or other non-terrestrial connection."),
    ))
}
private fun carrierSheet(c: LiveCarrier): ReadingSheet = ReadingSheet(c.role,
    "${c.rat} ${c.band}. ${if (c.physical != null) "Role reported by the current physical channel for this SIM slot." else "Role based on this SIM's serving-cell evidence."} Missing values are not invented.", buildList {
        add(Reading("Band", c.band, "LTE B-number or 5G n-number. Multiple carriers can use the same band on different channels."))
        add(Reading(if (c.rat == "NR") "NR-ARFCN" else "EARFCN", radioReading(c.channel), "The exact radio channel number distinguishes two carriers on the same band."))
        add(Reading("PCI", radioReading(c.pci), "Physical Cell Identity helps distinguish radio cells; it is not a unique tower ID."))
        add(Reading("Downlink bandwidth", c.bandwidthKhz?.let { "${formatNumber(it / 1000.0)} MHz" } ?: "Not reported", "Width of this receiving carrier, not your measured download speed."))
        c.physical?.let { p ->
            add(Reading("Uplink channel", radioReading(p.uplinkChannel), "Channel used to send data to the network."))
            add(Reading("Uplink bandwidth", p.uplinkBandwidthKhz?.let { "${formatNumber(it / 1000.0)} MHz" } ?: "Not reported", "Width of this carrier's upload channel."))
            add(Reading("Downlink frequency", p.downlinkFrequencyKhz?.let { "${formatNumber(it / 1000.0)} MHz" } ?: "Not reported", "The receiving frequency exposed by the physical-channel report."))
            add(Reading("Uplink frequency", p.uplinkFrequencyKhz?.let { "${formatNumber(it / 1000.0)} MHz" } ?: "Not reported", "The sending frequency exposed by the physical-channel report."))
        }
        c.cell?.let { addAll(cellReadings(it)) }
    }, sourceKey = c.key)
private fun signalSheet(c: SubscriptionModer.CellSnapshot?) = ReadingSheet("Understand your signal", "Strength measures useful radio signal. Clarity measures how well it stands out from interference. A strong signal can still be noisy.", c?.let(::cellReadings) ?: listOf(Reading("Signal", "Not reported", "The modem has not exposed measurements for this serving carrier.")))
private fun cellReadings(c: SubscriptionModer.CellSnapshot): List<Reading> = buildList {
    add(Reading("Operator", c.operator.ifBlank { "Not reported" }, "MCC-MNC identifies the cell's country and operator."))
    add(Reading("RSRP / SS-RSRP", radioReading(c.rsrp, "dBm"), "Reference-signal strength. Less negative is stronger; −90 dBm is stronger than −110 dBm."))
    add(Reading("RSRQ / SS-RSRQ", radioReading(c.rsrq, "dB"), "Reference-signal quality, influenced by interference and loading. Less negative is generally better."))
    add(Reading("SINR / SS-SINR", radioReading(c.sinr, "dB"), "Useful signal compared with interference and noise. Higher is clearer."))
    add(Reading("RSSI", radioReading(c.rssi, "dBm"), "Total received power, including interference; different from reference-signal strength."))
    add(Reading("CQI", radioReading(c.cqi), "Channel Quality Indicator helps the network select modulation and coding; not a speed measurement."))
    add(Reading("Signal level", radioReading(c.level, "/ 4"), "Android's coarse signal level. The detailed measurements provide more context."))
    add(Reading("Channel", radioReading(c.channel), "EARFCN for LTE or NR-ARFCN for 5G identifies the radio channel."))
    add(Reading("Downlink frequency", c.frequencyKhz?.let { "${formatNumber(it / 1000.0)} MHz" } ?: "Not reported", "Frequency reported or calculated from the standardized channel mapping."))
    add(Reading("PCI", radioReading(c.pci), "Physical Cell Identity is reused across the network, so is not a unique site identifier."))
    add(Reading("TAC", radioReading(c.tac), "Tracking Area Code groups cells for mobility and paging."))
    add(Reading("Cell ID", c.cellId.takeUnless { it in listOf("2147483647", "9223372036854775807", "-1") } ?: "Not reported", "LTE cell identity or 5G NCI. Identifies a cell, not an accurate map location."))
    if (c.type == "LTE") c.cellId.toLongOrNull()?.takeIf { it >= 0 && it < 268435456 }?.let {
        add(Reading("LTE eNB / sector", "${it / 256} / ${it % 256}", "Derived from LTE cell identity using the conventional 8-bit sector split; not a GPS location."))
    }
    add(Reading("Bandwidth", c.bandwidthKhz?.let { "${formatNumber(it / 1000.0)} MHz" } ?: "Not reported", "Width of this carrier. CA can combine several separate widths."))
    add(Reading("Timing advance", radioReading(c.timingAdvance, if (c.type == "NR") "µs" else "steps"), "Radio timing compensation; not a reliable direct distance to the tower."))
    add(Reading("Reported bands", c.allBands.takeUnless { it == "[]" || it.isBlank() } ?: "Not reported", "Bands for this measured cell; this list alone does not prove multiple CA carriers."))
    add(Reading("Connection role", when(c.connectionStatus) { 1 -> "Primary serving"; 2 -> "Secondary serving"; 0 -> "Not serving"; else -> "Not reported" }, "Primary handles signaling and possibly data; secondary supports data. Nearby cells are not automatically serving cells."))
    if (c.type == "NR") {
        add(Reading("CSI-RSRP", radioReading(c.csiRsrp, "dBm"), "5G channel-state-information reference-signal strength."))
        add(Reading("CSI-RSRQ", radioReading(c.csiRsrq, "dB"), "5G CSI reference-signal quality."))
        add(Reading("CSI-SINR", radioReading(c.csiSinr, "dB"), "5G CSI signal clarity relative to interference and noise."))
        add(Reading("CSI CQI table", radioReading(c.csiCqiTable), "Table used to interpret the 5G channel quality report."))
        add(Reading("CSI CQI report", c.csiCqiReport ?: "Not reported", "Per-resource quality values supplied by the modem."))
    }
}

