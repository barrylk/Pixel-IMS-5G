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
import androidx.compose.ui.unit.dp

/** Panels are rectangular with a small radius; see InstrumentShapes. */
val PanelShape
    @Composable get() = MaterialTheme.shapes.medium

/**
 * The page ground.
 *
 * A flat, single colour. The previous backdrop layered a vertical gradient and
 * two large radial colour blobs behind every screen, which tinted the
 * measurements sitting on top of it and made panels hard to tell apart from
 * their background.
 */
@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}

/**
 * A grouped panel.
 *
 * Solid fill and a hairline outline, no translucency and no gradient, so a
 * panel is legible against the page at any scroll position and its contents
 * keep their intended colour.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = colors.surfaceContainer,
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = colors.surfaceContainer,
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}

/**
 * A run of related rows inside one panel, separated by hairlines.
 *
 * Grouping is what removes the clutter: a page of twenty individually floating
 * cards reads as twenty unrelated things, where four panels of five rows reads
 * as four decisions.
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
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
