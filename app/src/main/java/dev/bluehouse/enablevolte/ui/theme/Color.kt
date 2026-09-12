package dev.bluehouse.enablevolte.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The 2.0 palette.
 *
 * The ground is a navy-teal rather than a neutral grey, because the panels are
 * translucent and translucency needs something to be translucent over. Grey
 * frosted on grey was the failure of the original theme: it read as haze, not
 * as layers.
 *
 * Colour is spent in three places and nowhere else — one accent for anything
 * interactive, one warm counterweight so the accent is not a lone pop on a dark
 * ground, and a three-stop ramp reserved strictly for signal quality. If a
 * number is green in this app, the measurement is good.
 */

// Ground — dark.
val Void = Color(0xFF04070C)
val Deck = Color(0xFF081220)
val Hull = Color(0xFF0A1420)
val HullHigh = Color(0xFF102030)

// Ground — light.
val Paper = Color(0xFFEDF2F7)
val PaperHull = Color(0xFFFFFFFF)
val PaperHullHigh = Color(0xFFE3EBF3)

// Accents.
val Signal = Color(0xFF00E0FF)
val SignalDeep = Color(0xFF0077A8)
val Ember = Color(0xFFFFA23A)
val EmberDeep = Color(0xFFB2660C)
val Pulse = Color(0xFFFF3A5E)

// Signal quality ramp, per theme.
val Good = Color(0xFF25E08C)
val Fair = Color(0xFFFFC247)
val Poor = Color(0xFFFF5A6E)
val GoodDeep = Color(0xFF0F9D63)
val FairDeep = Color(0xFFB87400)
val PoorDeep = Color(0xFFD3303F)

// Text.
val Ink = Color(0xFFE9F4FA)
val InkDim = Color(0xFFA9BFD0)
val InkFaint = Color(0xFF6F8698)
val PaperInk = Color(0xFF0A1622)
val PaperInkDim = Color(0xFF445668)
val PaperInkFaint = Color(0xFF74889B)

/*
 * Panel fills and hairlines.
 *
 * These were ported straight from a CSS prototype where the panels also had
 * `backdrop-filter: blur()` behind them. Compose has no backdrop blur, and
 * without it a 7% white fill over a near-black ground lands about ten levels
 * above the background — outlines, not glass. The fill does all the work here,
 * so it carries roughly three times the alpha the prototype used, with a
 * brighter top edge standing in for the highlight the blur used to give.
 */
val FrostDark = Color(0x2E92C4E0)
val FrostDarkHigh = Color(0x4592C4E0)
val EdgeDark = Color(0x3DA8D6F0)
val EdgeDarkBright = Color(0x73C4E8FF)
val SheenDark = Color(0x59DCF0FF)

val FrostLight = Color(0xFFFFFFFF)
val FrostLightHigh = Color(0xFFFFFFFF)
val EdgeLight = Color(0x2E0A1622)
val EdgeLightBright = Color(0x520A1622)
val SheenLight = Color(0xB3FFFFFF)
