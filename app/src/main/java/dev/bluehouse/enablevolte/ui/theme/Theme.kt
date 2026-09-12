package dev.bluehouse.enablevolte.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = AccentBlue,
        onPrimary = Color(0xFF06121F),
        secondary = InkOnSurfaceVariant,
        tertiary = AccentBlue,
        background = InkBackground,
        onBackground = InkOnSurface,
        surface = InkSurface,
        onSurface = InkOnSurface,
        surfaceContainerLowest = InkBackground,
        surfaceContainerLow = InkSurfaceLow,
        surfaceContainer = InkSurface,
        surfaceContainerHigh = InkSurfaceHigh,
        surfaceContainerHighest = InkSurfaceHighest,
        surfaceVariant = InkSurfaceHigh,
        onSurfaceVariant = InkOnSurfaceVariant,
        outline = InkOutline,
        outlineVariant = InkOutline,
        error = SignalRed,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = AccentBlueDark,
        onPrimary = Color(0xFFFFFFFF),
        secondary = PaperOnSurfaceVariant,
        tertiary = AccentBlueDark,
        background = PaperBackground,
        onBackground = PaperOnSurface,
        surface = PaperSurface,
        onSurface = PaperOnSurface,
        surfaceContainerLowest = PaperSurface,
        surfaceContainerLow = PaperSurfaceLow,
        surfaceContainer = PaperSurface,
        surfaceContainerHigh = PaperSurfaceHigh,
        surfaceContainerHighest = PaperSurfaceHighest,
        surfaceVariant = PaperSurfaceHigh,
        onSurfaceVariant = PaperOnSurfaceVariant,
        outline = PaperOutline,
        outlineVariant = PaperOutline,
        error = SignalRedDark,
    )

/**
 * Corner radii are deliberately small.
 *
 * Large radii read as consumer software. A measuring tool wants rectangular
 * panels that sit flush against each other, so the scale tops out where the
 * previous one started.
 */
private val InstrumentShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun EnableVoLTETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic colour is deliberately not offered. The palette carries meaning —
    // signal state, accent, neutral structure — and wallpaper-derived hues would
    // put that at the mercy of the user's home screen.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = InstrumentShapes,
        content = content,
    )
}
