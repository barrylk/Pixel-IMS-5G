package dev.bluehouse.enablevolte.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import dev.bluehouse.enablevolte.ui.theme.ReadoutHuge
import dev.bluehouse.enablevolte.ui.theme.ReadoutMedium
import dev.bluehouse.enablevolte.ui.theme.ReadoutSmall
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How good a reading is, on the one ramp the whole app shares. */
enum class Quality { GOOD, FAIR, POOR, UNKNOWN }

/**
 * Buckets RSRP the way a field engineer reads it.
 *
 * Better than -90 dBm is a cell you can rely on; past -105 dBm you are living
 * on the edge of the cell.
 */
fun qualityOfRsrp(dbm: Int?): Quality = when {
    dbm == null -> Quality.UNKNOWN
    dbm >= -90 -> Quality.GOOD
    dbm >= -105 -> Quality.FAIR
    else -> Quality.POOR
}

@Composable
fun qualityColor(q: Quality): Color {
    val inst = LocalInstrument.current
    return when (q) {
        Quality.GOOD -> inst.good
        Quality.FAIR -> inst.fair
        Quality.POOR -> inst.poor
        Quality.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Places a reading on 0..1 across the window that actually occurs in the field. */
private fun rsrpFraction(dbm: Int?): Float =
    if (dbm == null) 0f else ((dbm + 125f) / 45f).coerceIn(0f, 1f)

/**
 * The headline reading, drawn as a swept arc.
 *
 * The sweep is animated because the value moves continuously and a number that
 * jumps gives no sense of which way it is heading; the arc carries the
 * direction, the digits carry the value.
 */
@Composable
fun SignalGauge(
    dbm: Int?,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val inst = LocalInstrument.current
    val quality = qualityOfRsrp(dbm)
    val target = rsrpFraction(dbm)
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gauge sweep",
    )
    val shown by animateFloatAsState(
        targetValue = (dbm ?: 0).toFloat(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gauge value",
    )
    val arcColor by animateColorAsState(qualityColor(quality), tween(600), label = "gauge tone")
    val trackColor = inst.edge

    Box(modifier = modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(196.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke * 1.9f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val startAngle = 135f
            val fullSweep = 270f

            // Tick ring, outside the arc.
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rOuter = size.width / 2f
            val rInner = rOuter - stroke * 0.62f
            repeat(41) { i ->
                val a = Math.toRadians((startAngle + fullSweep * (i / 40f)).toDouble())
                drawLine(
                    color = trackColor,
                    start = Offset(cx + cos(a).toFloat() * rInner, cy + sin(a).toFloat() * rInner),
                    end = Offset(cx + cos(a).toFloat() * rOuter, cy + sin(a).toFloat() * rOuter),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = fullSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(arcColor.copy(alpha = 0.65f), arcColor, arcColor)),
                    startAngle = startAngle,
                    sweepAngle = fullSweep * sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (dbm == null) "--" else shown.roundToInt().toString(),
                    style = ReadoutHuge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " dBm",
                    style = ReadoutSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(caption, style = ReadoutSmall, color = arcColor)
            }
        }
    }
}

/**
 * A rolling plot of one metric.
 *
 * A dropout has a shape, and the shape is the diagnosis — a cliff is a
 * handover, a slow slide is you walking away from the cell. A single current
 * value cannot tell you which happened.
 */
@Composable
fun Sparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    min: Int = -120,
    max: Int = -60,
) {
    val inst = LocalInstrument.current
    val line = inst.glow
    Canvas(modifier = modifier.fillMaxWidth().height(92.dp)) {
        val grid = inst.edge
        repeat(3) { i ->
            val y = size.height * (i + 1) / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas

        val span = (max - min).toFloat().coerceAtLeast(1f)
        val dx = size.width / (values.size - 1).toFloat()
        fun yOf(v: Int): Float {
            val f = ((v - min) / span).coerceIn(0f, 1f)
            return size.height - f * size.height
        }

        val path = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = i * dx
            val y = yOf(v)
            if (i == 0) {
                path.moveTo(x, y)
                area.moveTo(x, size.height)
                area.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo((values.size - 1) * dx, size.height)
        area.close()

        drawPath(
            path = area,
            brush = Brush.verticalGradient(listOf(line.copy(alpha = 0.30f), Color.Transparent)),
        )
        drawPath(path = path, color = line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color = line, radius = 3.5.dp.toPx(), center = Offset((values.size - 1) * dx, yOf(values.last())))
    }
}

/** One labelled reading in the parameter grid. */
@Composable
fun SpecTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    val inst = LocalInstrument.current
    Column(
        modifier = modifier
            .background(inst.frost, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            value,
            style = ReadoutMedium,
            color = tone ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A short state marker: a dot and a word in the tone's colour. */
@Composable
fun SignalChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    dot: Boolean = false,
) {
    val color = tone ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = color.copy(alpha = 0.11f),
        contentColor = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dot) {
                Spacer(Modifier.size(6.dp).background(color, CircleShape))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
        }
    }
}

/** Four bars, the way a phone shows strength — but driven by the real reading. */
@Composable
fun SignalBars(
    dbm: Int?,
    modifier: Modifier = Modifier,
) {
    val inst = LocalInstrument.current
    val color = qualityColor(qualityOfRsrp(dbm))
    val lit = (rsrpFraction(dbm) * 4).roundToInt().coerceIn(0, 4)
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(6, 9, 12, 16).forEachIndexed { i, h ->
            Spacer(
                Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .background(
                        if (i < lit) color else inst.edge,
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}
