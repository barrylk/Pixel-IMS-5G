package dev.bluehouse.enablevolte.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.components.BottomBarSpacer
import dev.bluehouse.enablevolte.components.floatingGlass
import dev.bluehouse.enablevolte.components.calmSurface
import dev.bluehouse.enablevolte.*
import dev.bluehouse.enablevolte.components.LiquidPane
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class HomeSim(val id: Int, val name: String, val slot: Int)

/** Pure content: previews cannot connect a privileged service or modify the phone. */
@Composable
internal fun HomeDashboard(
    sims: List<HomeSim>, selectedId: Int?, ui: SetupState, accessMode: String,
    onSelectSim: (Int) -> Unit, onRefresh: () -> Unit, onNavigate: (String) -> Unit,
    onDismissNote: () -> Unit, onFix: (ReadinessCheck) -> Unit,
    patchContent: @Composable () -> Unit = {},
) {
    val inst = LocalInstrument.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val selected = sims.firstOrNull { it.id == selectedId }
    val setupChecks = ui.checks.filterNot { it.id == "endc" || it.id == "ims_reg" }

    val refreshing = ui.loading || ui.refreshing
    val access = ui.checks.firstOrNull { it.id == "privilege" }
    val patch = ui.checks.firstOrNull { it.id == "patch" }
    val ims = ui.checks.firstOrNull { it.id == "ims" }
    val headline = when {
        selected == null -> "No active SIM"
        ui.loading -> "Checking setup…"
        access?.status == CheckStatus.FAIL -> "Connect $accessMode"
        setupChecks.any { it.status == CheckStatus.FAIL } -> "Review your setup"
        else -> "Settings checked"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Home", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onRefresh, enabled = !refreshing && ui.busyFix == null,
                shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp)) {
                if (refreshing) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (refreshing) "Checking" else "Check now", fontSize = 12.sp)
            }
        }
        if (sims.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().floatingGlass(RoundedCornerShape(28.dp))
                .horizontalScroll(rememberScrollState()).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                sims.forEach { sim ->
                    val active = sim.id == selectedId
                    Box(Modifier.then(if (active) Modifier.calmSurface(RoundedCornerShape(23.dp)) else Modifier)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelectSim(sim.id) }).padding(horizontal = 16.dp, vertical = 13.dp)) {
                        Text("${sim.name} · SIM ${sim.slot}", fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) accent else muted)
                    }
                }
            }
        }
        LiquidPane {
            Text(selected?.name ?: "Setup & access", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(headline, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
            Text(when {
                selected == null -> "Insert or enable a SIM. Setup options appear when access is connected and Android reports a subscription."
                ui.loading -> "Reading access, calling settings and modem compatibility for this SIM."
                access?.status == CheckStatus.FAIL -> access.detail
                else -> "Manage access, calling settings and modem compatibility here. Open Live network for connection status, carrier aggregation and signal measurements."
            }, fontSize = 13.sp, lineHeight = 19.sp, color = muted)
            Text(if (ui.checkedAt > 0) "${if (refreshing) "Updating · last check" else "Setup checked"} ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ui.checkedAt))}"
                else "Setup checks do not confirm a live 5G connection", fontSize = 10.sp, color = muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeMetric("5G mode setting", ui.checks.firstOrNull { it.id == "nr_mode" }?.let { if (it.status == CheckStatus.PASS) "Enabled" else if (it.status == CheckStatus.FAIL) "Off" else "Not reported" } ?: if (ui.loading) "Checking" else "Not reported",
                "An enabled mode allows 5G; it does not prove a connection.", accent, Modifier.weight(1f))
            HomeMetric("Calling support", when { ui.loading -> "Checking"; ims?.status == CheckStatus.PASS -> "IMS supported"; ims?.status == CheckStatus.FAIL -> "Not supported"; else -> "Not reported" },
                "Device support for IP calling. Current IMS registration is in Network.", if (ims?.status == CheckStatus.PASS) inst.good else muted, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeMetric("$accessMode access", when { ui.loading -> "Checking"; access?.status == CheckStatus.PASS -> "Connected"; access?.status == CheckStatus.FAIL -> "Disconnected"; else -> "Not reported" },
                "Used to read and apply supported phone settings.", if (access?.status == CheckStatus.PASS) inst.good else muted, Modifier.weight(1f))
            HomeMetric("Modem compatibility", when { ui.loading -> "Checking"; patch?.status == CheckStatus.PASS -> "Patch mounted"; patch == null -> "Not reported"; accessMode == "Shizuku" -> "Root required"; patch.status == CheckStatus.UNKNOWN -> "Not reported"; else -> "Check below" },
                "Patch status is separate from an actual 5G connection.", if (patch?.status == CheckStatus.PASS) inst.good else muted, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeShortcut("Live network", "Carriers & signal", Icons.Filled.SignalCellularAlt, sims.isNotEmpty(), { onNavigate("network") }, Modifier.weight(1f))
            HomeShortcut("Field test", "Capture a report", Icons.Filled.Science, sims.isNotEmpty(), { onNavigate("field-test") }, Modifier.weight(1f))
        }
        ui.note?.let { note ->
            LiquidPane {
                Text(note, fontSize = 13.sp, lineHeight = 19.sp)
                TextButton(onClick = onDismissNote) { Text("Dismiss") }
            }
        }
        if (selected != null) {
            Text("Setup checks", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp))
            LiquidPane {
                Text("Review access, enabled features and compatibility before applying a change. Live connection measurements stay in Network.", fontSize = 12.sp, lineHeight = 18.sp, color = muted)
                if (ui.loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Reading this SIM…", fontSize = 13.sp)
                }
                setupChecks.forEachIndexed { index, check ->
                    if (index > 0) HorizontalDivider(color = muted.copy(alpha = .12f), modifier = Modifier.padding(vertical = 6.dp))
                    HomeCheck(check, busy = ui.busyFix == check.fix && check.fix != FixAction.NONE,
                        enabled = ui.busyFix == null && !refreshing, onFix = { onFix(check) })
                }
            }
        }
        patchContent()
        LiquidPane {
            HomeLink("Controls", "Review carrier settings and choose a SIM", Icons.Filled.Tune) { onNavigate("controls") }
            HorizontalDivider(color = muted.copy(alpha = .12f))
            HomeLink("How to use", "Setup, connection states and troubleshooting", Icons.AutoMirrored.Filled.MenuBook) { onNavigate("home/how-to") }
        }
        Text("Pixel IMS 5G · ${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = muted, modifier = Modifier.padding(horizontal = 3.dp))
        Spacer(Modifier.height(18.dp))
        BottomBarSpacer()
    }
}

@Composable private fun HomeMetric(label: String, value: String, meaning: String, tone: Color, modifier: Modifier) {
    LiquidPane(modifier) {
        Text(label, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = tone)
        Text(meaning, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HomeShortcut(title: String, detail: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.calmSurface(RoundedCornerShape(24.dp)).clickable(enabled = enabled, onClick = onClick)
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(23.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HomeCheck(check: ReadinessCheck, busy: Boolean, enabled: Boolean, onFix: () -> Unit) {
    val inst = LocalInstrument.current
    val tone = when (check.status) { CheckStatus.PASS -> inst.good; CheckStatus.FAIL -> if (check.id == "endc") inst.fair else inst.poor; CheckStatus.WARN -> inst.fair; CheckStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 3.dp).size(23.dp).background(tone.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(when (check.status) { CheckStatus.PASS -> Icons.Filled.Check; CheckStatus.FAIL, CheckStatus.WARN -> Icons.Filled.PriorityHigh; CheckStatus.UNKNOWN -> Icons.Filled.QuestionMark }, null, Modifier.size(14.dp), tint = tone)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(check.title, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(when (check.status) { CheckStatus.PASS -> "Confirmed"; CheckStatus.FAIL -> if (check.id == "endc") "Not connected" else "Needs attention"; CheckStatus.WARN -> "Review"; CheckStatus.UNKNOWN -> "Unknown" }, fontSize = 10.sp, lineHeight = 15.sp, color = tone, modifier = Modifier.widthIn(max = 95.dp))
            }
            Text(check.detail, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (check.fixLabel != null && check.fix != FixAction.NONE) {
                FilledTonalButton(onClick = onFix, enabled = enabled && !busy, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
                    if (busy) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(7.dp)) }
                    Text(if (busy) "Applying…" else check.fixLabel, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable private fun HomeLink(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
