package com.sp.player.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import com.sp.player.R
import com.sp.player.player.Prefs

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

    /** The app theme (style) for an accent colour (v1.2.0). Activities
     *  call setTheme() with this before setContentView, so system widgets
     *  (switches, radio buttons, dialogs, buttons) follow the theme too —
     *  before v1.2.0 they were always Pink. */
    fun themeFor(accent: Int): Int {
        return when (accent) {
            Prefs.ACCENT_BLUE -> R.style.Theme_SPPlayer_Blue
            Prefs.ACCENT_GREEN -> R.style.Theme_SPPlayer_Green
            Prefs.ACCENT_ORANGE -> R.style.Theme_SPPlayer_Orange
            Prefs.ACCENT_RAINBOW -> R.style.Theme_SPPlayer_Rainbow
            else -> R.style.Theme_SPPlayer
        }
    }

    /** The Rainbow theme's six-stop gradient (v1.2.0): mini-player
     *  background and Rainbow swatch. Built in code because XML gradients
     *  only support three stops. Static — no animation. */
    fun rainbowDrawable(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                0xFFF44336.toInt(), // red
                0xFFFF9800.toInt(), // orange
                0xFFFFEB3B.toInt(), // yellow
                0xFF4CAF50.toInt(), // green
                0xFF2196F3.toInt(), // blue
                0xFF9C27B0.toInt() // violet
            )
        )
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
