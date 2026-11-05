package dev.bluehouse.enablevolte.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The 2.3 palette: Apple system colours (systemBlue, systemGreen, systemOrange,
 * systemRed, grouped backgrounds, labels and separators) for an iOS-native look.
 * The earlier text below describes the glass approach that still applies to bars.
 *
 * Previously: clear glass.
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
val Void = Color(0xFF000000)
val Deck = Color(0xFF000000)
val Hull = Color(0xFF1C1C1E)
val HullHigh = Color(0xFF2C2C2E)

// Ground — light.
val Paper = Color(0xFFF2F2F7)
val PaperHull = Color(0xFFFFFFFF)
val PaperHullHigh = Color(0xFFE5E5EA)

// Ambient light pools behind the glass.
val PoolCyanDark = Color(0xFF000000)
val PoolVioletDark = Color(0xFF000000)
val PoolEmberDark = Color(0xFF000000)
val PoolCyanLight = Color(0xFFF2F2F7)
val PoolVioletLight = Color(0xFFF2F2F7)
val PoolEmberLight = Color(0xFFF2F2F7)

// Accents.
val Signal = Color(0xFF0A84FF)
val SignalDeep = Color(0xFF007AFF)
val Ember = Color(0xFFFFA23A)
val EmberDeep = Color(0xFFA85A06)
val Pulse = Color(0xFFFF3A5E)

// Signal quality ramp, per theme.
val Good = Color(0xFF30D158)
val Fair = Color(0xFFFF9F0A)
val Poor = Color(0xFFFF453A)
val GoodDeep = Color(0xFF34C759)
val FairDeep = Color(0xFFFF9500)
val PoorDeep = Color(0xFFFF3B30)

// Text.
val Ink = Color(0xFFFFFFFF)
val InkDim = Color(0x99EBEBF5)
val InkFaint = Color(0x4DEBEBF5)
val PaperInk = Color(0xFF000000)
val PaperInkDim = Color(0x993C3C43)
val PaperInkFaint = Color(0x4D3C3C43)

// Glass — dark. White light on a dark pane, rim brightest where the light enters.
val FrostDark = Color(0x14FFFFFF)
val FrostDarkHigh = Color(0x26FFFFFF)
val EdgeDark = Color(0x99545458)
val EdgeDarkBright = Color(0x66FFFFFF)
val SheenDark = Color(0x8CFFFFFF)
val SpecularDark = Color(0x2EFFFFFF)
val CausticDark = Color(0x664FE3FF)
val ShadeDark = Color(0x33000000)
val ScrimDark = Color(0xB31C1C1E)

// Glass — light. Milk-white panes, with a darker lower rim so they lift off the paper.
val FrostLight = Color(0x80FFFFFF)
val FrostLightHigh = Color(0xC7FFFFFF)
val EdgeLight = Color(0x4A3C3C43)
val EdgeLightBright = Color(0xF2FFFFFF)
val SheenLight = Color(0xFFFFFFFF)
val SpecularLight = Color(0x99FFFFFF)
val CausticLight = Color(0x2E0A2A4A)
val ShadeLight = Color(0x0F0A1622)
val ScrimLight = Color(0xCCF9F9F9)
