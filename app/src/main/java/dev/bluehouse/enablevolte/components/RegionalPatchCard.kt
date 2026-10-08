package dev.bluehouse.enablevolte.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.ReadinessRules
import dev.bluehouse.enablevolte.RegionalModemPatchStatus
import dev.bluehouse.enablevolte.RegionalPatchAction
import dev.bluehouse.enablevolte.RegionalPatchViewModel
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import dev.bluehouse.enablevolte.ui.theme.ReadoutSmall

private enum class PatchPhase { CHECKING, READY, REBOOT, ACTIVE, REMOVING, UNAVAILABLE }

private fun phaseOf(status: RegionalModemPatchStatus?, loading: Boolean): PatchPhase = when {
    status == null || (loading && status.message.isBlank()) -> PatchPhase.CHECKING
    status.removalPending -> PatchPhase.REMOVING
    status.installed && status.rebootRequired -> PatchPhase.REBOOT
    status.installed -> PatchPhase.ACTIVE
    ReadinessRules.canInstallPatch(status) -> PatchPhase.READY
    else -> PatchPhase.UNAVAILABLE
}

/**
 * The regional modem patch, as one card.
 *
 * Root users used to find this four levels down, under Controls → Bands, below
 * the band pickers — while it is the one step without which nothing else on
 * the Setup screen can turn into a 5G connection. It now leads the home screen
 * for root users and shrinks to a single status line once it is active. The
 * same card, with the same view model, also stays on the Bands page.
 *
 * The confirmation dialog is owned here so every route to installing the patch
 * — this card's button or the Setup check's fix — passes through it.
 */
@Composable
fun RegionalPatchCard(
    viewModel: RegionalPatchViewModel,
    modifier: Modifier = Modifier,
    compactWhenActive: Boolean = true,
) {
    val ui by viewModel.state.collectAsState()
    val inst = LocalInstrument.current
    val status = ui.status
    val phase = phaseOf(status, ui.loading)
    var expanded by rememberSaveable { mutableStateOf(false) }

    ui.confirming?.let { action -> PatchConfirmDialog(action, viewModel) }

    val tone = when (phase) {
        PatchPhase.ACTIVE -> inst.good
        PatchPhase.REBOOT, PatchPhase.REMOVING -> inst.fair
        PatchPhase.READY -> inst.ember
        PatchPhase.UNAVAILABLE -> inst.poor
        PatchPhase.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val headline = when (phase) {
        PatchPhase.CHECKING -> stringResource(R.string.patch_card_checking)
        PatchPhase.READY -> stringResource(R.string.patch_card_ready)
        PatchPhase.REBOOT -> stringResource(R.string.patch_card_reboot)
        PatchPhase.ACTIVE -> stringResource(R.string.patch_card_active)
        PatchPhase.REMOVING -> stringResource(R.string.patch_card_removing)
        PatchPhase.UNAVAILABLE -> stringResource(R.string.patch_card_unavailable)
    }

    // Once it is working there is nothing to do here: one line, tap for more.
    if (compactWhenActive && phase == PatchPhase.ACTIVE && !expanded) {
        Panel(modifier.fillMaxWidth(), onClick = { expanded = true }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusGlyph(true, tone)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.patch_card_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.patch_card_active_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SignalChip(label = headline, tone = tone, dot = true)
            }
        }
        return
    }

    val prominent = phase == PatchPhase.READY || phase == PatchPhase.REBOOT
    Panel(
        modifier.fillMaxWidth(),
        depth = if (prominent) GlassDepth.TINTED else GlassDepth.REGULAR,
        tint = if (prominent) tone else Color.Unspecified,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).background(tone.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = tone, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.patch_card_step).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = tone,
                    )
                    Text(stringResource(R.string.patch_card_title), style = MaterialTheme.typography.titleLarge)
                }
                if (phase == PatchPhase.CHECKING || ui.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    SignalChip(label = headline, tone = tone, dot = true)
                }
            }

            Text(
                when (phase) {
                    PatchPhase.REBOOT -> stringResource(R.string.patch_card_reboot_body)
                    PatchPhase.REMOVING -> stringResource(R.string.patch_card_removing_body)
                    PatchPhase.ACTIVE -> stringResource(R.string.patch_card_active_body)
                    PatchPhase.UNAVAILABLE -> status?.message.orEmpty()
                    else -> stringResource(R.string.patch_card_pitch)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ui.outcome?.let { outcome ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(tone.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(outcome, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissOutcome) { Text(stringResource(R.string.dismiss)) }
                }
            }

            if (status != null && phase != PatchPhase.CHECKING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Prerequisite(stringResource(R.string.patch_check_device), status.supported)
                    Prerequisite(status.rootManager.ifBlank { stringResource(R.string.patch_check_magisk) }, status.magiskAvailable)
                    Prerequisite(stringResource(R.string.patch_check_firmware), status.sourceAvailable)
                }
            }

            if (status != null) {
                when {
                    ReadinessRules.canInstallPatch(status) ->
                        Button(
                            onClick = { viewModel.requestConfirmation(RegionalPatchAction.INSTALL) },
                            enabled = !ui.busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Text(
                                stringResource(if (ui.busy) R.string.applying_profile else R.string.install_patch),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    ReadinessRules.canRevalidatePatch(status) ->
                        OutlinedButton(
                            onClick = { viewModel.requestConfirmation(RegionalPatchAction.INSTALL) },
                            enabled = !ui.busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(stringResource(R.string.reinstall_patch)) }
                }
                if (ReadinessRules.canRemovePatch(status)) {
                    TextButton(
                        onClick = { viewModel.requestConfirmation(RegionalPatchAction.REMOVE) },
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.remove_patch))
                    }
                }
            }

            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(stringResource(if (expanded) R.string.patch_card_hide_details else R.string.patch_card_details))
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.regional_patch_description), style = MaterialTheme.typography.bodySmall)
                    status?.let { patch ->
                        if (patch.device.isNotBlank()) DetailLine(stringResource(R.string.patch_device), patch.device)
                        if (patch.message.isNotBlank()) {
                            Text(patch.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (patch.sourceSha256.isNotBlank()) {
                            DetailLine(stringResource(R.string.stock_database_hash), patch.sourceSha256.take(16) + "…")
                        }
                        if (patch.patchedSha256.isNotBlank()) {
                            DetailLine(stringResource(R.string.patched_database_hash), patch.patchedSha256.take(16) + "…")
                        }
                    }
                    Text(
                        stringResource(R.string.regional_patch_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = inst.fair,
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchConfirmDialog(action: RegionalPatchAction, viewModel: RegionalPatchViewModel) {
    val install = action == RegionalPatchAction.INSTALL
    AlertDialog(
        onDismissRequest = viewModel::cancelConfirmation,
        icon = { Icon(Icons.Filled.Memory, contentDescription = null) },
        title = {
            Text(stringResource(if (install) R.string.regional_patch_confirm_title else R.string.regional_patch_remove_confirm_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        if (install) R.string.regional_patch_confirm_message else R.string.regional_patch_remove_confirm_message,
                    ),
                )
                if (install) {
                    Text(
                        stringResource(R.string.regional_patch_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::confirm) {
                Text(stringResource(if (install) R.string.install_patch else R.string.schedule_removal))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = viewModel::cancelConfirmation) { Text(stringResource(R.string.dismiss)) }
        },
    )
}

@Composable
private fun StatusGlyph(ok: Boolean, tone: Color) {
    Box(
        Modifier.size(24.dp).background(tone.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (ok) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun Prerequisite(label: String, ok: Boolean) {
    val inst = LocalInstrument.current
    val tone = if (ok) inst.good else inst.poor
    Row(
        Modifier
            .background(tone.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ok) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = tone, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tone)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = ReadoutSmall)
    }
}
