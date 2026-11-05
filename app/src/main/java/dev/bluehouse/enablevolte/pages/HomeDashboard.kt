package dev.bluehouse.enablevolte.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.CheckStatus
import dev.bluehouse.enablevolte.FixAction
import dev.bluehouse.enablevolte.ReadinessCheck
import dev.bluehouse.enablevolte.SetupState
import dev.bluehouse.enablevolte.components.Chevron
import dev.bluehouse.enablevolte.components.GlassIconButton
import dev.bluehouse.enablevolte.components.GroupCard
import dev.bluehouse.enablevolte.components.GroupDivider
import dev.bluehouse.enablevolte.components.GroupLabel
import dev.bluehouse.enablevolte.components.HeroCard
import dev.bluehouse.enablevolte.components.IconDot
import dev.bluehouse.enablevolte.components.ListRow
import dev.bluehouse.enablevolte.components.LocalAppActions
import dev.bluehouse.enablevolte.components.NoteBox
import dev.bluehouse.enablevolte.components.PageEnd
import dev.bluehouse.enablevolte.components.PageTitle
import dev.bluehouse.enablevolte.components.SimPicker
import dev.bluehouse.enablevolte.components.StatusDot
import dev.bluehouse.enablevolte.components.StatusPill
import dev.bluehouse.enablevolte.components.StatusTone
import dev.bluehouse.enablevolte.components.TonalPillButton
import dev.bluehouse.enablevolte.components.calmSurface
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
    patchContent: (@Composable () -> Unit)? = null,
) {
    val actions = LocalAppActions.current
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
        Modifier.fillMaxSize().padding(horizontal = 14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle("Home") {
            GlassIconButton(
                Icons.Filled.Refresh,
                "Check now",
                {
                    actions.onReload()
                    onRefresh()
                },
                enabled = ui.busyFix == null,
                busy = refreshing,
            )
            GlassIconButton(Icons.Filled.Info, "About", actions.onAbout)
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
                setupChecks.any { it.status == CheckStatus.FAIL } -> "Some settings need attention. Review the checks below before applying a change."
                else -> "Access, calling settings and modem compatibility look right. Open Network for the live connection."
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
                "Allows 5G; not proof of a connection",
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
                "Registration is in Network",
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
                "Reads and applies settings",
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
                "Separate from 5G connection",
                if (patch?.status == CheckStatus.PASS) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                Modifier.weight(1f),
            )
        }
        ui.note?.let { note ->
            NoteBox(note, StatusTone.ACCENT)
            TonalPillButton("Dismiss", onDismissNote, small = true)
        }

        if (patchContent != null) {
            GroupLabel("Regional 5G patch")
            patchContent()
        }

        if (selected != null) {
            GroupLabel("Setup checks")
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

        GroupCard(Modifier.padding(top = 2.dp)) {
            ListRow(
                "Controls",
                subtitle = "Carrier settings for a SIM",
                onClick = { onNavigate("controls") },
                leading = { IconDot(Icons.Filled.Tune, StatusTone.ACCENT) },
            ) { Chevron() }
            if (selectedId != null) {
                GroupDivider()
                ListRow(
                    "Bands",
                    subtitle = "LTE and 5G bands for this SIM",
                    onClick = { onNavigate("bands/$selectedId") },
                    leading = { IconDot(Icons.Filled.SignalCellularAlt, StatusTone.ACCENT) },
                ) { Chevron() }
            }
            GroupDivider()
            ListRow(
                "Deep diagnostics",
                subtitle = "Attach trace, physical channels, carrier settings",
                onClick = { onNavigate("network/diagnostics") },
                leading = { IconDot(Icons.Filled.Insights, StatusTone.ACCENT) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "How to use",
                subtitle = "Setup, states, troubleshooting",
                onClick = { onNavigate("home/how-to") },
                leading = { IconDot(Icons.AutoMirrored.Filled.MenuBook, StatusTone.ACCENT) },
            ) { Chevron() }
            if (actions.restoreAvailable) {
                GroupDivider()
                ListRow(
                    "Restore and reboot",
                    subtitle = "Undo applied changes",
                    onClick = actions.onRestore,
                    leading = { IconDot(Icons.Filled.PowerSettingsNew, StatusTone.DANGER) },
                ) { Chevron() }
            }
        }
        Text(
            "Pixel IMS 5G · ${BuildConfig.VERSION_NAME} · ${actions.appearanceLabel}",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = actions.onCycleAppearance).padding(horizontal = 6.dp, vertical = 4.dp),
        )
        PageEnd()
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
        Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp, color = color)
        Text(meaning, fontSize = 10.5.sp, lineHeight = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Spacer(Modifier.size(5.dp))
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
