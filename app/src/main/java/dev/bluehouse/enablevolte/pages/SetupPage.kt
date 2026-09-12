package dev.bluehouse.enablevolte.pages

import android.app.Application
import android.telephony.SubscriptionInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.CheckStatus
import dev.bluehouse.enablevolte.FixAction
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.ReadinessCheck
import dev.bluehouse.enablevolte.SetupState
import dev.bluehouse.enablevolte.SetupViewModel
import dev.bluehouse.enablevolte.components.Panel
import dev.bluehouse.enablevolte.components.PanelGroup
import dev.bluehouse.enablevolte.components.RowDivider
import dev.bluehouse.enablevolte.components.SignalChip
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import dev.bluehouse.enablevolte.ui.theme.ReadoutHuge
import dev.bluehouse.enablevolte.uniqueName

/**
 * The screen the app opens on.
 *
 * Earlier versions opened on a settings list, which asked the user to already
 * know which of twenty switches was the one holding 5G back. This asks the
 * question they actually have — is it working, and if not, why — and puts the
 * fix for each answer next to it.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun SetupPage(
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

    val viewModel: SetupViewModel =
        viewModel(
            key = "setup-${subscription.subscriptionId}",
            factory = SetupViewModel.factory(context.applicationContext as Application, subscription.subscriptionId),
        )
    val ui by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

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

        VerdictPanel(ui, subscription.uniqueName, viewModel::refresh)

        ui.note?.let { note ->
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissNote) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }

        if (ui.loading) {
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.setup_checking), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            PanelGroup {
                ui.checks.forEachIndexed { index, check ->
                    if (index > 0) RowDivider()
                    CheckRow(
                        check = check,
                        busy = ui.busyFix == check.fix && check.fix != FixAction.NONE,
                        onFix = {
                            when (check.fix) {
                                FixAction.OPEN_BANDS -> navController.navigate("bands/${subscription.subscriptionId}")
                                FixAction.OPEN_EXPERT -> navController.navigate("config/${subscription.subscriptionId}")
                                else -> viewModel.applyFix(check.fix)
                            }
                        },
                    )
                }
            }
        }

        PanelGroup {
            dev.bluehouse.enablevolte.components.PremiumActionRow(
                title = stringResource(R.string.setup_expert),
                subtitle = stringResource(R.string.setup_expert_summary),
                icon = Icons.Filled.PriorityHigh,
                onClick = { navController.navigate("config/${subscription.subscriptionId}") },
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

/**
 * The answer, before any of the detail.
 *
 * A ring rather than a list, because the first thing wanted here is a verdict,
 * and a verdict is one shape.
 */
@Composable
private fun VerdictPanel(
    ui: SetupState,
    simName: String,
    onRefresh: () -> Unit,
) {
    val inst = LocalInstrument.current
    val progress by animateFloatAsState(
        targetValue = if (ui.total == 0) 0f else ui.passed / ui.total.toFloat(),
        animationSpec = tween(700),
        label = "verdict",
    )
    val tone = when {
        ui.loading -> MaterialTheme.colorScheme.onSurfaceVariant
        ui.ready -> inst.good
        ui.firstProblem != null -> inst.poor
        else -> inst.fair
    }

    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(74.dp)) {
                        val w = 7.dp.toPx()
                        drawArc(
                            color = inst.edge,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                        if (progress > 0f) {
                            drawArc(
                                color = tone,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                            )
                        }
                    }
                    Text(
                        if (ui.loading) "--" else "${ui.passed}/${ui.total}",
                        style = MaterialTheme.typography.titleMedium,
                        color = tone,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            ui.loading -> stringResource(R.string.setup_checking)
                            ui.ready -> stringResource(R.string.setup_ready)
                            else -> stringResource(R.string.setup_not_ready)
                        },
                        style = ReadoutHuge.copy(fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        ui.carrier.ifBlank { simName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ui.firstProblem?.let { problem ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.setup_blocking, problem.title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        problem.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.setup_recheck)) }
                if (ui.ready) {
                    SignalChip(label = stringResource(R.string.setup_all_clear), tone = inst.good, dot = true)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    check: ReadinessCheck,
    busy: Boolean,
    onFix: () -> Unit,
) {
    val inst = LocalInstrument.current
    val tone = when (check.status) {
        CheckStatus.PASS -> inst.good
        CheckStatus.FAIL -> inst.poor
        CheckStatus.WARN -> inst.fair
        CheckStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val glyph = when (check.status) {
        CheckStatus.PASS -> Icons.Filled.Check
        CheckStatus.FAIL -> Icons.Filled.Close
        CheckStatus.WARN -> Icons.Filled.PriorityHigh
        CheckStatus.UNKNOWN -> Icons.Filled.QuestionMark
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(22.dp).background(tone.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(glyph, contentDescription = null, tint = tone, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, style = MaterialTheme.typography.titleMedium)
            Text(
                check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (check.fixLabel != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onFix,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(check.fixLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
