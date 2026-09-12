package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument

/**
 * The page ground, lit from above.
 *
 * The gradient is not decoration. Panels are translucent, so whatever is behind
 * them decides whether they read as objects or as nothing at all — a flat
 * near-black ground is what made the first attempt at this look like outlines
 * on a void.
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
                    0.0f to inst.glow.copy(alpha = if (inst.isDark) 0.20f else 0.10f),
                    0.28f to inst.glow.copy(alpha = if (inst.isDark) 0.05f else 0.025f),
                    0.60f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = if (inst.isDark) 0.30f else 0f),
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
 * Three things make this read as glass without a backdrop blur, which Compose
 * cannot do: a fill bright enough to separate from the ground on its own, a
 * gradient running top-to-bottom so the surface has a direction, and a one-pixel
 * sheen along the top edge where a real pane would catch the light.
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
    val fill = Brush.verticalGradient(listOf(inst.frostHigh, inst.frost))
    val border = BorderStroke(1.dp, inst.edge)

    @Composable
    fun Body() {
        Box(Modifier.background(fill, shape).clip(shape)) {
            content()
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, inst.sheen, Color.Transparent),
                        ),
                    ),
            )
        }
    }

    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.onSurface,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = if (inst.isDark) 0.dp else 2.dp,
        ) { Body() }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.onSurface,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = if (inst.isDark) 0.dp else 2.dp,
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
