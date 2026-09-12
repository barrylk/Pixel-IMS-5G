package dev.bluehouse.enablevolte.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A neutral instrument palette.
 *
 * This app reports radio measurements and writes modem configuration, so the
 * interface should read like a measuring tool: near-neutral greys carry the
 * structure, and colour is spent only where it means something — one accent for
 * interactive elements, and three signal colours for state. Nothing here is
 * decorative.
 */

// Structure — dark.
val InkBackground = Color(0xFF0E1013)
val InkSurface = Color(0xFF14171C)
val InkSurfaceLow = Color(0xFF111419)
val InkSurfaceHigh = Color(0xFF1B1F26)
val InkSurfaceHighest = Color(0xFF232830)
val InkOutline = Color(0xFF2A303A)
val InkOnSurface = Color(0xFFE7EAEE)
val InkOnSurfaceVariant = Color(0xFF98A1AD)

// Structure — light.
val PaperBackground = Color(0xFFF7F8FA)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceLow = Color(0xFFFBFBFC)
val PaperSurfaceHigh = Color(0xFFF1F3F5)
val PaperSurfaceHighest = Color(0xFFE7EAEE)
val PaperOutline = Color(0xFFDCE0E5)
val PaperOnSurface = Color(0xFF12161C)
val PaperOnSurfaceVariant = Color(0xFF5B6572)

// The single accent. One hue, two values so it holds contrast on either ground.
val AccentBlue = Color(0xFF5091F2)
val AccentBlueDark = Color(0xFF1B62D6)

/**
 * Signal colours, in dark and light pairs.
 *
 * These carry meaning — a registered IMS session, a marginal measurement, a
 * refused write — so they are tuned per theme rather than reused across both,
 * where one of the two would always be under-contrasted.
 */
val SignalGreen = Color(0xFF3DD68C)
val SignalAmber = Color(0xFFE9A23B)
val SignalRed = Color(0xFFF2555F)

val SignalGreenDark = Color(0xFF12824C)
val SignalAmberDark = Color(0xFFA76B06)
val SignalRedDark = Color(0xFFC7303C)
