package dev.bluehouse.enablevolte.ui.theme

import android.content.Context

/** The appearance the user picked, independent of the system setting. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** System → Light → Dark → System, the order the top-bar toggle steps through. */
    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    companion object {
        private const val PREFS = "pixel_ims_appearance"
        private const val KEY = "theme_mode"

        fun load(context: Context): ThemeMode =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
                ?.let { runCatching { valueOf(it) }.getOrNull() }
                ?: SYSTEM

        fun save(context: Context, mode: ThemeMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        }
    }
}
