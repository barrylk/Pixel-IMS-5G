package dev.bluehouse.enablevolte.pages

import android.app.Application
import android.telephony.SubscriptionInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.CellEvent
import dev.bluehouse.enablevolte.CellEventKind
import dev.bluehouse.enablevolte.NetworkMonitorViewModel
import dev.bluehouse.enablevolte.NetworkState
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import dev.bluehouse.enablevolte.components.Panel
import dev.bluehouse.enablevolte.components.PanelGroup
import dev.bluehouse.enablevolte.components.PremiumActionRow
import dev.bluehouse.enablevolte.components.RowDivider
import dev.bluehouse.enablevolte.components.SignalBars
import dev.bluehouse.enablevolte.components.SignalChip
import dev.bluehouse.enablevolte.components.SignalGauge
import dev.bluehouse.enablevolte.components.Sparkline
import dev.bluehouse.enablevolte.components.SpecTile
import dev.bluehouse.enablevolte.components.qualityColor
import dev.bluehouse.enablevolte.components.qualityOfRsrp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import dev.bluehouse.enablevolte.ui.theme.ReadoutSmall
import dev.bluehouse.enablevolte.uniqueName

/**
 * The live radio.
 *
 * Everything here is sampled continuously rather than captured once when the
 * page opened — the point of a monitor is that it keeps looking.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun NetworkPage(
    subscriptions: List<SubscriptionInfo>,
    navController: NavController,
) {
    val context = LocalContext.current
    var slot by rememberSaveable { mutableIntStateOf(0) }
    val subscription = subscriptions.getOrNull(slot)
    val scrollState = rememberScrollState()

    if (subscription == null) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.controls_no_sim),
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        return
    }

    val viewModel: NetworkMonitorViewModel =
        viewModel(
            key = "network-${subscription.subscriptionId}",
            factory = NetworkMonitorViewModel.factory(
                context.applicationContext as Application,
                subscription.subscriptionId,
            ),
        )
    val ui by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ui.serving?.operator?.takeIf { it.isNotBlank() } ?: subscription.uniqueName,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ui.serviceState.takeIf { it.isNotBlank() } ?: stringResource(R.string.network_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SamplingChip(ui.sampling) { viewModel.toggle() }
        }

        if (subscriptions.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                subscriptions.forEachIndexed { index, sub ->
                    FilterChip(
                        selected = index == slot,
                        onClick = { slot = index },
                        label = { Text(sub.uniqueName, maxLines = 1) },
                    )
                }
            }
        }

        ui.message?.let {
            Panel(Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        ServingPanel(ui)
        ParameterGrid(ui.serving, ui.anchor)
        HistoryPanel(ui)
        NeighbourPanel(ui.neighbours)
        EventPanel(ui.events) { viewModel.clearLog() }

        PanelGroup {
            PremiumActionRow(
                title = stringResource(R.string.network_diagnostics),
                subtitle = stringResource(R.string.network_diagnostics_summary),
                icon = Icons.Filled.Biotech,
                onClick = { navController.navigate("network/diagnostics") },
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SamplingChip(
    sampling: Boolean,
    onToggle: () -> Unit,
) {
    val inst = LocalInstrument.current
    TextButton(onClick = onToggle, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        SignalChip(
            label = if (sampling) stringResource(R.string.network_live) else stringResource(R.string.network_paused),
            tone = if (sampling) inst.good else MaterialTheme.colorScheme.onSurfaceVariant,
            dot = true,
        )
    }
}

@Composable
private fun ServingPanel(ui: NetworkState) {
    val inst = LocalInstrument.current
    val serving = ui.serving
    val rsrp = serving?.rsrp ?: serving?.dbm
    Panel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SignalGauge(
                dbm = rsrp,
                label = if (ui.nrAttached) "SS-RSRP" else "RSRP",
                caption = serving?.let { qualityWord(it.rsrp ?: it.dbm) },
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 14.dp)) {
                SignalChip(
                    label = when {
                        ui.nrAttached && ui.anchor != null -> "5G NSA"
                        ui.nrAttached -> "5G SA"
                        else -> serving?.type ?: "—"
                    },
                    tone = MaterialTheme.colorScheme.primary,
                )
                serving?.band?.takeIf { it.isNotBlank() }?.let {
                    SignalChip(label = it, tone = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (ui.anchor != null) {
                    SignalChip(label = "EN-DC", tone = inst.ember)
                }
            }
        }
    }
}

private fun qualityWord(dbm: Int?): String = when (qualityOfRsrp(dbm)) {
    dev.bluehouse.enablevolte.components.Quality.GOOD -> "Good signal"
    dev.bluehouse.enablevolte.components.Quality.FAIR -> "Fair signal"
    dev.bluehouse.enablevolte.components.Quality.POOR -> "Weak signal"
    dev.bluehouse.enablevolte.components.Quality.UNKNOWN -> "No reading"
}

/** Every parameter the modem reports for the serving cell, and its anchor. */
@Composable
private fun ParameterGrid(
    serving: SubscriptionModer.CellSnapshot?,
    anchor: SubscriptionModer.CellSnapshot?,
) {
    if (serving == null) return
    val inst = LocalInstrument.current
    val entries = buildList {
        add(stringResource(R.string.param_channel) to serving.channel.toString())
        add("PCI" to serving.pci.toString())
        serving.sinr?.let { add("SINR" to "$it dB") }
        serving.rsrq?.let { add("RSRQ" to "$it dB") }
        serving.csiRsrp?.let { add("CSI-RSRP" to "$it dBm") }
        serving.csiSinr?.let { add("CSI-SINR" to "$it dB") }
        serving.rssi?.let { add("RSSI" to "$it dBm") }
        serving.cqi?.let { add("CQI" to it.toString()) }
        serving.bandwidthKhz?.let { add(stringResource(R.string.param_bandwidth) to "${it / 1000} MHz") }
        serving.timingAdvance?.let { add(stringResource(R.string.param_timing_advance) to it.toString()) }
        add("TAC" to serving.tac.toString())
        add(stringResource(R.string.param_cell_id) to serving.cellId)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (k, v) ->
                    SpecTile(
                        label = k,
                        value = v,
                        modifier = Modifier.weight(1f),
                        tone = if (k == "SINR" || k == "CSI-SINR") inst.good else null,
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (anchor != null) {
            Text(
                stringResource(R.string.network_anchor, anchor.band, anchor.channel.toString(), anchor.pci.toString()),
                style = ReadoutSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun HistoryPanel(ui: NetworkState) {
    if (ui.rsrpHistory.size < 2) return
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.network_history), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.network_samples, ui.sampleCount.toString()),
                    style = ReadoutSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Sparkline(values = ui.rsrpHistory)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecTile("min", "${ui.historyMin ?: "--"}", Modifier.weight(1f), qualityColor(qualityOfRsrp(ui.historyMin)))
                SpecTile("mean", "${ui.historyMean ?: "--"}", Modifier.weight(1f))
                SpecTile("max", "${ui.historyMax ?: "--"}", Modifier.weight(1f), qualityColor(qualityOfRsrp(ui.historyMax)))
            }
        }
    }
}

@Composable
private fun NeighbourPanel(cells: List<SubscriptionModer.CellSnapshot>) {
    if (cells.isEmpty()) return
    Column {
        Text(
            stringResource(R.string.network_cells, cells.size.toString()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        PanelGroup {
            cells.forEachIndexed { index, cell ->
                if (index > 0) RowDivider()
                CellRow(cell)
            }
        }
    }
}

@Composable
private fun CellRow(cell: SubscriptionModer.CellSnapshot) {
    val dbm = cell.rsrp ?: cell.dbm
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp)) {
            Text(cell.band.ifBlank { cell.type }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (cell.registered) {
                Text(
                    stringResource(R.string.network_serving),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${cell.type} · ${cell.channel} · PCI ${cell.pci}",
            style = ReadoutSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SignalBars(dbm)
        Spacer(Modifier.width(9.dp))
        Text(
            dbm.toString(),
            style = ReadoutSmall,
            color = qualityColor(qualityOfRsrp(dbm)),
        )
    }
}

@Composable
private fun EventPanel(
    events: List<CellEvent>,
    onClear: () -> Unit,
) {
    val inst = LocalInstrument.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.network_log),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (events.isNotEmpty()) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.network_clear)) }
            }
        }
        if (events.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.network_log_empty),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        PanelGroup {
            events.forEachIndexed { index, event ->
                if (index > 0) RowDivider()
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(26.dp)
                                .background(eventTone(event.kind), MaterialTheme.shapes.extraSmall),
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                event.detail,
                                style = ReadoutSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(event.clock, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.network_log_note),
            style = MaterialTheme.typography.bodySmall,
            color = inst.ember,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

@Composable
private fun eventTone(kind: CellEventKind): androidx.compose.ui.graphics.Color {
    val inst = LocalInstrument.current
    return when (kind) {
        CellEventKind.REGISTERED -> inst.good
        CellEventKind.ENDC -> MaterialTheme.colorScheme.primary
        CellEventKind.HANDOVER -> inst.ember
        CellEventKind.BAND_CHANGE -> MaterialTheme.colorScheme.primary
        CellEventKind.SIGNAL_DROP -> inst.fair
        CellEventKind.LOST -> inst.poor
    }
}
