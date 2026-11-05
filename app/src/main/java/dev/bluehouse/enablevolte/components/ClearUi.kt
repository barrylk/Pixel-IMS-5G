package dev.bluehouse.enablevolte.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import dev.bluehouse.enablevolte.ui.theme.Mono

/*
 * Clear Glass building blocks, sized to the approved concept: content sits on calm
 * grouped cards, glass is kept for what floats, and every screen uses the same few
 * pieces so the app reads as one system.
 */

/** App-level actions that live on Home in the concept: restore, appearance, About. */
data class AppActions(
    val restoreAvailable: Boolean = false,
    val onRestore: () -> Unit = {},
    val appearanceLabel: String = "",
    val onCycleAppearance: () -> Unit = {},
    val onAbout: () -> Unit = {},
    val onReload: () -> Unit = {},
)

val LocalAppActions = compositionLocalOf { AppActions() }

/** The large title at the top of a tab page, with round glass buttons on the right. */
@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            fontSize = 30.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.75).sp,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** A 40 dp round glass button for the page title row. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Box(
        Modifier
            .size(40.dp)
            .floatingGlass(CircleShape)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A glass capsule holding a short state, like "Live". */
@Composable
fun GlassPill(
    text: String,
    dot: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.floatingGlass(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = dot)
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
        modifier = modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
        fontSize = 13.sp,
        lineHeight = 17.sp,
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
    HorizontalDivider(thickness = 1.dp, color = LocalInstrument.current.hairline)
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.5.sp, lineHeight = 15.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            below?.invoke(this)
        }
        trailing()
    }
}

/** The "›" at the end of a row that opens something. */
@Composable
fun Chevron() {
    Text("›", fontSize = 20.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A plain value on the right of a row. */
@Composable
fun RowValue(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
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
    val alpha = when (tone) {
        StatusTone.WARNING -> 0.16f
        StatusTone.ACCENT -> 0.12f
        StatusTone.NEUTRAL -> 0.10f
        else -> 0.14f
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(if (tone == StatusTone.NEUTRAL) LocalInstrument.current.hairline else color.copy(alpha = alpha))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (live) {
            Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).background(color.copy(alpha = 0.25f), CircleShape))
                Box(Modifier.size(6.dp).background(color, CircleShape))
            }
        }
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
        Modifier.size(26.dp).background(color.copy(alpha = if (tone == StatusTone.WARNING) 0.16f else 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = color, fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** A round tinted icon at the start of a row. */
@Composable
fun IconDot(
    icon: ImageVector,
    tone: StatusTone,
) {
    val color = statusToneColor(tone)
    Box(Modifier.size(26.dp).background(color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
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
        modifier.fillMaxWidth().calmSurface(RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(title, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.48).sp)
        if (!body.isNullOrBlank()) {
            Text(body, fontSize = 12.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
        if (!footnote.isNullOrBlank()) {
            Text(footnote, fontSize = 10.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A tinted note for warnings and hints. */
@Composable
fun NoteBox(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val color = if (tone == StatusTone.NEUTRAL) MaterialTheme.colorScheme.onSurfaceVariant else statusToneColor(tone)
    Text(
        text,
        color = color,
        fontSize = 11.5.sp,
        lineHeight = 16.5.sp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (tone == StatusTone.NEUTRAL) LocalInstrument.current.hairline else color.copy(alpha = if (tone == StatusTone.WARNING) 0.12f else 0.10f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    )
}

/** The concept's switch: a 44 × 26 track, green when on, with a 20 dp white thumb. */
@Composable
fun ClearSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
) {
    val inst = LocalInstrument.current
    val track by animateColorAsState(
        if (checked) inst.good else if (inst.isDark) Color(0xFF39404C) else Color(0xFFD5DAE2),
        label = "switch track",
    )
    val thumb by animateDpAsState(if (checked) 21.dp else 3.dp, label = "switch thumb")
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(track)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .offset(x = thumb, y = 3.dp)
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** The main action: solid accent pill with a soft glow. */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (enabled) Modifier.shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(CircleShape)
            .background(accent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 13.dp else 16.dp, vertical = if (small) 8.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = color,
            fontSize = if (small) 12.sp else 13.5.sp,
            lineHeight = if (small) 16.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A text-only action, such as "What does this mean?". */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

/** A thin horizontal meter, used for strength, clarity and carrier width. */
@Composable
fun MeterBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    thickness: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Box(modifier.fillMaxWidth().height(thickness).clip(CircleShape).background(LocalInstrument.current.hairline)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** A large measured number with its unit, in the monospaced reading style. */
@Composable
fun BigReading(
    value: String,
    unit: String,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontFamily = Mono, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
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
        Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, fontFamily = Mono)
        if (!note.isNullOrBlank()) Text(note, fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The glass SIM switcher: equal segments, the chosen one a solid white pill. */
@Composable
fun SimPicker(
    items: List<Pair<Int, String>>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (items.isEmpty()) return
    val inst = LocalInstrument.current
    val shadowColor = if (inst.isDark) Color(0x80000000) else Color(0x3D1E2D50)
    Row(
        modifier.fillMaxWidth().floatingGlass(CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { (id, label) ->
            val active = id == selectedId
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (active) {
                            Modifier.shadow(3.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor).clip(CircleShape).background(inst.card)
                        } else {
                            Modifier.clip(CircleShape)
                        },
                    )
                    .selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = { onSelect(id) })
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Equal segments for a short choice inside a card, such as the 5G architecture. */
@Composable
fun Segments(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val inst = LocalInstrument.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (active) Modifier.background(inst.card) else Modifier)
                    .selectable(selected = active, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(vertical = 7.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    fontSize = 11.5.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Space between the last item on a page and the floating bar. */
@Composable
fun PageEnd() {
    Spacer(Modifier.height(4.dp))
    BottomBarSpacer()
}
