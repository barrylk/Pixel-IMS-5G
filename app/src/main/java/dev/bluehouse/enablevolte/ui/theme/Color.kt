package dev.bluehouse.enablevolte.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The 2.3 palette: clear glass.
 *
 * Glass is kept for what floats (tab bar, top-bar controls, SIM picker, sheets),
 * blurred for real where the platform allows. Content sits on calm solid cards.
 *
 * Glass is only visible against something. The ground is therefore lit by
 * three soft pools of colour, and every panel is a translucent pane over them
 * with a specular rim, a highlight where the light enters and a faint shade
 * where it leaves. Without a backdrop blur in Compose, the pools are kept large
 * and smooth on purpose: a blurred smooth gradient looks the same as the
 * gradient, so the pane reads as frosted without sampling what is behind it.
 *
 * Colour outside the ground is still spent in three places only — one accent
 * for anything interactive, one warm counterweight, and a three-stop ramp
 * reserved strictly for signal quality. If a number is green, the measurement
 * is good.
 */

// Ground — dark.
val Void = Color(0xFF040507)
val Deck = Color(0xFF07090D)
val Hull = Color(0xFF14181F)
val HullHigh = Color(0xFF1C212A)

// Ground — light.
val Paper = Color(0xFFF2F4F8)
val PaperHull = Color(0xFFFFFFFF)
val PaperHullHigh = Color(0xFFE8EBF1)

// Ambient light pools behind the glass.
val PoolCyanDark = Color(0xFF12305A)
val PoolVioletDark = Color(0xFF2A1D4D)
val PoolEmberDark = Color(0xFF0F3A33)
val PoolCyanLight = Color(0xFFCFE3FF)
val PoolVioletLight = Color(0xFFE7DCFF)
val PoolEmberLight = Color(0xFFD6F5EC)

// Accents.
val Signal = Color(0xFF5AA8FF)
val SignalDeep = Color(0xFF0B6BD8)
val Ember = Color(0xFFFFA23A)
val EmberDeep = Color(0xFFA85A06)
val Pulse = Color(0xFFFF3A5E)

// Signal quality ramp, per theme.
val Good = Color(0xFF45C983)
val Fair = Color(0xFFF0A93A)
val Poor = Color(0xFFFF6A5F)
val GoodDeep = Color(0xFF1A8F55)
val FairDeep = Color(0xFFB26B00)
val PoorDeep = Color(0xFFD03B33)

// Text.
val Ink = Color(0xFFEEF2F7)
val InkDim = Color(0xFF97A1AE)
val InkFaint = Color(0xFF6E7884)
val PaperInk = Color(0xFF10141A)
val PaperInkDim = Color(0xFF5F6875)
val PaperInkFaint = Color(0xFF808894)

// Glass — dark. White light on a dark pane, rim brightest where the light enters.
val FrostDark = Color(0x14FFFFFF)
val FrostDarkHigh = Color(0x26FFFFFF)
val EdgeDark = Color(0x1FFFFFFF)
val EdgeDarkBright = Color(0x66FFFFFF)
val SheenDark = Color(0x8CFFFFFF)
val SpecularDark = Color(0x2EFFFFFF)
val CausticDark = Color(0x664FE3FF)
val ShadeDark = Color(0x33000000)
val ScrimDark = Color(0x8C1C212A)

// Glass — light. Milk-white panes, with a darker lower rim so they lift off the paper.
val FrostLight = Color(0x80FFFFFF)
val FrostLightHigh = Color(0xC7FFFFFF)
val EdgeLight = Color(0x1410141A)
val EdgeLightBright = Color(0xF2FFFFFF)
val SheenLight = Color(0xFFFFFFFF)
val SpecularLight = Color(0x99FFFFFF)
val CausticLight = Color(0x2E0A2A4A)
val ShadeLight = Color(0x0F0A1622)
val ScrimLight = Color(0x94FFFFFF)
