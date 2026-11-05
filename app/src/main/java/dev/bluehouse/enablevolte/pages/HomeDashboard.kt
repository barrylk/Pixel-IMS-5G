package dev.bluehouse.enablevolte.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.CheckStatus
import dev.bluehouse.enablevolte.FixAction
import dev.bluehouse.enablevolte.ReadinessCheck
import dev.bluehouse.enablevolte.SetupState
import dev.bluehouse.enablevolte.components.BottomBarSpacer
import dev.bluehouse.enablevolte.components.Chevron
import dev.bluehouse.enablevolte.components.GroupCard
import dev.bluehouse.enablevolte.components.GroupDivider
import dev.bluehouse.enablevolte.components.GroupLabel
import dev.bluehouse.enablevolte.components.HeroCard
import dev.bluehouse.enablevolte.components.ListRow
import dev.bluehouse.enablevolte.components.NoteBox
import dev.bluehouse.enablevolte.components.PageTitle
import dev.bluehouse.enablevolte.components.SimPicker
import dev.bluehouse.enablevolte.components.StatusDot
import dev.bluehouse.enablevolte.components.StatusPill
import dev.bluehouse.enablevolte.components.StatusTone
import dev.bluehouse.enablevolte.components.TonalPillButton
import dev.bluehouse.enablevolte.components.calmSurface
import dev.bluehouse.enablevolte.components.floatingGlass
import dev.bluehouse.enablevolte.components.statusToneColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class HomeSim(val id: Int, val name: String, val slot: Int)

/** Pure content: previews cannot connect a privileged service or modify the phone. */
@Composable
internal fun HomeDashboard(
    sims: List<HomeSim>,
    selectedId: Int?,
    ui: SetupState,
    accessMode: String,
    onSelectSim: (Int) -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (String) -> Unit,
    onDismissNote: () -> Unit,
    onFix: (ReadinessCheck) -> Unit,
    patchContent: @Composable () -> Unit = {},
) {
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

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle("Home") {
            Row(
                Modifier
                    .floatingGlass(CircleShape)
                    .clickable(enabled = !refreshing && ui.busyFix == null, onClick = onRefresh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(17.dp))
                }
                Spacer(Modifier.width(7.dp))
                Text(if (refreshing) "Checking" else "Check now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        SimPicker(
            items = sims.map { it.id to "${it.name} · SIM ${it.slot}" },
            selectedId = selectedId,
            onSelect = onSelectSim,
        )
        HeroCard(
            title = headline,
            eyebrow = listOfNotNull(selected?.name, "$accessMode access").joinToString(" · "),
            body = when {
                selected == null -> "Insert or enable a SIM. Setup options appear when access is connected and Android reports a subscription."
                ui.loading -> "Reading access, calling settings and modem compatibility for this SIM."
                access?.status == CheckStatus.FAIL -> access.detail
                else -> "Manage access, calling settings and modem compatibility here. Open Live network for connection status, carrier aggregation and signal measurements."
            },
            footnote = if (ui.checkedAt > 0) {
                "${if (refreshing) "Updating · last check" else "Setup checked"} " +
                    "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ui.checkedAt))} · does not confirm a live 5G connection"
            } else {
                "Setup checks do not confirm a live 5G connection"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile(
                "5G mode setting",
                ui.checks.firstOrNull { it.id == "nr_mode" }?.let {
                    if (it.status == CheckStatus.PASS) "Enabled" else if (it.status == CheckStatus.FAIL) "Off" else "Not reported"
                } ?: if (ui.loading) "Checking" else "Not reported",
                "An enabled mode allows 5G; it does not prove a connection.",
                StatusTone.ACCENT,
                Modifier.weight(1f),
            )
            HomeTile(
                "Calling support",
                when {
                    ui.loading -> "Checking"
                    ims?.status == CheckStatus.PASS -> "IMS supported"
                    ims?.status == CheckStatus.FAIL -> "Not supported"
                    else -> "Not reported"
                },
                "Device support for IP calling. Current IMS registration is in Network.",
                if (ims?.status == CheckStatus.PASS) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile(
                "$accessMode access",
                when {
                    ui.loading -> "Checking"
                    access?.status == CheckStatus.PASS -> "Connected"
                    access?.status == CheckStatus.FAIL -> "Disconnected"
                    else -> "Not reported"
                },
                "Used to read and apply supported phone settings.",
                when (access?.status) {
                    CheckStatus.PASS -> StatusTone.SUCCESS
                    CheckStatus.FAIL -> StatusTone.DANGER
                    else -> StatusTone.NEUTRAL
                },
                Modifier.weight(1f),
            )
            HomeTile(
                "Modem compatibility",
                when {
                    ui.loading -> "Checking"
                    patch?.status == CheckStatus.PASS -> "Patch mounted"
                    patch == null -> "Not reported"
                    accessMode == "Shizuku" -> "Root required"
                    patch.status == CheckStatus.UNKNOWN -> "Not reported"
                    else -> "Check below"
                },
                "Patch status is separate from an actual 5G connection.",
                if (patch?.status == CheckStatus.PASS) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeShortcut("Live network", "Carriers & signal", Icons.Filled.SignalCellularAlt, sims.isNotEmpty(), { onNavigate("network") }, Modifier.weight(1f))
            HomeShortcut("Field test", "Capture a report", Icons.Filled.Science, sims.isNotEmpty(), { onNavigate("field-test") }, Modifier.weight(1f))
        }
        ui.note?.let { note ->
            NoteBox(note, StatusTone.ACCENT)
            TonalPillButton("Dismiss", onDismissNote, small = true)
        }
        if (selected != null) {
            GroupLabel("Setup checks")
            Text(
                "Review access, enabled features and compatibility before applying a change. Live connection measurements stay in Network.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            GroupCard {
                if (ui.loading) {
                    ListRow(
                        title = "Reading this SIM…",
                        leading = { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                    )
                }
                setupChecks.forEachIndexed { index, check ->
                    if (index > 0 || ui.loading) GroupDivider()
                    HomeCheck(
                        check,
                        busy = ui.busyFix == check.fix && check.fix != FixAction.NONE,
                        enabled = ui.busyFix == null && !refreshing,
                        onFix = { onFix(check) },
                    )
                }
            }
        }
        patchContent()
        GroupCard {
            HomeLink("Controls", "Review carrier settings and choose a SIM", Icons.Filled.Tune) { onNavigate("controls") }
            GroupDivider()
            HomeLink("How to use", "Setup, connection states and troubleshooting", Icons.AutoMirrored.Filled.MenuBook) { onNavigate("home/how-to") }
        }
        Text(
            "Pixel IMS 5G · ${BuildConfig.VERSION_NAME}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Spacer(Modifier.size(8.dp))
        BottomBarSpacer()
    }
}

@Composable
private fun HomeTile(
    label: String,
    value: String,
    meaning: String,
    tone: StatusTone,
    modifier: Modifier,
) {
    val color = if (tone == StatusTone.NEUTRAL) MaterialTheme.colorScheme.onSurface else statusToneColor(tone)
    Column(
        modifier.calmSurface(RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = color)
        Text(meaning, fontSize = 10.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeShortcut(
    title: String,
    detail: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.calmSurface(RoundedCornerShape(20.dp)).clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconDot(icon, if (enabled) StatusTone.ACCENT else StatusTone.NEUTRAL)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An icon in a soft round tint, used for shortcuts and links. */
@Composable
private fun IconDot(
    icon: ImageVector,
    tone: StatusTone,
) {
    val color = statusToneColor(tone)
    Box(Modifier.size(30.dp).background(color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(17.dp), tint = color)
    }
}

@Composable
private fun HomeCheck(
    check: ReadinessCheck,
    busy: Boolean,
    enabled: Boolean,
    onFix: () -> Unit,
) {
    val tone = when (check.status) {
        CheckStatus.PASS -> StatusTone.SUCCESS
        CheckStatus.FAIL -> if (check.id == "endc") StatusTone.WARNING else StatusTone.DANGER
        CheckStatus.WARN -> StatusTone.WARNING
        CheckStatus.UNKNOWN -> StatusTone.NEUTRAL
    }
    val fixLabel = check.fixLabel
    ListRow(
        title = check.title,
        subtitle = check.detail,
        leading = {
            StatusDot(
                tone,
                when (check.status) {
                    CheckStatus.PASS -> "✓"
                    CheckStatus.FAIL, CheckStatus.WARN -> "!"
                    CheckStatus.UNKNOWN -> "?"
                },
            )
        },
        below = if (fixLabel != null && check.fix != FixAction.NONE) {
            {
                Spacer(Modifier.size(4.dp))
                TonalPillButton(if (busy) "Applying…" else fixLabel, onFix, enabled = enabled && !busy, small = true)
            }
        } else {
            null
        },
    ) {
        StatusPill(
            when (check.status) {
                CheckStatus.PASS -> "Confirmed"
                CheckStatus.FAIL -> if (check.id == "endc") "Not connected" else "Needs attention"
                CheckStatus.WARN -> "Review"
                CheckStatus.UNKNOWN -> "Unknown"
            },
            tone,
        )
    }
}

@Composable
private fun HomeLink(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = { IconDot(icon, StatusTone.ACCENT) },
    ) { Chevron() }
}
