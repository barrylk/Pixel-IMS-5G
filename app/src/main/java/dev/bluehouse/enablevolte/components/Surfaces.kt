package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument

/**
 * The page ground, lit from above.
 *
 * Panels in 2.0 are translucent, and translucency is only legible over
 * something. A flat fill made the original glass read as haze; a ground with a
 * light source in it gives every panel an edge to catch.
 */
@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val inst = LocalInstrument.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .background(
                Brush.verticalGradient(
                    0.0f to inst.glow.copy(alpha = if (inst.isDark) 0.13f else 0.07f),
                    0.32f to colors.background.copy(alpha = 0.0f),
                    1.0f to Color.Transparent,
                ),
            )
            .background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1.0f to inst.ember.copy(alpha = if (inst.isDark) 0.05f else 0.03f),
                ),
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}

/**
 * A frosted panel.
 *
 * The fill is a diagonal gradient rather than a flat colour so the surface has
 * a direction to it, and the hairline is brighter than the fill so the edge
 * reads before the body does — that edge is what separates glass from fog.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val inst = LocalInstrument.current
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val fill = Brush.linearGradient(listOf(inst.frostHigh, inst.frost, inst.frost.copy(alpha = inst.frost.alpha * 0.55f)))
    val border = BorderStroke(1.dp, inst.edge)

    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.onSurface,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(Modifier.background(fill, shape)) { content() }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.onSurface,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(Modifier.background(fill, shape)) { content() }
        }
    }
}

/**
 * A run of related rows in one panel.
 *
 * Grouping is what keeps a settings page from reading as twenty unrelated
 * floating things.
 */
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
