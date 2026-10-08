package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument

/**
 * The page ground, lit by three soft pools of light.
 *
 * The pools are not decoration. Panels are translucent, so whatever is behind
 * them decides whether they read as glass or as nothing at all — a flat ground
 * is what made earlier attempts look like outlines on a void. They are large
 * and smooth because Compose has no backdrop blur: a blurred smooth gradient
 * looks like the gradient, so the panes read as frosted without sampling.
 */
@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val inst = LocalInstrument.current
    val strength = if (inst.isDark) 0.34f else 0.55f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val reach = maxOf(w, h)
                val pools = listOf(
                    Triple(inst.pools[0], Offset(w * 0.05f, h * 0.06f), reach * 0.62f),
                    Triple(inst.pools[1], Offset(w * 1.02f, h * 0.38f), reach * 0.55f),
                    Triple(inst.pools[2], Offset(w * 0.20f, h * 0.92f), reach * 0.50f),
                ).map { (color, center, radius) ->
                    Brush.radialGradient(
                        0f to color.copy(alpha = strength),
                        0.45f to color.copy(alpha = strength * 0.35f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    )
                }
                val vignette = Brush.verticalGradient(
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (inst.isDark) 0.35f else 0f),
                )
                onDrawBehind {
                    pools.forEach { drawRect(it) }
                    drawRect(vignette)
                }
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}

/**
 * A pane of liquid glass holding one block of content.
 *
 * See [liquidGlass] for how the pane is drawn.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    depth: GlassDepth = GlassDepth.REGULAR,
    tint: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large

    @Composable
    fun Body() {
        Box(Modifier.liquidGlass(shape, depth, tint)) { content() }
    }

    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
        ) { Body() }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
        ) { Body() }
    }
}

/** A run of related rows in one panel. */
@Composable
fun PanelGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Panel(modifier = modifier.fillMaxWidth()) {
        Column { content() }
    }
}

/** The hairline between two rows of a [PanelGroup]. */
@Composable
fun RowDivider(inset: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (inset) 16.dp else 0.dp),
        thickness = 1.dp,
        color = LocalInstrument.current.edge,
    )
}
