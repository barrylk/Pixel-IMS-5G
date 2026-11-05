package dev.bluehouse.enablevolte.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.ui.theme.ContinuousRoundedShape
import dev.bluehouse.enablevolte.ui.theme.IosType
import dev.bluehouse.enablevolte.ui.theme.LocalInstrument
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/*
 * iOS-native building blocks. Inset grouped lists on the grouped background, Apple's
 * system colours, continuous corners, the iOS switch and segmented control, buttons that
 * dim and shrink when pressed, and a large title that collapses into a blurred bar.
 */

private val CardShape = ContinuousRoundedShape(26.dp)
private val TileShape = ContinuousRoundedShape(7.dp)
private val PressSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 650f)
private val SlideSpring = spring<Dp>(dampingRatio = 0.82f, stiffness = 520f)

/** Apple's system colours, picked for the current appearance. */
object IosColors {
    val blue @Composable get() = pick(0xFF007AFF, 0xFF0A84FF)
    val green @Composable get() = pick(0xFF34C759, 0xFF30D158)
    val orange @Composable get() = pick(0xFFFF9500, 0xFFFF9F0A)
    val red @Composable get() = pick(0xFFFF3B30, 0xFFFF453A)
    val indigo @Composable get() = pick(0xFF5856D6, 0xFF5E5CE6)
    val purple @Composable get() = pick(0xFFAF52DE, 0xFFBF5AF2)
    val teal @Composable get() = pick(0xFF30B0C7, 0xFF40C8E0)
    val pink @Composable get() = pick(0xFFFF2D55, 0xFFFF375F)
    val gray @Composable get() = pick(0xFF8E8E93, 0xFF8E8E93)
    val secondaryLabel @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiaryLabel @Composable get() = if (LocalInstrument.current.isDark) Color(0x4DEBEBF5) else Color(0x4D3C3C43)
    val fill @Composable get() = if (LocalInstrument.current.isDark) Color(0x3D767680) else Color(0x1F767680)

    @Composable
    private fun pick(
        light: Long,
        dark: Long,
    ) = Color(if (LocalInstrument.current.isDark) dark else light)
}

/** Shrinks slightly and dims while pressed, like a UIButton. */
@Composable
private fun Modifier.pressFeedback(
    source: MutableInteractionSource,
    pressedScale: Float = 0.96f,
    pressedAlpha: Float = 0.75f,
): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, PressSpring, label = "press scale")
    val alpha by animateFloatAsState(if (pressed) pressedAlpha else 1f, tween(if (pressed) 60 else 220), label = "press alpha")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/** App-level actions that live on Home: restore, appearance, About, reload. */
data class AppActions(
    val restoreAvailable: Boolean = false,
    val onRestore: () -> Unit = {},
    val appearanceLabel: String = "",
    val onCycleAppearance: () -> Unit = {},
    val onAbout: () -> Unit = {},
    val onReload: () -> Unit = {},
)

val LocalAppActions = staticCompositionLocalOf { AppActions() }

/**
 * The navigation bar of the current tab page. The page's large title reports where it is;
 * once it scrolls under the bar, the bar fades in its blurred material and the small
 * centred title. The page's buttons always sit in the bar, as on iOS.
 */
class NavBarState {
    internal var owner by mutableStateOf<Any?>(null)
    internal var title by mutableStateOf("")
    internal var trailing by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
    internal var titleBottom by mutableFloatStateOf(Float.MAX_VALUE)
}

val LocalNavBar = staticCompositionLocalOf { NavBarState() }

private val NavBarHeight = 44.dp

/** The large title of a tab page. Its [trailing] buttons are shown in the navigation bar. */
@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavBar.current
    val owner = remember { Any() }
    DisposableEffect(owner) {
        nav.owner = owner
        onDispose {
            if (nav.owner === owner) {
                nav.owner = null
                nav.trailing = null
                nav.titleBottom = Float.MAX_VALUE
            }
        }
    }
    SideEffect {
        if (nav.owner === owner) {
            nav.title = title
            nav.trailing = trailing
        }
    }
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(NavBarHeight))
        Text(
            title,
            style = IosType.LargeTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(start = 4.dp, bottom = 4.dp)
                .onGloballyPositioned { if (nav.owner === owner) nav.titleBottom = it.boundsInRoot().bottom },
        )
    }
}

/** The blurred bar over tab pages, drawn above the page by the app shell. */
@Composable
fun IosNavBar(
    state: NavBarState,
    haze: HazeState?,
) {
    if (state.owner == null) return
    val density = LocalDensity.current
    val inst = LocalInstrument.current
    val barBottom = WindowInsets.statusBars.getTop(density) + with(density) { NavBarHeight.toPx() }
    val fade = with(density) { 26.dp.toPx() }
    val progress = ((barBottom + fade - state.titleBottom) / fade).coerceIn(0f, 1f)
    val tint = if (inst.isDark) Color(0xBF161616) else Color(0xCCF9F9F9)
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = progress }
                .then(
                    if (haze != null) {
                        Modifier.hazeEffect(
                            state = haze,
                            style = HazeStyle(MaterialTheme.colorScheme.background, listOf(HazeTint(tint)), 30.dp, 0f),
                        ) { inputScale = HazeInputScale.Fixed(0.5f) }
                    } else {
                        Modifier.background(tint)
                    },
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .graphicsLayer { alpha = progress }
                .background(inst.hairline),
        )
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(NavBarHeight)) {
            Text(
                state.title,
                style = IosType.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = progress },
            )
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { state.trailing?.invoke(this) }
        }
    }
}

/** A round glass button for the navigation bar. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .pressFeedback(source, 0.88f, 0.85f)
            .floatingGlass(CircleShape)
            .clickable(source, null, enabled = enabled && !busy, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
        } else {
            Icon(icon, contentDescription, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A glass capsule with a live dot, such as "Live" on Network. */
@Composable
fun GlassPill(
    text: String,
    dot: Color,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressFeedback(source, 0.92f, 0.85f)
            .floatingGlass(CircleShape)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .height(40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PulseDot(dot)
        Text(text, style = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PulseDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val halo by transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "halo")
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(14.dp * halo).alpha(1f - halo).background(color, CircleShape))
        Box(Modifier.size(7.dp).background(color, CircleShape))
    }
}

/** An inset-grouped section header: footnote size, upper case, secondary label. */
@Composable
fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
        style = IosType.Footnote.copy(letterSpacing = 0.2.sp),
        color = IosColors.secondaryLabel,
    )
}

/** An inset-grouped section: a card of rows. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(LocalInstrument.current.card), content = content)
}

/** A card with padding for free-form content. */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(LocalInstrument.current.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** The hairline separator, inset to where the row's text starts. */
@Composable
fun GroupDivider(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(LocalInstrument.current.hairline))
}

/** Separator for rows that start with an icon tile. */
@Composable
fun IconRowDivider() = GroupDivider(inset = 59.dp)

/**
 * An iOS table row: optional leading icon, a body-size title with an optional secondary
 * line, and an accessory on the right. Tapping flashes the grey iOS highlight.
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
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = IosType.Body, color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor)
            // Short details read at subhead size; long explanations drop to footnote, as iOS does.
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = if (subtitle.length > 70) IosType.Footnote else IosType.Subhead, color = IosColors.secondaryLabel)
            below?.invoke(this)
        }
        trailing()
    }
}

/** The disclosure chevron (like SF "chevron.forward"), thin and tertiary grey. */
@Composable
fun Chevron() {
    val color = IosColors.tertiaryLabel
    Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
        val stroke = 2.2.dp.toPx()
        val path = Path().apply {
            moveTo(stroke / 2, stroke / 2)
            lineTo(size.width - stroke / 2, size.height / 2)
            lineTo(stroke / 2, size.height - stroke / 2)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A secondary value on the right of a row. */
@Composable
fun RowValue(text: String) {
    Text(text, style = IosType.Body, color = IosColors.secondaryLabel, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** A vivid status capsule in the tone's system colour. [live] adds a pulsing dot. */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val color = if (tone == StatusTone.NEUTRAL) IosColors.gray else statusToneColor(tone)
    val dark = LocalInstrument.current.isDark
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (dark) 0.26f else 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (live) PulseDot(color)
        Text(text, color = color, style = IosType.Footnote.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false)
    }
}

/** A filled status circle with a white mark, like SF "checkmark.circle.fill". */
@Composable
fun StatusDot(
    tone: StatusTone,
    symbol: String,
) {
    val color = if (tone == StatusTone.NEUTRAL) IosColors.gray else statusToneColor(tone)
    Box(Modifier.size(24.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(symbol, color = Color.White, style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold))
    }
}

/** A Settings-style icon: a white glyph on a solid colour tile with continuous corners. */
@Composable
fun IconTile(
    icon: ImageVector,
    color: Color,
) {
    Box(Modifier.size(30.dp).clip(TileShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(19.dp), tint = Color.White)
    }
}

/** [IconTile] in a status tone's colour. */
@Composable
fun IconDot(
    icon: ImageVector,
    tone: StatusTone,
) = IconTile(icon, if (tone == StatusTone.NEUTRAL) IosColors.gray else statusToneColor(tone))

/** A summary card: eyebrow, title, explanation and optional footnote. */
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
        modifier.fillMaxWidth().clip(CardShape).background(LocalInstrument.current.card).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = IosType.Footnote.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(title, style = IosType.Title2, color = MaterialTheme.colorScheme.onSurface)
        if (!body.isNullOrBlank()) Text(body, style = if (body.length > 140) IosType.Footnote else IosType.Subhead, color = IosColors.secondaryLabel)
        content()
        if (!footnote.isNullOrBlank()) Text(footnote, style = IosType.Caption1, color = IosColors.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
    }
}

/** A tinted note for warnings and hints. */
@Composable
fun NoteBox(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val dark = LocalInstrument.current.isDark
    val neutral = tone == StatusTone.NEUTRAL
    val color = if (neutral) IosColors.secondaryLabel else statusToneColor(tone)
    Text(
        text,
        color = color,
        style = IosType.Footnote,
        modifier = modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedShape(16.dp))
            .background(if (neutral) IosColors.fill else color.copy(alpha = if (dark) 0.18f else 0.11f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

/**
 * The iOS switch: 51 × 31, systemGreen when on, a 27 dp thumb with a soft shadow that
 * stretches while held and springs across. A light haptic tick confirms each change.
 */
@Composable
fun ClearSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
) {
    val dark = LocalInstrument.current.isDark
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val track by animateColorAsState(
        if (checked) IosColors.green else if (dark) Color(0xFF39393D) else Color(0xFFE9E9EA),
        tween(220),
        label = "switch track",
    )
    val thumbWidth by animateDpAsState(if (pressed) 33.dp else 27.dp, SlideSpring, label = "switch stretch")
    val thumbX by animateDpAsState(if (checked) 49.dp - thumbWidth else 2.dp, SlideSpring, label = "switch thumb")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(track)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, interactionSource = source, indication = null, enabled = enabled, role = Role.Switch) {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onCheckedChange(it)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 2.dp)
                .size(width = thumbWidth, height = 27.dp)
                .shadow(3.dp, CircleShape, ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
                .background(Color.White, CircleShape),
        )
    }
}

/** A filled iOS button: capsule, system blue, body-size semibold label. */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressFeedback(source)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = IosType.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A tinted iOS button: soft fill and label in the tint; red when destructive. */
@Composable
fun TonalPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    small: Boolean = false,
) {
    val color = if (danger) IosColors.red else MaterialTheme.colorScheme.primary
    val dark = LocalInstrument.current.isDark
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressFeedback(source)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(color.copy(alpha = if (dark) 0.22f else 0.13f))
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = if (small) 32.dp else 50.dp)
            .padding(horizontal = if (small) 14.dp else 20.dp, vertical = if (small) 6.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = color,
            style = if (small) IosType.Subhead.copy(fontWeight = FontWeight.SemiBold) else IosType.Headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A plain text button in the tint, which dims while pressed. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = IosType.Subhead,
        modifier = modifier
            .pressFeedback(source, 1f, 0.35f)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

/** A thin rounded meter that springs to its value. */
@Composable
fun MeterBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 6.dp,
) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow), label = "meter")
    Box(modifier.fillMaxWidth().height(thickness).clip(CircleShape).background(IosColors.fill)) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(CircleShape).background(color))
    }
}

private val TabularDisplay = IosType.LargeTitle.copy(fontFeatureSettings = "tnum", fontSize = 40.sp, lineHeight = 44.sp)

/** The headline number, with tabular digits that roll when the value changes. */
@Composable
fun BigReading(
    value: String,
    unit: String,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val up = (targetState.toIntOrNull() ?: 0) > (initialState.toIntOrNull() ?: 0)
                (slideInVertically(tween(260)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(200))) togetherWith
                    (slideOutVertically(tween(260)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(160)))
            },
            label = "reading",
        ) { shown ->
            Text(shown, style = TabularDisplay, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(unit, style = IosType.Subhead, color = IosColors.secondaryLabel, modifier = Modifier.padding(bottom = 6.dp))
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
        Text(label, style = IosType.Caption1, color = IosColors.secondaryLabel)
        Text(value, style = IosType.Headline.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
        if (!note.isNullOrBlank()) Text(note, style = IosType.Caption2.copy(fontWeight = FontWeight.Normal), color = IosColors.secondaryLabel)
    }
}

/**
 * The iOS segmented control: a grey track with a white thumb that slides on a spring.
 * [capsule] gives the rounder iOS 26 form used for the SIM switcher.
 */
@Composable
private fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    capsule: Boolean,
    height: Dp,
) {
    if (labels.isEmpty()) return
    val dark = LocalInstrument.current.isDark
    val haptics = LocalHapticFeedback.current
    val trackShape = if (capsule) CircleShape else ContinuousRoundedShape(9.dp)
    val thumbShape = if (capsule) CircleShape else ContinuousRoundedShape(7.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clip(trackShape).background(IosColors.fill).padding(2.dp),
    ) {
        val slot = maxWidth / labels.size
        val x by animateDpAsState(slot * selectedIndex.coerceIn(0, labels.lastIndex), SlideSpring, label = "segment thumb")
        Box(
            Modifier
                .offset(x = x)
                .width(slot)
                .fillMaxHeight()
                .shadow(2.dp, thumbShape, ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000))
                .background(if (dark) Color(0xFF636366) else Color.White, thumbShape),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val active = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            enabled = enabled,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!active) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = IosType.Footnote.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/** The SIM switcher: an iOS 26 capsule segmented control. */
@Composable
fun SimPicker(
    items: List<Pair<Int, String>>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (items.isEmpty()) return
    SegmentedControl(
        labels = items.map { it.second },
        selectedIndex = items.indexOfFirst { it.first == selectedId }.coerceAtLeast(0),
        onSelect = { onSelect(items[it].first) },
        modifier = modifier,
        enabled = enabled,
        capsule = true,
        height = 36.dp,
    )
}

/** A short choice inside a card, such as the 5G architecture. */
@Composable
fun Segments(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = SegmentedControl(options, selectedIndex, onSelect, modifier, enabled, capsule = false, height = 32.dp)

/** Space between the last item on a page and the floating tab bar. */
@Composable
fun PageEnd() {
    Spacer(Modifier.height(8.dp))
    BottomBarSpacer()
}
