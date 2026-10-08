package dev.bluehouse.enablevolte.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.InstrumentColors
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import kotlin.math.max

/** How much of the ground a pane lets through. */
enum class GlassDepth {
    /** Content panels: the most translucent. */
    REGULAR,

    /** Bars and controls that sit over scrolling content and must stay legible. */
    THICK,

    /** A tinted pane, used for the one call to action on a screen. */
    TINTED,
}

/**
 * A pane of liquid glass.
 *
 * Five layers, drawn in the order light meets them: a translucent body running
 * slightly brighter at the top, a specular bloom at the top-leading corner
 * where light enters, a faint shade at the bottom where it leaves, the content,
 * and a rim that is bright at the top, nearly vanishes along the sides and
 * picks up a coloured caustic at the lower-trailing corner. No elevation
 * shadow: on a translucent surface it shows through the pane as a grey smudge.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = MaterialTheme.shapes.large,
    depth: GlassDepth = GlassDepth.REGULAR,
    tint: Color = Color.Unspecified,
): Modifier {
    val inst = LocalInstrument.current
    return this
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val body = glassBody(inst, depth, tint)
            val bloom = Brush.radialGradient(
                colors = listOf(inst.specular, Color.Transparent),
                center = Offset(size.width * 0.12f, -size.height * 0.15f),
                radius = max(size.width, size.height) * 0.9f,
            )
            val shade = Brush.verticalGradient(
                0.55f to Color.Transparent,
                1f to inst.shade,
            )
            val rim = Brush.linearGradient(
                0f to inst.edgeBright,
                0.28f to inst.edge,
                0.72f to inst.edge.copy(alpha = inst.edge.alpha * 0.6f),
                1f to if (tint.isSpecified) tint.copy(alpha = 0.55f) else inst.caustic,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
            val sheen = Brush.horizontalGradient(
                0.08f to Color.Transparent,
                0.35f to inst.sheen,
                0.75f to inst.sheen.copy(alpha = inst.sheen.alpha * 0.3f),
                0.95f to Color.Transparent,
            )
            // The stroke is centred on the clipped edge, so half of it shows.
            val rimWidth = 1.2.dp.toPx() * 2f
            val sheenInset = 1.dp.toPx()
            onDrawWithContent {
                drawOutline(outline, body)
                drawOutline(outline, bloom)
                drawOutline(outline, shade)
                drawContent()
                drawOutline(outline, rim, style = Stroke(rimWidth))
                drawLine(
                    brush = sheen,
                    start = Offset(0f, sheenInset),
                    end = Offset(size.width, sheenInset),
                    strokeWidth = sheenInset,
                )
            }
        }
}

private fun glassBody(inst: InstrumentColors, depth: GlassDepth, tint: Color): Brush {
    val top: Color
    val bottom: Color
    when (depth) {
        GlassDepth.REGULAR -> {
            top = inst.frostHigh
            bottom = inst.frost
        }
        GlassDepth.THICK -> {
            top = inst.scrim
            bottom = inst.scrim.copy(alpha = (inst.scrim.alpha - 0.08f).coerceAtLeast(0f))
        }
        GlassDepth.TINTED -> {
            val base = if (tint.isSpecified) tint else inst.glow
            top = base.copy(alpha = if (inst.isDark) 0.30f else 0.20f)
            bottom = base.copy(alpha = if (inst.isDark) 0.12f else 0.08f)
        }
    }
    return Brush.verticalGradient(listOf(top, bottom))
}
