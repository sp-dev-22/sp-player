package com.sp.player.player

import android.content.Context

/** App settings kept in one SharedPreferences file. */
object Prefs {
    private const val NAME = "sp_player_prefs"

    const val ACCENT_PINK = 0xFFE91E63.toInt()
    const val ACCENT_BLUE = 0xFF2196F3.toInt()
    const val ACCENT_GREEN = 0xFF4CAF50.toInt()
    const val ACCENT_ORANGE = 0xFFFF9800.toInt()
    // Rainbow (v1.2.0): violet is the theme's representative colour (text,
    // icons, system widgets); the mini-player and swatch show the full
    // rainbow gradient instead of this flat colour.
    const val ACCENT_RAINBOW = 0xFF7C4DFF.toInt()
    val ACCENTS = listOf(
        ACCENT_PINK, ACCENT_BLUE, ACCENT_GREEN, ACCENT_ORANGE, ACCENT_RAINBOW
    )
    val ACCENT_NAMES = listOf("Pink", "Blue", "Green", "Orange", "Rainbow")

    /** True when the stored accent is the Rainbow theme. */
    fun isRainbow(context: Context): Boolean = accent(context) == ACCENT_RAINBOW

    const val NIGHT_SYSTEM = 0
    const val NIGHT_LIGHT = 1
    const val NIGHT_DARK = 2

    const val SORT_DATE = 0
    const val SORT_NAME = 1

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun accent(context: Context): Int = prefs(context).getInt("accent", ACCENT_PINK)
    fun setAccent(context: Context, color: Int) {
        prefs(context).edit().putInt("accent", color).apply()
    }

    fun nightMode(context: Context): Int = prefs(context).getInt("night_mode", NIGHT_SYSTEM)
    fun setNightMode(context: Context, mode: Int) {
        prefs(context).edit().putInt("night_mode", mode).apply()
    }

    fun sortBy(context: Context): Int = prefs(context).getInt("sort_by", SORT_DATE)
    fun setSortBy(context: Context, sort: Int) {
        prefs(context).edit().putInt("sort_by", sort).apply()
    }

    /** In-app volume 0-100, independent of the system media volume. Default 25. */
    fun appVolume(context: Context): Int =
        prefs(context).getInt("app_volume", 25).coerceIn(0, 100)

    fun setAppVolume(context: Context, volume: Int) {
        prefs(context).edit().putInt("app_volume", volume.coerceIn(0, 100)).apply()
    }

    fun speed(context: Context): Float = prefs(context).getFloat("speed", 1f)
    fun setSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat("speed", speed).apply()
    }

    /** Background play video (v1.1.0): keep a video's audio playing when the
     *  app backgrounds. Default ON. */
    fun bgVideo(context: Context): Boolean = prefs(context).getBoolean("bg_video", true)
    fun setBgVideo(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("bg_video", enabled).apply()
    }
}
