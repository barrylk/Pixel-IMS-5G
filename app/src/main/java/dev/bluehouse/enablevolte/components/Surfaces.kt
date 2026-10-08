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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The blur source the glass surfaces sample.
 *
 * Every pane in the app blurs the same thing — the lit ground drawn by
 * [AppBackdrop] — so there is one [HazeState] for the whole screen, handed down
 * here. It is nullable so a [Panel] rendered outside the backdrop (a @Preview,
 * a unit of UI lifted into isolation) still draws; it simply falls back to its
 * tint with no blur behind it.
 */
val LocalGlassHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * The page ground, lit from above — and the one surface everything else blurs.
 *
 * The gradient is not decoration. Panels are translucent glass, so whatever is
 * behind them decides whether they read as objects or as nothing at all. The
 * ground is marked as the haze source and drawn first; the app, with its glass
 * panels and floating navigation bar, is drawn over it and samples it.
 */
@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val inst = LocalInstrument.current
    val hazeState = rememberHazeState()
    Box(modifier = Modifier.fillMaxSize()) {
        val scope = this
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colors.background)
                .background(
                    Brush.verticalGradient(
                        0.0f to inst.glow.copy(alpha = if (inst.isDark) 0.20f else 0.10f),
                        0.28f to inst.glow.copy(alpha = if (inst.isDark) 0.05f else 0.025f),
                        0.60f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = if (inst.isDark) 0.30f else 0f),
                    ),
                )
                .hazeSource(state = hazeState),
        )
        CompositionLocalProvider(
            LocalContentColor provides colors.onBackground,
            LocalGlassHaze provides hazeState,
        ) {
            scope.content()
        }
    }
}

/**
 * The glass recipe, shared by every pane.
 *
 * A thin frost tint over the real blur, plus a touch of grain so a flat
 * gradient does not read as plastic. When no hardware blur is available the
 * [HazeStyle.fallbackTint] paints an opaque container colour instead, so a pane
 * stays legible rather than dissolving into the ground.
 */
@Composable
fun glassHazeStyle(blurRadius: Dp = 24.dp): HazeStyle {
    val inst = LocalInstrument.current
    val colors = MaterialTheme.colorScheme
    return HazeStyle(
        backgroundColor = colors.surface,
        tints = listOf(HazeTint(if (inst.isDark) inst.frostHigh else inst.frost)),
        blurRadius = blurRadius,
        noiseFactor = if (inst.isDark) 0.04f else 0.02f,
        fallbackTint = HazeTint(colors.surfaceContainerHigh),
    )
}

/**
 * A frosted glass panel.
 *
 * The blur does the heavy lifting now: the pane samples the lit ground, blurs
 * it, and lays a thin frost film and a one-pixel top sheen over it, with a
 * hairline edge where a real pane would meet the light.
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
    val haze = LocalGlassHaze.current
    val style = glassHazeStyle()
    val fill = Brush.verticalGradient(listOf(inst.frostHigh, inst.frost))
    val border = BorderStroke(1.dp, inst.edge)

    @Composable
    fun Body() {
        Box(
            Modifier
                .clip(shape)
                .then(if (haze != null) Modifier.hazeEffect(state = haze, style = style) else Modifier)
                .background(fill),
        ) {
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
