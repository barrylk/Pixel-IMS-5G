package dev.bluehouse.enablevolte.pages

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.telephony.SubscriptionInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SriLankaCarrierProfiles
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.components.GlassSurface
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.uniqueName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Monitor(subscriptions: List<SubscriptionInfo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSubId by rememberSaveable { mutableStateOf(subscriptions.firstOrNull()?.subscriptionId ?: -1) }
    val selectedSubscription = subscriptions.firstOrNull { it.subscriptionId == selectedSubId }
    var snapshot by remember { mutableStateOf<SubscriptionModer.RadioDiagnostics?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember(selectedSubId) { mutableStateOf(listOf<Int>()) }
    var profileResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedSubId) {
        if (selectedSubId < 0) return@LaunchedEffect
        while (true) {
            runCatching {
                withContext(Dispatchers.IO) {
                    SubscriptionModer(context, selectedSubId).getRadioDiagnostics()
                }
            }.onSuccess {
                snapshot = it
                error = null
                it.cells.firstOrNull { cell -> cell.registered }?.dbm?.let { dbm ->
                    history = (history + dbm).takeLast(40)
                }
            }.onFailure { error = it.message ?: "Telephony data is unavailable" }
            delay(2_500)
        }
    }

    val profile = selectedSubscription?.let {
        SriLankaCarrierProfiles.find(it.mccString, it.mncString)
    }
    val wifi = remember(snapshot) { wifiSummary(context) }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderText(text = stringResource(R.string.network_monitor))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subscriptions.forEach { subscription ->
                FilterChip(
                    selected = subscription.subscriptionId == selectedSubId,
                    onClick = {
                        selectedSubId = subscription.subscriptionId
                        snapshot = null
                        profileResult = null
                    },
                    label = { Text(subscription.uniqueName) },
                )
            }
        }

        error?.let { StatusCard(stringResource(R.string.monitor_error), it, MaterialTheme.colorScheme.error) }
        snapshot?.let { data ->
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.connection_summary), style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPill(data.displayTechnology, Icons.Filled.SignalCellularAlt)
                        if (data.usingCarrierAggregation) {
                            StatusPill(stringResource(R.string.lte_plus_active), Icons.Filled.SignalCellularAlt)
                        }
                        if (data.imsRegistered && data.imsTransport == "IWLAN") {
                            StatusPill(stringResource(R.string.vowifi), Icons.Filled.Wifi)
                        }
                    }
                    KeyValue(stringResource(R.string.service_state), data.serviceState)
                    KeyValue(stringResource(R.string.data_radio), data.displayTechnology)
                    KeyValue(stringResource(R.string.raw_radio_state), "${data.dataRat} · NR state ${data.nrState}")
                    KeyValue(
                        stringResource(R.string.carrier_aggregation),
                        if (data.usingCarrierAggregation) {
                            stringResource(R.string.active_lte_plus)
                        } else {
                            stringResource(R.string.not_active)
                        },
                    )
                    KeyValue(stringResource(R.string.ims_transport), data.imsTransport)
                    KeyValue(
                        stringResource(R.string.vowifi_state),
                        if (data.imsRegistered && data.imsTransport == "IWLAN") {
                            stringResource(R.string.vowifi_active)
                        } else {
                            stringResource(R.string.vowifi_not_active)
                        },
                    )
                    KeyValue(stringResource(R.string.wifi_link), wifi)
                    KeyValue(
                        stringResource(R.string.nsa_capability),
                        when {
                            data.endcAvailable == true -> stringResource(R.string.endc_available)
                            data.nrAvailable == true -> stringResource(R.string.nr_without_endc)
                            else -> stringResource(R.string.nr_not_reported)
                        },
                    )
                    Text(
                        stringResource(R.string.iwlan_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (history.size > 1) {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.signal_history), style = MaterialTheme.typography.titleMedium)
                        SignalChart(history)
                        Text(
                            "${history.last()} dBm · ${stringResource(R.string.last_samples, history.size)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            HeaderText(text = stringResource(R.string.visible_cells))
            if (data.cells.isEmpty()) {
                StatusCard(stringResource(R.string.none_reported), stringResource(R.string.cell_visibility_limit))
            }
            data.cells.forEachIndexed { index, cell ->
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${if (cell.registered) "Serving" else "Neighbor"} ${cell.type} ${cell.band}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (cell.registered) Color(0xFF21A366) else MaterialTheme.colorScheme.onSurface,
                            )
                            Text("#${index + 1}")
                        }
                        KeyValue("MCC-MNC", cell.operator.ifBlank { "—" })
                        KeyValue("Channel / PCI", "${cell.channel} / ${cell.pci}")
                        KeyValue("TAC / Cell ID", "${cell.tac} / ${cell.cellId}")
                        KeyValue(
                            "Signal",
                            "${cell.dbm} dBm · RSRP ${cell.rsrp ?: "—"} · RSRQ ${cell.rsrq ?: "—"} · SINR ${cell.sinr ?: "—"}",
                        )
                        KeyValue(
                            "Radio detail",
                            "level ${cell.level}/4 · RSSI ${cell.rssi ?: "—"} · CQI ${cell.cqi ?: "—"} · " +
                                "TA ${cell.timingAdvance ?: "—"} · BW " +
                                (cell.bandwidthKhz?.let { "${it / 1000f} MHz" } ?: "hidden"),
                        )
                    }
                }
            }
        }

        profile?.let { carrier ->
            HeaderText(text = stringResource(R.string.sri_lanka_carrier_profile))
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${carrier.name} (${carrier.operatorNumeric})", style = MaterialTheme.typography.titleMedium)
                    KeyValue("Sri Lanka LTE", carrier.lteBands.joinToString { "B$it" })
                    KeyValue(
                        "Commercial 5G profile",
                        carrier.nrBands.joinToString { "n$it" }.ifBlank { "not assumed" },
                    )
                    Text(carrier.wifiCallingNote, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.profile_does_not_provision),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            profileResult = context.getString(R.string.applying_profile)
                            Thread {
                                val applied = SubscriptionModer(context, selectedSubId)
                                    .applyCompatibilityProfile(carrier.nrBands.isNotEmpty())
                                context.mainExecutor.execute {
                                    profileResult = context.getString(
                                        if (applied) R.string.profile_applied else R.string.profile_failed,
                                    )
                                }
                            }.start()
                        },
                    ) { Text(stringResource(R.string.apply_compatibility_profile)) }
                    profileResult?.let { Text(it) }
                }
            }
        }

        Text(
            stringResource(R.string.monitor_limit_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusPill(label: String, icon: ImageVector) {
    Surface(
        color = Color(0xFF198754),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(label)
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StatusCard(title: String, body: String, titleColor: Color = MaterialTheme.colorScheme.onSurface) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Text(body)
        }
    }
}

@Composable
private fun SignalChart(values: List<Int>) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(92.dp).padding(vertical = 10.dp)) {
        if (values.size < 2) return@Canvas
        val minimum = -125f
        val maximum = -55f
        val dx = size.width / (values.size - 1)
        values.zipWithNext().forEachIndexed { index, pair ->
            fun y(value: Int): Float =
                size.height - ((value.coerceIn(minimum.toInt(), maximum.toInt()) - minimum) / (maximum - minimum)) * size.height
            drawLine(
                lineColor,
                Offset(index * dx, y(pair.first)),
                Offset((index + 1) * dx, y(pair.second)),
                strokeWidth = 4f,
            )
        }
    }
}

private fun wifiSummary(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "Disconnected"
    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Not active"
    val info = capabilities.transportInfo as? WifiInfo
    val frequency = info?.frequency?.takeIf { it > 0 }
    return if (frequency != null) "$frequency MHz (Wi-Fi)" else "Connected (frequency hidden)"
}
