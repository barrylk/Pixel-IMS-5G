package dev.bluehouse.enablevolte.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The iOS tap response: a grey highlight that appears at once on touch-down and fades out
 * over a quarter second on release, in place of Android's ripple. Rows use it as is;
 * buttons add their own dim-and-shrink on top.
 */
data class IosHighlight(val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = HighlightNode(interactionSource, color)
}

private class HighlightNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private val level = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> level.snapTo(1f)
                    is PressInteraction.Release, is PressInteraction.Cancel -> level.animateTo(0f, tween(260))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (level.value > 0f) drawRect(color.copy(alpha = color.alpha * level.value))
    }
}

/**
 * Apple's continuous ("squircle") corner, the curvature iOS uses for cards, icons and
 * buttons: the curve starts earlier than a circular arc and blends into the straight
 * edge. Control points are the published UIBezierPath continuous-corner coefficients.
 */
class ContinuousRoundedShape(private val radius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val r = minOf(with(density) { radius.toPx() }, minOf(w, h) / 2f / 1.52866483f)
        val path = Path()

        // One corner, in units of r measured inwards from the corner along each edge.
        fun corner(map: (Float, Float) -> Offset) {
            fun p(
                u: Float,
                v: Float,
            ) = map(u * r, v * r)
            p(1.52866471f, 0f).let { path.lineTo(it.x, it.y) }
            curve(path, p(1.08849323f, 0f), p(0.86840689f, 0f), p(0.66993427f, 0.06549600f))
            p(0.63149399f, 0.07491100f).let { path.lineTo(it.x, it.y) }
            curve(path, p(0.37282392f, 0.16905899f), p(0.16906013f, 0.37282401f), p(0.07491176f, 0.63149399f))
            p(0.06549600f, 0.66993427f).let { path.lineTo(it.x, it.y) }
            curve(path, p(0f, 0.86840701f), p(0f, 1.08849299f), p(0f, 1.52866483f))
        }
        path.moveTo(1.52866483f * r, 0f)
        corner { u, v -> Offset(w - u, v) } // top right
        corner { u, v -> Offset(w - v, h - u) } // bottom right
        corner { u, v -> Offset(u, h - v) } // bottom left
        corner { u, v -> Offset(v, u) } // top left
        path.close()
        return Outline.Generic(path)
    }

    private fun curve(
        path: Path,
        c1: Offset,
        c2: Offset,
        end: Offset,
    ) = path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)

    override fun equals(other: Any?) = other is ContinuousRoundedShape && other.radius == radius

    override fun hashCode() = radius.hashCode()
}
