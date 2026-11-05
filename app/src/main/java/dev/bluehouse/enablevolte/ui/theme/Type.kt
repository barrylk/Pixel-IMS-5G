package dev.bluehouse.enablevolte.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.R

/**
 * Clear Glass type: Figtree for everything people read, JetBrains Mono for measured
 * values so columns of readings stay aligned while they update.
 *
 * Figtree ships as one variable font (SIL OFL 1.1, see docs/licenses/Figtree-OFL.txt);
 * each weight below is an instance of it.
 */
@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) =
    Font(
        R.font.figtree_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700), figtree(800))

val Display = Figtree

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val Typography =
    Typography(
        displayLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp),
        displaySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.7).sp),
        headlineLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
        headlineSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
        titleMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
        titleSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
        bodyLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
        bodySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp),
        labelMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp),
    )

/** The headline reading on a screen — the one number you look at first. */
val ReadoutHuge =
    TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-1).sp,
    )

/** A value in a spec tile. */
val ReadoutMedium =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.3).sp)

/** Dense readings: neighbour rows, logs, raw captures. */
val ReadoutSmall =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)

/** Names kept from 1.0.9 so existing readouts keep working. */
val NumericTextStyle = ReadoutMedium
val NumericSmallTextStyle = ReadoutSmall

/** Condenses a long reading slightly rather than letting it wrap or clip. */
val ReadoutTight =
    ReadoutSmall.copy(textGeometricTransform = TextGeometricTransform(scaleX = 0.94f))
