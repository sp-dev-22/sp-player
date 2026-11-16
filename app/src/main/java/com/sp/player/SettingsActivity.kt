package com.sp.player

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.sp.player.player.Prefs
import com.sp.player.ui.ThemeTint

class SettingsActivity : AppCompatActivity() {

    private lateinit var selectedColorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v1.2.0: theme variant for the chosen accent, before inflating.
        setTheme(ThemeTint.themeFor(Prefs.accent(this)))
        setContentView(R.layout.activity_settings)

        selectedColorText = findViewById(R.id.selected_color_text)

        val swatches = listOf(
            findViewById<Button>(R.id.swatch_pink) to Prefs.ACCENT_PINK,
            findViewById<Button>(R.id.swatch_blue) to Prefs.ACCENT_BLUE,
            findViewById<Button>(R.id.swatch_green) to Prefs.ACCENT_GREEN,
            findViewById<Button>(R.id.swatch_orange) to Prefs.ACCENT_ORANGE,
            findViewById<Button>(R.id.swatch_rainbow) to Prefs.ACCENT_RAINBOW
        )
        // Rainbow swatch shows the gradient (set in code — XML gradients
        // only support three stops).
        findViewById<Button>(R.id.swatch_rainbow).background =
            ThemeTint.rainbowDrawable()
        for ((button, color) in swatches) {
            button.setOnClickListener {
                if (Prefs.accent(this) != color) {
                    Prefs.setAccent(this, color)
                    // v1.2.0: recreate so the whole screen (switch,
                    // radios, buttons) shows the new theme at once.
                    recreate()
                }
            }
        }
        refreshSelectedColor()

        val nightGroup = findViewById<RadioGroup>(R.id.night_group)
        when (Prefs.nightMode(this)) {
            Prefs.NIGHT_LIGHT -> nightGroup.check(R.id.night_light)
            Prefs.NIGHT_DARK -> nightGroup.check(R.id.night_dark)
            else -> nightGroup.check(R.id.night_system)
        }
        nightGroup.setOnCheckedChangeListener { _, checkedId ->
            val (mode, pref) = when (checkedId) {
                R.id.night_light -> AppCompatDelegate.MODE_NIGHT_NO to Prefs.NIGHT_LIGHT
                R.id.night_dark -> AppCompatDelegate.MODE_NIGHT_YES to Prefs.NIGHT_DARK
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to Prefs.NIGHT_SYSTEM
            }
            Prefs.setNightMode(this, pref)
            // Applies immediately; activities recreate automatically.
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        // Background play video (v1.1.0): default ON.
        val bgVideoSwitch = findViewById<SwitchCompat>(R.id.bg_video_switch)
        bgVideoSwitch.isChecked = Prefs.bgVideo(this)
        bgVideoSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setBgVideo(this, checked)
        }

        // Support page (v1.1.1).
        findViewById<Button>(R.id.support_button).setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }

        // Version from the build, so the footer never goes stale again.
        findViewById<TextView>(R.id.version_text).text =
            "SP Player v" + BuildConfig.VERSION_NAME
    }

    private fun refreshSelectedColor() {
        val current = Prefs.accent(this)
        val index = Prefs.ACCENTS.indexOf(current).coerceAtLeast(0)
        selectedColorText.text = "Selected colour: " + Prefs.ACCENT_NAMES[index]
    }
}
