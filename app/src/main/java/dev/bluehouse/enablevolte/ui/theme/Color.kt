package dev.bluehouse.enablevolte.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The 2.2 palette: liquid glass.
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
val Void = Color(0xFF03060C)
val Deck = Color(0xFF070C16)
val Hull = Color(0xFF0C1422)
val HullHigh = Color(0xFF142034)

// Ground — light.
val Paper = Color(0xFFE8EEF6)
val PaperHull = Color(0xFFF7FAFD)
val PaperHullHigh = Color(0xFFE2EAF4)

// Ambient light pools behind the glass.
val PoolCyanDark = Color(0xFF00B4FF)
val PoolVioletDark = Color(0xFF7457FF)
val PoolEmberDark = Color(0xFFFF7A3D)
val PoolCyanLight = Color(0xFF6FD3FF)
val PoolVioletLight = Color(0xFFB9A4FF)
val PoolEmberLight = Color(0xFFFFC3A0)

// Accents.
val Signal = Color(0xFF3CE0FF)
val SignalDeep = Color(0xFF0068A8)
val Ember = Color(0xFFFFA23A)
val EmberDeep = Color(0xFFA85A06)
val Pulse = Color(0xFFFF3A5E)

// Signal quality ramp, per theme.
val Good = Color(0xFF2EE59A)
val Fair = Color(0xFFFFC247)
val Poor = Color(0xFFFF5A6E)
val GoodDeep = Color(0xFF0B8A55)
val FairDeep = Color(0xFF9A6200)
val PoorDeep = Color(0xFFC82434)

// Text.
val Ink = Color(0xFFF0F6FC)
val InkDim = Color(0xFFADBED0)
val InkFaint = Color(0xFF718599)
val PaperInk = Color(0xFF0A1622)
val PaperInkDim = Color(0xFF3F5164)
val PaperInkFaint = Color(0xFF6D8196)

// Glass — dark. White light on a dark pane, rim brightest where the light enters.
val FrostDark = Color(0x14FFFFFF)
val FrostDarkHigh = Color(0x26FFFFFF)
val EdgeDark = Color(0x24FFFFFF)
val EdgeDarkBright = Color(0x66FFFFFF)
val SheenDark = Color(0x8CFFFFFF)
val SpecularDark = Color(0x2EFFFFFF)
val CausticDark = Color(0x664FE3FF)
val ShadeDark = Color(0x33000000)
val ScrimDark = Color(0xB3070C16)

// Glass — light. Milk-white panes, with a darker lower rim so they lift off the paper.
val FrostLight = Color(0x80FFFFFF)
val FrostLightHigh = Color(0xC7FFFFFF)
val EdgeLight = Color(0x1A0A1622)
val EdgeLightBright = Color(0xF2FFFFFF)
val SheenLight = Color(0xFFFFFFFF)
val SpecularLight = Color(0x99FFFFFF)
val CausticLight = Color(0x2E0A2A4A)
val ShadeLight = Color(0x0F0A1622)
val ScrimLight = Color(0xCCF2F6FB)
