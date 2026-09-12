package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.NumericTextStyle
import dev.bluehouse.enablevolte.ui.theme.SignalAmber
import dev.bluehouse.enablevolte.ui.theme.SignalAmberDark
import dev.bluehouse.enablevolte.ui.theme.SignalGreen
import dev.bluehouse.enablevolte.ui.theme.SignalGreenDark
import dev.bluehouse.enablevolte.ui.theme.SignalRed
import dev.bluehouse.enablevolte.ui.theme.SignalRedDark

enum class StatusTone {
    ACCENT,
    SUCCESS,
    WARNING,
    DANGER,
    NEUTRAL,
}

/**
 * Signal colours are chosen per theme.
 *
 * A single green that reads well on near-black is washed out on white, and the
 * reverse, so each tone carries a pair and the theme picks.
 */
@Composable
fun statusToneColor(tone: StatusTone): Color {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        StatusTone.ACCENT -> MaterialTheme.colorScheme.primary
        StatusTone.SUCCESS -> if (dark) SignalGreen else SignalGreenDark
        StatusTone.WARNING -> if (dark) SignalAmber else SignalAmberDark
        StatusTone.DANGER -> if (dark) SignalRed else SignalRedDark
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * The heading block at the top of a page.
 *
 * The entrance animation is gone: it delayed the first reading by about
 * 400 ms every time a page opened, and a measurement tool should not make you
 * wait to be told what it measured.
 */
@Composable
fun PremiumPageIntro(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A small state marker: a dot and a word, in the tone's colour. */
@Composable
fun PremiumStatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val color = statusToneColor(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.10f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(Modifier.size(6.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * One measured value with its label.
 *
 * The value is monospaced so a column of readings stays aligned and the digits
 * do not shift sideways as a live measurement updates.
 */
@Composable
fun PremiumMetric(
    label: String,
    value: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = NumericTextStyle,
            color = statusToneColor(tone),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A navigable row.
 *
 * The icon is a plain tinted glyph rather than a filled circle on a tinted
 * card; at five or six of these on a page the old treatment was most of the
 * colour on screen.
 */
@Composable
fun PremiumActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.ACCENT,
) {
    val color = statusToneColor(tone)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun PremiumSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The container for a single property row.
 *
 * Transparent rather than a card. Every one of these used to draw its own
 * rounded translucent surface, so a settings page was twenty floating cards
 * with twenty shadows — which is most of what made the pages feel cluttered.
 * A row now takes the colour of whatever it sits in: grouped into a
 * [PanelGroup] it reads as one list, and directly on the page it reads as a
 * plain settings row.
 */
@Composable
fun CompactPropertySurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            contentColor = LocalContentColor.current,
            tonalElevation = 0.dp,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            contentColor = LocalContentColor.current,
            tonalElevation = 0.dp,
            content = content,
        )
    }
}
