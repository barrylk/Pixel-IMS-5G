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
 * iOS-style type on Android.
 *
 * San Francisco is licensed only for Apple platforms, so the app uses Inter (SIL OFL 1.1,
 * docs/licenses/Inter-OFL.txt), the closest open design. Like SF, Inter has an optical-size
 * axis: [InterText] (opsz 16) is drawn for body sizes and [InterDisplay] (opsz 32) for
 * titles, the same split as SF Text and SF Display. Sizes and tracking follow Apple's
 * Dynamic Type "Large" defaults: 34 large title, 17 body, 15 subhead, 13 footnote.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(
    weight: Int,
    opsz: Float,
) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
)

val InterText = FontFamily(inter(400, 16f), inter(500, 16f), inter(600, 16f), inter(700, 16f), inter(800, 16f))
val InterDisplay = FontFamily(inter(400, 32f), inter(500, 32f), inter(600, 32f), inter(700, 32f), inter(800, 32f))

val Display = InterDisplay

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Apple's Dynamic Type sizes with Inter's tracking tuned to sit like SF. */
object IosType {
    val LargeTitle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.6).sp)
    val Title1 = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp)
    val Title2 = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp)
    val Title3 = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp)
    val Headline = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.43).sp)
    val Body = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.43).sp)
    val Callout = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.32).sp)
    val Subhead = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp)
    val Footnote = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.08).sp)
    val Caption1 = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
    val Caption2 = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.06.sp)
}

val Typography =
    Typography(
        displayLarge = IosType.LargeTitle,
        displaySmall = IosType.Title1,
        headlineLarge = IosType.Title1,
        headlineSmall = IosType.Title2,
        titleLarge = IosType.Title3,
        titleMedium = IosType.Headline,
        titleSmall = IosType.Subhead.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = IosType.Body,
        bodyMedium = IosType.Subhead,
        bodySmall = IosType.Footnote,
        labelLarge = IosType.Headline,
        labelMedium = IosType.Footnote.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = IosType.Caption2,
    )

/** The headline reading on a screen — the one number you look at first. */
val ReadoutHuge =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.2).sp)

/** A value in a spec tile. */
val ReadoutMedium =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.3).sp)

/** Dense readings: neighbour rows, logs, raw captures. */
val ReadoutSmall =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)

/** Names kept from 1.0.9 so existing readouts keep working. */
val NumericTextStyle = ReadoutMedium
val NumericSmallTextStyle = ReadoutSmall

/** Condenses a long reading slightly rather than letting it wrap or clip. */
val ReadoutTight =
    ReadoutSmall.copy(textGeometricTransform = TextGeometricTransform(scaleX = 0.94f))
