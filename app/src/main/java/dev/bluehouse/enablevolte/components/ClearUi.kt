package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument

/*
 * Clear Glass building blocks. Content sits on calm grouped cards; glass is kept for
 * what floats. Every screen uses the same few pieces so the app reads as one system:
 * a large page title, group labels, grouped cards of rows, one status pill per row,
 * tinted notes for warnings, and pill-shaped buttons.
 */

/** The large title at the top of a tab page, with optional controls on the right. */
@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.7).sp,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** The small heading above a grouped card. */
@Composable
fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier = modifier.padding(start = 6.dp, top = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A calm card holding a run of rows. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().calmSurface(RoundedCornerShape(22.dp)).padding(vertical = 2.dp), content = content)
}

/** A calm card with inner padding for free-form content. */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().calmSurface(RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** The hairline between rows of a [GroupCard]. */
@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 1.dp,
        color = LocalInstrument.current.hairline,
    )
}

/**
 * One row in a grouped card: an optional leading dot, a title with an optional detail
 * line, and whatever sits on the right (a pill, a switch, a value or a chevron).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            below?.invoke(this)
        }
        trailing()
    }
}

/** The arrow at the end of a row that opens something. */
@Composable
fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

/** A plain value on the right of a row. */
@Composable
fun RowValue(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** One state word in a soft pill of its tone. [live] adds a dot. */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val color = statusToneColor(tone)
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (LocalInstrument.current.isDark) 0.18f else 0.13f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (live) Spacer(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** A round status mark at the start of a row: ✓ confirmed, ! review, ? unknown. */
@Composable
fun StatusDot(
    tone: StatusTone,
    symbol: String,
) {
    val color = statusToneColor(tone)
    Box(
        Modifier.size(26.dp).background(color.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** The one-answer card that opens a page: eyebrow, headline, explanation. */
@Composable
fun HeroCard(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    body: String? = null,
    footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().calmSurface(RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        if (!body.isNullOrBlank()) {
            Text(body, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
        if (!footnote.isNullOrBlank()) {
            Text(footnote, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A tinted note for warnings and hints, in place of loose red or grey text. */
@Composable
fun NoteBox(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val color = statusToneColor(tone)
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (LocalInstrument.current.isDark) 0.14f else 0.10f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    )
}

/** A switch in the concept's colours: green when on. */
@Composable
fun ClearSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
) {
    val inst = LocalInstrument.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = inst.good,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = if (inst.isDark) Color(0xFF39404C) else Color(0xFFD5DAE2),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** The main action on a card. */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A secondary action: soft accent fill, or soft red for something destructive. */
@Composable
fun TonalPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    small: Boolean = false,
) {
    val color = if (danger) LocalInstrument.current.poor else MaterialTheme.colorScheme.primary
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(alpha = 0.13f), contentColor = color),
        contentPadding = if (small) PaddingValues(horizontal = 13.dp, vertical = 6.dp) else PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = if (small) 12.sp else 14.sp, maxLines = 1)
    }
}

/** A thin horizontal meter, used for strength and clarity. */
@Composable
fun MeterBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(LocalInstrument.current.hairline)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** A large measured number with its unit, in the monospaced reading style. */
@Composable
fun BigReading(
    value: String,
    unit: String,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        Text(unit, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
}

/** One labelled value in a small grid of readings. */
@Composable
fun ReadingCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        if (!note.isNullOrBlank()) Text(note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The glass SIM switcher at the top of a page. */
@Composable
fun SimPicker(
    items: List<Pair<Int, String>>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (items.isEmpty()) return
    Row(
        modifier.fillMaxWidth().floatingGlass(CircleShape).horizontalScroll(rememberScrollState()).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { (id, label) ->
            val active = id == selectedId
            Box(
                Modifier
                    .then(if (active) Modifier.calmSurface(CircleShape) else Modifier.clip(CircleShape))
                    .selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = { onSelect(id) })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
