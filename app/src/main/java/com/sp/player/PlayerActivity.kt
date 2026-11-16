package com.sp.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.sp.player.player.PlayerManager
import com.sp.player.player.PlaylistStore
import com.sp.player.player.Prefs
import com.sp.player.player.formatDuration
import com.sp.player.ui.ThemeTint

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var gestureHint: TextView
    private lateinit var audioPanel: LinearLayout
    private lateinit var audioTitle: TextView
    private lateinit var audioArtist: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: Button
    private lateinit var btnRepeat: Button
    private lateinit var btnSpeed: Button
    private lateinit var btnSleep: Button
    private lateinit var btnPlaylist: Button
    private lateinit var volumeBar: SeekBar
    private lateinit var sleepText: TextView

    private var isFullscreen = false
    private var isVideoMode = false
    private var userSeeking = false

    // Back in fullscreen exits fullscreen first (instead of leaving the
    // player). It goes through the fullscreen button itself so Media3's
    // internal fullscreen state stays in sync with ours.
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val btn = playerView.findViewById<View>(R.id.exo_fullscreen)
            if (btn != null) btn.performClick() else setFullscreen(false)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val subtitlePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Session-only access is enough for this playback.
                }
                PlayerManager.setManualSubtitle(this, uri)
                Toast.makeText(this, "Subtitle लग गया", Toast.LENGTH_SHORT).show()
            }
        }

    // Video swipe gestures: right half = volume, left half = brightness.
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureVolume = -1
    private var gestureBrightness = -1f
    private var gestureSideRight = true
    private var gestureActive = false
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val track = PlayerManager.current
        if (track == null) {
            finish()
            return
        }
        isVideoMode = track.isVideo
        setContentView(R.layout.activity_player)
        onBackPressedDispatcher.addCallback(this, backCallback)

        playerView = findViewById(R.id.player_view)
        gestureHint = findViewById(R.id.gesture_hint)
        audioPanel = findViewById(R.id.audio_panel)
        audioTitle = findViewById(R.id.audio_title)
        audioArtist = findViewById(R.id.audio_artist)
        seekBar = findViewById(R.id.seek_bar)
        timeCurrent = findViewById(R.id.time_current)
        timeTotal = findViewById(R.id.time_total)
        btnPrev = findViewById(R.id.btn_prev)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnShuffle = findViewById(R.id.btn_shuffle)
        btnRepeat = findViewById(R.id.btn_repeat)
        btnSpeed = findViewById(R.id.btn_speed)
        btnSleep = findViewById(R.id.btn_sleep)
        btnPlaylist = findViewById(R.id.btn_playlist)
        volumeBar = findViewById(R.id.volume_bar)
        sleepText = findViewById(R.id.sleep_text)

        // Theme wash on the full-player screen (audio panel; video shows the
        // video itself, so the tint only matters here).
        audioPanel.setBackgroundColor(
            ThemeTint.accentBackground(this, Prefs.accent(this), 0.08f)
        )

        if (isVideoMode) {
            audioPanel.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            playerView.player = PlayerManager.player
            playerView.setControllerAnimationEnabled(false)
            playerView.showController()
            playerView.findViewById<Button>(R.id.btn_speed_video)?.setOnClickListener {
                showSpeedDialog()
            }
            playerView.findViewById<Button>(R.id.btn_sleep_video)?.setOnClickListener {
                showSleepDialog()
            }
            playerView.findViewById<Button>(R.id.btn_subtitle_video)?.setOnClickListener {
                subtitlePicker.launch(arrayOf("*/*"))
            }
            // Media3's own fullscreen hook: setting a listener is also what
            // makes the fullscreen button VISIBLE (PlayerControlView hides
            // it until a listener is set — v1.0.1 bug: no button at all).
            // Media3 toggles its internal state and passes the NEW state;
            // never set our own click listener on exo_fullscreen (it would
            // overwrite Media3's and break the icon toggle).
            playerView.setFullscreenButtonClickListener { enter ->
                setFullscreen(enter)
            }
            setupGestures()
        } else {
            playerView.visibility = View.GONE
            audioPanel.visibility = View.VISIBLE
            setupAudioUi()
        }
    }

    override fun onStart() {
        super.onStart()
        if (isVideoMode) playerView.player = PlayerManager.player
    }

    override fun onResume() {
        super.onResume()
        handler.post(progressUpdater)
        updateProgress()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(progressUpdater)
    }

    override fun onStop() {
        super.onStop()
        // Video pauses when the app backgrounds (battery). Audio keeps playing.
        PlayerManager.pauseIfVideo()
        if (isVideoMode) playerView.player = null
    }

    private fun setupAudioUi() {
        val accent = ColorStateList.valueOf(Prefs.accent(this))
        seekBar.progressTintList = accent
        seekBar.thumbTintList = accent
        volumeBar.progressTintList = accent
        volumeBar.thumbTintList = accent
        btnPrev.imageTintList = accent
        btnPlayPause.imageTintList = accent
        btnNext.imageTintList = accent
        volumeBar.max = 100
        volumeBar.progress = Prefs.appVolume(this)
        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) PlayerManager.setAppVolume(this@PlayerActivity, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) timeCurrent.text = formatDuration(progress.toLong())
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                userSeeking = false
                PlayerManager.seekTo(sb?.progress?.toLong() ?: 0L)
            }
        })
        btnPlayPause.setOnClickListener {
            PlayerManager.togglePlayPause()
            updateProgress()
        }
        btnPrev.setOnClickListener { PlayerManager.prev() }
        btnNext.setOnClickListener { PlayerManager.next() }
        btnShuffle.setOnClickListener {
            val p = PlayerManager.player ?: return@setOnClickListener
            PlayerManager.setShuffle(!p.shuffleModeEnabled)
            updateToggleButtons()
        }
        btnRepeat.setOnClickListener {
            val p = PlayerManager.player ?: return@setOnClickListener
            val next = when (p.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            PlayerManager.setRepeatMode(next)
            updateToggleButtons()
        }
        btnSpeed.setOnClickListener { showSpeedDialog() }
        btnSleep.setOnClickListener { showSleepDialog() }
        btnPlaylist.setOnClickListener {
            val t = PlayerManager.current ?: return@setOnClickListener
            if (PlaylistStore.contains(this, t)) {
                PlaylistStore.remove(this, t)
                Toast.makeText(this, "Playlist से हटाया गया", Toast.LENGTH_SHORT).show()
            } else {
                PlaylistStore.add(this, t)
                Toast.makeText(this, "Playlist में जोड़ा गया", Toast.LENGTH_SHORT).show()
            }
            updateToggleButtons()
        }
        updateToggleButtons()
    }

    private fun updateProgress() {
        val player = PlayerManager.player
        val track = PlayerManager.current
        if (player == null || track == null) {
            finish()
            return
        }
        if (track.isVideo != isVideoMode) {
            // Queue moved between audio and video (mixed queue) — rebuild the screen.
            recreate()
            return
        }
        if (!isVideoMode) {
            audioTitle.text = track.title
            audioArtist.text = track.artist
            audioArtist.visibility = if (track.artist.isBlank()) View.GONE else View.VISIBLE
            val dur = player.duration.coerceAtLeast(0)
            val pos = player.currentPosition.coerceAtLeast(0)
            if (!userSeeking) {
                seekBar.max = dur.toInt()
                seekBar.progress = pos.toInt()
            }
            timeCurrent.text = formatDuration(pos)
            timeTotal.text = formatDuration(dur)
            btnPlayPause.setImageResource(
                if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            val rem = PlayerManager.sleepRemainingMs
            sleepText.visibility = if (rem > 0) View.VISIBLE else View.GONE
            if (rem > 0) sleepText.text = "Sleep: " + formatDuration(rem)
            updateToggleButtons()
        }
    }

    private fun updateToggleButtons() {
        val player = PlayerManager.player ?: return
        val accent = Prefs.accent(this)
        val normal = ContextCompat.getColor(this, R.color.text_secondary)
        btnShuffle.setTextColor(if (player.shuffleModeEnabled) accent else normal)
        val repeatLabel = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> "Repeat: All"
            Player.REPEAT_MODE_ONE -> "Repeat: One"
            else -> "Repeat: Off"
        }
        btnRepeat.text = repeatLabel
        btnRepeat.setTextColor(if (player.repeatMode != Player.REPEAT_MODE_OFF) accent else normal)
        btnSpeed.text = "Speed: " + formatSpeed(Prefs.speed(this))
        val track = PlayerManager.current
        btnPlaylist.text =
            if (track != null && PlaylistStore.contains(this, track)) "Playlist से हटाएं"
            else "Playlist में जोड़ें"
    }

    private fun formatSpeed(s: Float): String {
        return if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"
    }

    private fun showSpeedDialog() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = speeds.map { formatSpeed(it) }.toTypedArray()
        val checked = speeds.indexOfFirst { it == Prefs.speed(this) }.coerceAtLeast(2)
        AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                PlayerManager.setSpeed(this, speeds[which])
                updateToggleButtons()
                dialog.dismiss()
            }
            .setNegativeButton("बंद करें", null)
            .show()
    }

    private fun showSleepDialog() {
        val minutes = intArrayOf(0, 5, 10, 15, 30, 45, 60)
        val labels = arrayOf("Off", "5 मिनट", "10 मिनट", "15 मिनट", "30 मिनट", "45 मिनट", "60 मिनट")
        AlertDialog.Builder(this)
            .setTitle("Sleep Timer")
            .setItems(labels) { _, which ->
                val m = minutes[which]
                if (m == 0) {
                    PlayerManager.cancelSleepTimer()
                    Toast.makeText(this, "Sleep timer बंद", Toast.LENGTH_SHORT).show()
                } else {
                    PlayerManager.startSleepTimer(m)
                    Toast.makeText(this, "Sleep timer: $m मिनट", Toast.LENGTH_SHORT).show()
                }
                updateProgress()
            }
            .show()
    }

    // Fullscreen state comes from Media3's fullscreen-button callback
    // (the NEW state after the tap), never from toggling our own field.
    private fun setFullscreen(enter: Boolean) {
        if (enter == isFullscreen) return
        isFullscreen = enter
        backCallback.isEnabled = enter
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enter) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupGestures() {
        playerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartX = event.x
                    gestureStartY = event.y
                    gestureActive = false
                    gestureSideRight = event.x > playerView.width / 2f
                    gestureVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val lpBrightness = window.attributes.screenBrightness
                    gestureBrightness = if (lpBrightness < 0) 0.5f else lpBrightness
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = gestureStartY - event.y
                    val dx = event.x - gestureStartX
                    if (!gestureActive && Math.abs(dy) > 24 && Math.abs(dy) > Math.abs(dx) * 1.5) {
                        gestureActive = true
                    }
                    if (gestureActive) {
                        val h = playerView.height.coerceAtLeast(1)
                        val delta = dy / h
                        if (gestureSideRight) {
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val v = (gestureVolume + delta * max).toInt()
                                .coerceIn(0, max)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                            showGestureHint("Volume: " + (v * 100 / max.coerceAtLeast(1)) + "%")
                        } else {
                            val b = (gestureBrightness + delta).coerceIn(0.01f, 1f)
                            val lp = window.attributes
                            lp.screenBrightness = b
                            window.attributes = lp
                            showGestureHint("Brightness: " + (b * 100).toInt() + "%")
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gestureActive) {
                        handler.postDelayed({ gestureHint.visibility = View.GONE }, 800)
                        gestureActive = false
                    }
                }
            }
            // Let PlayerView handle taps (controller show/hide).
            false
        }
    }

    private fun showGestureHint(text: String) {
        gestureHint.text = text
        gestureHint.visibility = View.VISIBLE
    }
}
