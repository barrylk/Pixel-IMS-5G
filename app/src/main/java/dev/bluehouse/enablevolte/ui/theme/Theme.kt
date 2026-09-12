package dev.bluehouse.enablevolte.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat

/**
 * Tokens Material's own scheme has no slot for.
 *
 * Panel fill, hairline and the signal ramp are not "primary" or "surface" in
 * Material's sense — they mean something specific here, and routing them
 * through an approximate Material slot is how a palette quietly loses its
 * meaning.
 */
data class InstrumentColors(
    val frost: Color,
    val frostHigh: Color,
    val edge: Color,
    val edgeBright: Color,
    val good: Color,
    val fair: Color,
    val poor: Color,
    val ember: Color,
    val pulse: Color,
    val glow: Color,
    val isDark: Boolean,
)

private val DarkInstrument =
    InstrumentColors(
        frost = FrostDark,
        frostHigh = FrostDarkHigh,
        edge = EdgeDark,
        edgeBright = EdgeDarkBright,
        good = Good,
        fair = Fair,
        poor = Poor,
        ember = Ember,
        pulse = Pulse,
        glow = Signal,
        isDark = true,
    )

val LocalInstrument = staticCompositionLocalOf { DarkInstrument }

private val LightInstrument =
    InstrumentColors(
        frost = FrostLight,
        frostHigh = FrostLightHigh,
        edge = EdgeLight,
        edgeBright = EdgeLightBright,
        good = GoodDeep,
        fair = FairDeep,
        poor = PoorDeep,
        ember = EmberDeep,
        pulse = Pulse,
        glow = SignalDeep,
        isDark = false,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Signal,
        onPrimary = Color(0xFF00212C),
        secondary = Ember,
        onSecondary = Color(0xFF2A1600),
        tertiary = Signal,
        background = Void,
        onBackground = Ink,
        surface = Hull,
        onSurface = Ink,
        surfaceContainerLowest = Void,
        surfaceContainerLow = Deck,
        surfaceContainer = Hull,
        surfaceContainerHigh = HullHigh,
        surfaceContainerHighest = HullHigh,
        surfaceVariant = HullHigh,
        onSurfaceVariant = InkDim,
        outline = EdgeDark,
        outlineVariant = EdgeDark,
        error = Poor,
        onError = Color(0xFF2B0007),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = SignalDeep,
        onPrimary = Color(0xFFFFFFFF),
        secondary = EmberDeep,
        onSecondary = Color(0xFFFFFFFF),
        tertiary = SignalDeep,
        background = Paper,
        onBackground = PaperInk,
        surface = PaperHull,
        onSurface = PaperInk,
        surfaceContainerLowest = PaperHull,
        surfaceContainerLow = PaperHull,
        surfaceContainer = PaperHull,
        surfaceContainerHigh = PaperHullHigh,
        surfaceContainerHighest = PaperHullHigh,
        surfaceVariant = PaperHullHigh,
        onSurfaceVariant = PaperInkDim,
        outline = EdgeLight,
        outlineVariant = EdgeLight,
        error = PoorDeep,
        onError = Color(0xFFFFFFFF),
    )

/**
 * Radii are soft again.
 *
 * 1.0.9 cut them to 10dp to look severe, which made panels read as boxes ruled
 * onto the page. Glass has thickness, and thickness has a radius.
 */
private val InstrumentShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(26.dp),
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun EnableVoLTETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic colour stays off: the ramp means something, and a wallpaper does
    // not know what a good RSRP looks like.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val instrument = if (darkTheme) DarkInstrument else LightInstrument
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            window.isNavigationBarContrastEnforced = false
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars = !darkTheme
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalInstrument provides instrument) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = InstrumentShapes,
            content = content,
        )
    }
}
