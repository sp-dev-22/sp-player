package com.sp.player.ui

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.core.graphics.ColorUtils

/**
 * Accent-tinted colours that work in day and night mode: the accent is
 * blended over the current window background instead of replacing it, so a
 * theme change recolours backgrounds and highlights, not just text.
 */
object ThemeTint {

    /** Accent blended over the window background ([ratio] 0.0-1.0). */
    fun accentBackground(context: Context, accent: Int, ratio: Float): Int {
        return ColorUtils.blendARGB(windowBackground(context), accent, ratio)
    }

    /** The current window background colour (?android:attr/colorBackground). */
    fun windowBackground(context: Context): Int {
        return resolveColor(context, android.R.attr.colorBackground)
            ?: if (isNight(context)) 0xFF121212.toInt() else 0xFFFFFFFF.toInt()
    }

    /** The current primary text colour (?android:attr/textColorPrimary). */
    fun textPrimary(context: Context): Int {
        return resolveColor(context, android.R.attr.textColorPrimary)
            ?: if (isNight(context)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    }

    private fun resolveColor(context: Context, attr: Int): Int? {
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(attr, tv, true)) return null
        return if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            tv.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) tv.data else null
    }

    private fun isNight(context: Context): Boolean {
        return (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
