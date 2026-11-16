package com.sp.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sp.player.player.Prefs
import com.sp.player.ui.ThemeTint

/** Support page (v1.1.1): about SP Player, the developer, and links.
 *  All links open in the browser or YouTube app — the app itself stays
 *  fully offline (intents only, no internet permission). */
class SupportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v1.2.0: theme variant for the chosen accent, before inflating.
        setTheme(ThemeTint.themeFor(Prefs.accent(this)))
        setContentView(R.layout.activity_support)

        findViewById<TextView>(R.id.support_title).setTextColor(Prefs.accent(this))
        findViewById<TextView>(R.id.support_version_text).text =
            "Version " + BuildConfig.VERSION_NAME

        findViewById<Button>(R.id.youtube_button).setOnClickListener {
            openLink("https://www.youtube.com/@sp-dev-22")
        }
        findViewById<Button>(R.id.github_button).setOnClickListener {
            openLink("https://github.com/sp-dev-22/sp-player")
        }
        // v1.2.0: our website (projects portal).
        findViewById<Button>(R.id.website_button).setOnClickListener {
            openLink("https://sp-dev-22.github.io")
        }
        findViewById<Button>(R.id.share_button).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "SP Player — a fully offline audio-video player for " +
                        "low-end phones.\n" +
                        "https://github.com/sp-dev-22/sp-player/releases"
                )
            }
            startActivity(Intent.createChooser(intent, "Share SP Player"))
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }
}
