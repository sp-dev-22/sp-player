package com.sp.player

import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.sp.player.player.Prefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var selectedColorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        selectedColorText = findViewById(R.id.selected_color_text)

        val swatches = listOf(
            findViewById<Button>(R.id.swatch_pink) to Prefs.ACCENT_PINK,
            findViewById<Button>(R.id.swatch_blue) to Prefs.ACCENT_BLUE,
            findViewById<Button>(R.id.swatch_green) to Prefs.ACCENT_GREEN,
            findViewById<Button>(R.id.swatch_orange) to Prefs.ACCENT_ORANGE
        )
        for ((button, color) in swatches) {
            button.setOnClickListener {
                Prefs.setAccent(this, color)
                refreshSelectedColor()
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
    }

    private fun refreshSelectedColor() {
        val current = Prefs.accent(this)
        val index = Prefs.ACCENTS.indexOf(current).coerceAtLeast(0)
        selectedColorText.text = "चुना हुआ रंग: " + Prefs.ACCENT_NAMES[index]
    }
}
