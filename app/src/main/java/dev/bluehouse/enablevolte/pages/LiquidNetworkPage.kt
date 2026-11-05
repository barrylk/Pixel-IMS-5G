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
import androidx.compose.material.icons.filled.Insights
import dev.bluehouse.enablevolte.components.IconDot
import dev.bluehouse.enablevolte.components.GlassIconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.components.BottomBarSpacer
import dev.bluehouse.enablevolte.components.GlassPill
import dev.bluehouse.enablevolte.components.PageEnd
import dev.bluehouse.enablevolte.components.QuietButton
import dev.bluehouse.enablevolte.ui.theme.Mono
import dev.bluehouse.enablevolte.components.BigReading
import dev.bluehouse.enablevolte.components.Chevron
import dev.bluehouse.enablevolte.components.ContentCard
import dev.bluehouse.enablevolte.components.GroupCard
import dev.bluehouse.enablevolte.components.GroupDivider
import dev.bluehouse.enablevolte.components.GroupLabel
import dev.bluehouse.enablevolte.components.HeroCard
import dev.bluehouse.enablevolte.components.ListRow
import dev.bluehouse.enablevolte.components.MeterBar
import dev.bluehouse.enablevolte.components.NoteBox
import dev.bluehouse.enablevolte.components.PageTitle
import dev.bluehouse.enablevolte.components.ReadingCell
import dev.bluehouse.enablevolte.components.RowValue
import dev.bluehouse.enablevolte.components.SimPicker
import dev.bluehouse.enablevolte.components.StatusPill
import dev.bluehouse.enablevolte.components.StatusTone
import dev.bluehouse.enablevolte.components.TonalPillButton
import dev.bluehouse.enablevolte.components.statusToneColor
import dev.bluehouse.enablevolte.components.floatingGlass
import dev.bluehouse.enablevolte.components.calmSurface
import dev.bluehouse.enablevolte.*
import dev.bluehouse.enablevolte.components.Sparkline
import dev.bluehouse.enablevolte.components.LiquidPane as LivePane
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Reading(val name: String, val value: String, val meaning: String)
private data class ReadingSheet(val title: String, val explanation: String, val readings: List<Reading>, val raw: String? = null, val sourceKey: String? = null, val actionLabel: String? = null, val action: (() -> Unit)? = null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NetworkPage(subscriptions: List<SubscriptionInfo>, navController: NavController) {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableIntStateOf(-1) }
    val sub = subscriptions.firstOrNull { it.subscriptionId == selectedId } ?: subscriptions.firstOrNull()
    if (sub == null) {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageTitle("Network")
            NoteBox("No active SIM. Insert or enable a SIM to monitor its network.", StatusTone.NEUTRAL)
        }
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
            current.title == NEARBY_TITLE -> nearbyListSheet(ui)
            current.title == HISTORY_TITLE -> historySheet(ui, vm::clearLog)
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

    val cell = ui.serving
    val dbm = (cell?.rsrp ?: cell?.dbm)?.takeIf { it != Int.MAX_VALUE }
    val clarity = cell?.sinr ?: cell?.csiSinr
    val strengthTone = when { dbm == null -> StatusTone.NEUTRAL; dbm >= -95 -> StatusTone.SUCCESS; dbm >= -105 -> StatusTone.WARNING; else -> StatusTone.DANGER }
    val clarityTone = when { clarity == null -> StatusTone.NEUTRAL; clarity >= 10 -> StatusTone.SUCCESS; clarity >= 0 -> StatusTone.WARNING; else -> StatusTone.DANGER }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("Network") {
            GlassIconButton(Icons.Filled.Insights, "Deep diagnostics", { navController.navigate("network/diagnostics") })
            GlassPill(if (ui.sampling) "Live" else "Paused", if (ui.sampling && ui.available) good else caution) {
                userPaused = !userPaused
                if (userPaused) vm.stop() else vm.start()
            }
        }
        SimPicker(
            items = subscriptions.map { sim ->
                sim.subscriptionId to "${liveCarrierName(sim.carrierName.toString(), "${sim.mccString}${sim.mncString}", "SIM ${sim.simSlotIndex + 1}")} · SIM ${sim.simSlotIndex + 1}"
            },
            selectedId = sub.subscriptionId,
            onSelect = { selectedId = it },
        )
        ui.message?.let { NoteBox(it, StatusTone.DANGER) }

        val title = when {
            !ui.available && !ui.loading -> "Readings unavailable"
            radio?.serviceState == "Radio off" -> "Mobile radio is off"
            radio?.serviceState in listOf("Out of service", "Emergency only") -> radio?.serviceState.orEmpty()
            else -> connection?.title ?: "Reading your network…"
        }
        val plmn = radio?.operatorNumeric?.let { if (it.length >= 5) "${it.take(3)}-${it.drop(3)}" else it }.orEmpty()
        val servingLine = cell?.let { "${if (it.type == "NR") "5G" else "LTE"} ${it.band} · channel ${radioReading(it.channel)}" }
        val count = connection?.caBandwidth?.componentCount ?: 0
        HeroCard(
            title = title,
            eyebrow = listOfNotNull(listOf(name, plmn).filter { it.isNotBlank() }.joinToString(" "), servingLine).joinToString(" · "),
            body = if (!ui.sampling) "Paused. The values below are the last captured reading." else connection?.explanation ?: "Waiting for current radio information from your phone.",
        ) {
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(connection?.fiveGStatus ?: "5G state not reported", if (ui.nrAttached) StatusTone.SUCCESS else StatusTone.WARNING)
                if (count > 1) {
                    StatusPill("LTE CA · $count carriers", StatusTone.ACCENT)
                } else if (connection?.caActive == true) {
                    StatusPill("CA reported", StatusTone.ACCENT)
                }
            }
            Text("What does this mean?", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { sheet = connectionSheet(ui) }.padding(top = 4.dp, bottom = 2.dp))
        }

        ContentCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Signal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("What does this mean?", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { sheet = signalSheet(cell) })
            }
            BigReading(dbm?.toString() ?: "—", if (dbm != null) "dBm RSRP" else "No reading")
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).clickable { sheet = signalSheet(cell) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Strength", fontSize = 11.sp, color = muted, modifier = Modifier.weight(1f))
                        Text(when { dbm == null -> "No reading"; dbm >= -95 -> "Good"; dbm >= -105 -> "Fair"; else -> "Weak" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusToneColor(strengthTone))
                    }
                    MeterBar(((dbm ?: -140) + 125) / 45f, statusToneColor(strengthTone))
                }
                Column(Modifier.weight(1f).clickable { sheet = signalSheet(cell) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Clarity ${radioReading(clarity, "dB")}", fontSize = 11.sp, color = muted, modifier = Modifier.weight(1f))
                        Text(when { clarity == null -> "No reading"; clarity >= 10 -> "Good"; clarity >= 0 -> "Fair"; else -> "Noisy" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusToneColor(clarityTone))
                    }
                    MeterBar(((clarity ?: -20) + 10) / 40f, statusToneColor(clarityTone))
                }
            }
            val rssiCalculated = if (cell?.rssi == null && cell?.type == "LTE") RadioMath.estimateLteRssi(cell.rsrp, cell.rsrq, cell.bandwidthKhz) else null
            val distance = cell?.let { RadioMath.distanceMetres(it.type, it.timingAdvance) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadingCell(if (cell?.type == "NR") "SS-RSRQ" else "RSRQ", radioReading(cell?.rsrq, "dB"), Modifier.weight(1f))
                ReadingCell("RSSI", cell?.rssi?.let { "$it dBm" } ?: rssiCalculated?.let { "≈$it dBm" } ?: "—", Modifier.weight(1f), note = if (cell?.rssi == null && rssiCalculated != null) "calculated" else null)
                ReadingCell("CQI", cell?.cqi?.toString() ?: "—", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadingCell("Timing adv.", cell?.timingAdvance?.let { "$it${if (cell.type == "NR") " µs" else ""}" } ?: "—", Modifier.weight(1f))
                ReadingCell("Distance", distance?.let(RadioMath::formatDistance) ?: "—", Modifier.weight(1f), note = if (distance != null) "from timing advance" else null)
                ReadingCell("PCI", radioReading(cell?.pci), Modifier.weight(1f))
            }
            if (ui.rsrpHistory.size > 1) {
                Sparkline(ui.rsrpHistory, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(46.dp))
                Text("Recent strength · ${ui.rsrpHistory.size} samples · low ${radioReading(ui.historyMin)} · average ${radioReading(ui.historyMean)} · high ${radioReading(ui.historyMax)} dBm", color = muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Text("Signal is not a speed test. Network load, bandwidth and carrier policy also affect performance. A value marked calculated is derived from reported readings, not reported by the modem.", color = muted, fontSize = 11.sp, lineHeight = 15.sp)
        }

        val bandwidth = connection?.caBandwidth
        GroupLabel(
            when {
                count > 1 -> "Active carriers · " + (bandwidth?.totalKhz?.let { "${formatNumber(it / 1000.0)} MHz reported" } ?: "$count carriers")
                connection?.caActive == true -> "CA reported · details incomplete"
                else -> "LTE carrier report"
            },
        )
        val carriers = connection?.carriers.orEmpty()
        val widest = carriers.mapNotNull { it.bandwidthKhz?.takeIf { w -> w > 0 && w != Int.MAX_VALUE } }.maxOrNull() ?: 0
        GroupCard {
            if (carriers.isEmpty()) ListRow("No LTE component details are available in this sample.")
            carriers.forEachIndexed { index, carrier ->
                if (index > 0) GroupDivider()
                CarrierRow(carrier, widest) { sheet = carrierSheet(carrier) }
            }
        }
        if (connection?.caActive == true && count < 2) {
            NoteBox("Android's registration state reports CA, but extra components are not identified in this sample. Its bandwidth summary may be retained between radio updates; it is shown under All registration details.", StatusTone.WARNING)
        }

        GroupLabel("Registration and IMS")
        GroupCard {
            ListRow("Data") { RowValue("${radio?.dataRat ?: "Not reported"} · ${ui.serviceState.ifBlank { "Not reported" }}") }
            GroupDivider()
            ListRow("Calling (IMS)") {
                StatusPill(
                    if (radio?.imsRegistered == true) "Registered · ${radio.imsTransport}" else "Not registered",
                    if (radio?.imsRegistered == true) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                )
            }
            GroupDivider()
            ListRow("NR advertised") { RowValue(flag(radio?.nrAvailable)) }
            GroupDivider()
            ListRow("EN-DC available") { RowValue(flag(radio?.endcAvailable)) }
            GroupDivider()
            ListRow("NR state") {
                StatusPill(
                    when (radio?.nrState) { 0 -> "None"; 1 -> "Restricted"; 2 -> "Not connected"; 3 -> "Connected"; else -> "Not reported" },
                    when (radio?.nrState) { 3 -> StatusTone.SUCCESS; 1, 2 -> StatusTone.WARNING; else -> StatusTone.NEUTRAL },
                )
            }
            GroupDivider()
            ListRow("All registration details", subtitle = "Every value Android reports for this SIM, with explanations", onClick = { sheet = connectionSheet(ui) }) { Chevron() }
        }

        GroupCard {
            ListRow(
                "Deep diagnostics",
                subtitle = "Attach trace, physical channels, carrier settings",
                onClick = { navController.navigate("network/diagnostics") },
                leading = { IconDot(Icons.Filled.Insights, StatusTone.ACCENT) },
            ) { Chevron() }
            GroupDivider()
            ListRow("Nearby and other measured cells", subtitle = "${ui.neighbours.size} cells, not part of this SIM's carriers", onClick = { sheet = nearbyListSheet(ui) }) { Chevron() }
            GroupDivider()
            ListRow("Connection history", subtitle = "${ui.events.size} changes this session", onClick = { sheet = historySheet(ui, vm::clearLog) }) { Chevron() }
            GroupDivider()
            ListRow("All measurements", subtitle = "Every value the modem reports", onClick = { sheet = allMeasurementsSheet(ui, name) }) { Chevron() }
            GroupDivider()
            ListRow("How to read the measurements", onClick = { sheet = guideSheet() }) { Chevron() }
        }
        if (ui.lastSampleAt > 0) Text("${if (ui.sampling && ui.available) "Updated" else "Last reading"} ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ui.lastSampleAt))} · ${ui.serviceState}", fontSize = 11.sp, color = muted, modifier = Modifier.padding(horizontal = 6.dp))
        PageEnd()
    }
    sheet?.let { details ->
        val inst = LocalInstrument.current
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (inst.isDark) Color(0xF21C212A) else Color(0xF7FFFFFF),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 5.dp).background(muted.copy(alpha = 0.4f), CircleShape)) },
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(details.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp, modifier = Modifier.padding(horizontal = 4.dp))
                Text(details.explanation, fontSize = 12.5.sp, lineHeight = 18.sp, color = muted, modifier = Modifier.padding(horizontal = 4.dp))
                if (details.readings.isNotEmpty()) {
                    GroupCard(Modifier.padding(top = 4.dp)) {
                        details.readings.forEachIndexed { index, reading ->
                            if (index > 0) GroupDivider()
                            ListRow(reading.name, subtitle = reading.meaning.ifBlank { null }) {
                                Text(reading.value, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = accent, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 150.dp))
                            }
                        }
                    }
                }
                details.raw?.let { raw ->
                    var expanded by remember(details.title) { mutableStateOf(false) }
                    QuietButton(if (expanded) "Hide Android source data" else "Show Android source data", { expanded = !expanded })
                    if (expanded) SelectionContainer { Text(raw, fontFamily = Mono, fontSize = 11.sp) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    val action = details.action
                    val actionLabel = details.actionLabel
                    if (action != null && actionLabel != null) {
                        TonalPillButton(actionLabel, {
                            action()
                            sheet = null
                        }, danger = true, small = true)
                    }
                    TonalPillButton("Done", { sheet = null }, small = true)
                }
            }
        }
    }
}

/** One active carrier: role chip, name and width, with the width bar underneath. */
@Composable private fun CarrierRow(carrier: LiveCarrier, widestKhz: Int, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val primary = carrier.rat == "NR" || carrier.role != "Extra 4G carrier"
    val width = carrier.bandwidthKhz?.takeIf { it > 0 && it != Int.MAX_VALUE }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when { carrier.rat == "NR" -> "5G"; carrier.role == "Extra 4G carrier" -> "SCC"; else -> "PCC" },
                fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold,
                color = if (primary) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.background(if (primary) accent.copy(alpha = .12f) else LocalInstrument.current.hairline, RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Text("${carrier.band} · ${bandName(carrier.band)}${carrier.rat}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(width?.let { "${formatNumber(it / 1000.0)} MHz" } ?: "—", fontSize = 12.sp, fontFamily = Mono)
        }
        MeterBar(if (width != null && widestKhz > 0) width.toFloat() / widestKhz else 0f, accent, Modifier.padding(start = 44.dp), thickness = 5.dp)
    }
}

/** Common LTE/NR band names, as in the concept ("B3 · 1800 LTE"). */
private fun bandName(band: String): String = when (band) {
    "B1" -> "2100 "; "B3" -> "1800 "; "B5" -> "850 "; "B7" -> "2600 "; "B8" -> "900 "; "B20" -> "800 "; "B28" -> "700 "
    "B38" -> "2600 "; "B40" -> "2300 "; "B41" -> "2500 "; "n78" -> "3500 "; "n77" -> "3700 "; "n41" -> "2500 "; "n28" -> "700 "
    else -> ""
}

private const val NEARBY_TITLE = "Nearby and other measured cells"
private const val HISTORY_TITLE = "Connection history"

private fun nearbyListSheet(ui: NetworkState) = ReadingSheet(
    NEARBY_TITLE,
    "Measured cells without a confirmed role in this SIM's carrier combination. They are not counted as CA. Another SIM may also appear here.",
    ui.neighbours.map { c ->
        Reading(
            "${c.type} ${c.band} · PCI ${radioReading(c.pci)}",
            radioReading(c.rsrp, "dBm"),
            "Channel ${radioReading(c.channel)} · RSRQ ${radioReading(c.rsrq, "dB")} · operator ${c.operator.ifBlank { "not reported" }}",
        )
    }.ifEmpty { listOf(Reading("No other cells reported", "", "")) },
)

private fun historySheet(ui: NetworkState, clear: () -> Unit) = ReadingSheet(
    HISTORY_TITLE,
    "Changes recorded while this page was open, newest first.",
    ui.events.take(30).map { Reading("${it.clock} · ${it.title}", "", it.detail) }
        .ifEmpty { listOf(Reading("No changes recorded in this session.", "", "")) },
    actionLabel = if (ui.events.isNotEmpty()) "Clear history" else null,
    action = clear,
)

private fun guideSheet() = ReadingSheet(
    "How to read the measurements",
    "What each value means. Missing values were not supplied by the phone; they do not mean zero.",
    listOf(
        Reading("RSRP", "", "Useful signal strength. Less negative is stronger."),
        Reading("RSRQ", "", "Signal quality; less negative is usually better."),
        Reading("SINR", "", "Signal clarity; higher is better."),
        Reading("RSSI", "", "Total received power including noise. When the modem omits it, it is calculated from RSRP, RSRQ and the channel width, and marked calculated."),
        Reading("CQI", "", "Radio quality used to select coding, not a speed score."),
        Reading("EARFCN / NR-ARFCN", "", "The exact LTE / 5G channel."),
        Reading("PCI", "", "A reusable radio cell identity, not a unique tower ID."),
        Reading("TAC", "", "The tracking area used for mobility and paging."),
        Reading("Cell ID", "", "The network's cell identifier. eNB / sector is derived from the LTE cell ID."),
        Reading("Bandwidth", "", "Radio spectrum width, not download speed."),
        Reading("Timing advance", "", "Timing compensation. Distance is estimated from it (about 78 m per LTE step) and is approximate."),
        Reading("Not reported", "", "The phone supplied no valid value; it does not mean zero."),
    ),
)

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
    val rssiCalculated = if (c.rssi == null && c.type == "LTE") RadioMath.estimateLteRssi(c.rsrp, c.rsrq, c.bandwidthKhz) else null
    add(Reading("RSSI", c.rssi?.let { "$it dBm" } ?: rssiCalculated?.let { "≈$it dBm (calculated)" } ?: "Not reported", "Total received power, including interference; different from reference-signal strength. When the modem omits it, it is calculated as RSRP + 10·log10(resource blocks) − RSRQ."))
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
    add(Reading("Distance to cell (estimate)", RadioMath.distanceMetres(c.type, c.timingAdvance)?.let(RadioMath::formatDistance) ?: "Not reported", "Estimated from timing advance: about 78 m per LTE step, or 150 m per microsecond for 5G. Approximate; reflections and network rounding affect it. Zero means not measured."))
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

