package com.sp.player.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sp.player.PlayerService
import java.io.File

/**
 * Single owner of the ExoPlayer instance (process lifetime) and the playback queue.
 * The PlayerService only owns the MediaSession/notification; it never releases the player.
 */
object PlayerManager {

    var player: ExoPlayer? = null
        private set

    var queue: List<Track> = emptyList()
        private set

    var currentIndex: Int = -1
        private set

    val current: Track?
        get() = queue.getOrNull(currentIndex)

    /**
     * Play Video as Audio (v1.1.0): the current video is playing audio-only
     * (audio panel shown, video surface detached). Session state, NOT a
     * persisted pref. Resets to false whenever the current track changes —
     * each new track starts in its natural mode. Changing it also switches
     * the active sleep timer to the new effective mode.
     */
    var audioOnly: Boolean = false
        set(value) {
            field = value
            setActiveSleepMode(isEffectiveVideo)
        }

    /** Effective video mode: a video track NOT in audio-only mode. */
    val isEffectiveVideo: Boolean
        get() = current?.isVideo == true && !audioOnly

    /**
     * True while the app is backgrounded (PlayerActivity.onStop ran, onStart
     * has not). Used to decide whether stopping the service should pause
     * playback (see pauseForServiceStop).
     */
    var isBackgrounded: Boolean = false

    /** Manually picked subtitle for the current video (null = auto .srt / none). */
    private var manualSubtitleUri: Uri? = null

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- Separate sleep timers (v1.1.0): one per mode. Only the current
    // effective mode's timer ticks; switching modes freezes the other
    // timer's countdown (remaining time kept, resumes on return). ---
    private class SleepTimer {
        /** Remaining time when frozen (not counting down). */
        var remainingMs: Long = 0

        /** Deadline when counting down (0 = not running). */
        var endsAtMs: Long = 0
    }

    private val audioSleep = SleepTimer()
    private val videoSleep = SleepTimer()
    private var sleepRunnable: Runnable? = null
    private var sleepRunnableForVideo: Boolean = false

    fun sleepRemainingMs(forVideo: Boolean): Long {
        val t = if (forVideo) videoSleep else audioSleep
        return if (t.endsAtMs > 0) {
            (t.endsAtMs - System.currentTimeMillis()).coerceAtLeast(0)
        } else {
            t.remainingMs
        }
    }

    fun startSleepTimer(minutes: Int, forVideo: Boolean) {
        val t = if (forVideo) videoSleep else audioSleep
        if (sleepRunnable != null && sleepRunnableForVideo == forVideo) {
            mainHandler.removeCallbacks(sleepRunnable!!)
            sleepRunnable = null
        }
        t.remainingMs = (minutes * 60_000L).coerceAtLeast(0)
        t.endsAtMs = 0
        if (minutes <= 0) return
        // Only the current mode's timer ticks; a timer set for the other
        // mode waits frozen until that mode becomes active.
        if (forVideo == isEffectiveVideo) resumeSleepTimer(forVideo)
    }

    fun cancelSleepTimer(forVideo: Boolean) {
        if (sleepRunnable != null && sleepRunnableForVideo == forVideo) {
            mainHandler.removeCallbacks(sleepRunnable!!)
            sleepRunnable = null
        }
        val t = if (forVideo) videoSleep else audioSleep
        t.remainingMs = 0
        t.endsAtMs = 0
    }

    /** Freeze the inactive mode's timer, resume this mode's timer if it has time left. */
    fun setActiveSleepMode(forVideo: Boolean) {
        if (sleepRunnable != null && sleepRunnableForVideo != forVideo) {
            val t = if (sleepRunnableForVideo) videoSleep else audioSleep
            t.remainingMs = (t.endsAtMs - System.currentTimeMillis()).coerceAtLeast(0)
            t.endsAtMs = 0
            mainHandler.removeCallbacks(sleepRunnable!!)
            sleepRunnable = null
        }
        val t = if (forVideo) videoSleep else audioSleep
        if (t.remainingMs > 0 && t.endsAtMs == 0L) resumeSleepTimer(forVideo)
    }

    private fun resumeSleepTimer(forVideo: Boolean) {
        val t = if (forVideo) videoSleep else audioSleep
        if (t.remainingMs <= 0) return
        t.endsAtMs = System.currentTimeMillis() + t.remainingMs
        val runnable = Runnable {
            sleepRunnable = null
            t.remainingMs = 0
            t.endsAtMs = 0
            player?.pause()
            appContext?.let {
                Toast.makeText(it, "Sleep timer: playback paused", Toast.LENGTH_SHORT).show()
            }
        }
        sleepRunnable = runnable
        sleepRunnableForVideo = forVideo
        mainHandler.postDelayed(runnable, t.remainingMs)
    }

    fun getOrCreatePlayer(context: Context): ExoPlayer {
        appContext = context.applicationContext
        player?.let { return it }
        val ctx = context.applicationContext
        val exo = ExoPlayer.Builder(ctx).build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus= */ true
        )
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val oldIndex = currentIndex
                currentIndex = exo.currentMediaItemIndex
                manualSubtitleUri = null
                // A new track starts in its natural mode. Only reset when
                // the index actually changed: the transition also fires on
                // the initial prepare, where play()/playAsAudio already set
                // audioOnly for the starting track.
                if (oldIndex != -1 && oldIndex != currentIndex) audioOnly = false
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(ctx, "This file could not be played", Toast.LENGTH_SHORT).show()
                if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() else exo.stop()
            }
        })
        exo.volume = Prefs.appVolume(ctx) / 100f
        exo.setPlaybackSpeed(Prefs.speed(ctx))
        player = exo
        return exo
    }

    /** Play [tracks] starting at [index]. Queue = the list playback started from. */
    fun play(context: Context, tracks: List<Track>, index: Int, asAudio: Boolean = false) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        val exo = getOrCreatePlayer(context)
        manualSubtitleUri = null
        isBackgrounded = false // a new play() is always user-initiated in the foreground
        audioOnly = asAudio
        queue = tracks
        currentIndex = index
        val items = tracks.map { buildMediaItem(it, null) }
        exo.setMediaItems(items, index, 0L)
        exo.prepare()
        exo.play()
        // The audioOnly setter above ran before the queue was set, so switch
        // the active sleep timer again now that the track is final.
        setActiveSleepMode(isEffectiveVideo)
        val appCtx = context.applicationContext
        if (tracks[index].isVideo && !asAudio) {
            // No notification while watching video; the service is audio-only.
            appCtx.stopService(Intent(appCtx, PlayerService::class.java))
        } else {
            // Background audio: keep the session service alive for the notification.
            ensureServiceRunning(appCtx)
        }
    }

    /** Play a video directly in audio-only mode (long-press menu). */
    fun playAsAudio(context: Context, tracks: List<Track>, index: Int) {
        play(context, tracks, index, asAudio = true)
    }

    /** Start the session service; safe to call when it is already running. */
    fun ensureServiceRunning(context: Context) {
        val appCtx = context.applicationContext
        ContextCompat.startForegroundService(appCtx, Intent(appCtx, PlayerService::class.java))
    }

    /**
     * App backgrounded during playback (PlayerActivity.onStop). Audio always
     * keeps playing. Video keeps playing (audio continues) when the
     * "Background play video" setting is ON, or when the video is in audio
     * mode; otherwise the video pauses (v1.0.x behaviour).
     */
    fun onAppBackgrounded(context: Context) {
        val track = current ?: return
        if (!track.isVideo || audioOnly) return // audio already keeps playing; service running
        if (Prefs.bgVideo(context)) {
            ensureServiceRunning(context)
        } else {
            player?.pause()
        }
    }

    /**
     * Pause when the session service stops (notification dismissed). Audio
     * (and audio-mode video) always pauses. A video in video mode only
     * pauses if the app is backgrounded — stopping the service while
     * watching a video in the foreground must NOT pause it.
     */
    fun pauseForServiceStop() {
        if (!isEffectiveVideo || isBackgrounded) player?.pause()
    }

    fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun next() {
        val exo = player ?: return
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem()
    }

    fun prev() {
        val exo = player ?: return
        if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms.coerceAtLeast(0))
    }

    fun setSpeed(context: Context, speed: Float) {
        Prefs.setSpeed(context, speed)
        player?.setPlaybackSpeed(speed)
    }

    fun setAppVolume(context: Context, percent: Int) {
        Prefs.setAppVolume(context, percent)
        player?.volume = percent / 100f
    }

    fun setShuffle(enabled: Boolean) {
        player?.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        player?.repeatMode = mode
    }

    /** Rebuild the current video item with a manually picked subtitle, keeping position. */
    fun setManualSubtitle(context: Context, uri: Uri?) {
        val exo = player ?: return
        val track = current ?: return
        if (!track.isVideo) return
        manualSubtitleUri = uri
        val pos = exo.currentPosition
        val wasPlaying = exo.isPlaying
        exo.setMediaItem(buildMediaItem(track, uri), pos)
        exo.prepare()
        if (wasPlaying) exo.play()
    }

    private fun buildMediaItem(track: Track, subtitleUri: Uri?): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.key)
            // Title/artist so the notification and lock screen show the song.
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
        if (track.isVideo) {
            val sub = subtitleUri ?: findSidecarSrt(track)
            if (sub != null) {
                builder.setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(sub)
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("und")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    )
                )
            }
        }
        return builder.build()
    }

    /** A same-name .srt next to the video file is loaded automatically. */
    private fun findSidecarSrt(track: Track): Uri? {
        val path = track.dataPath ?: return null
        val base = path.substringBeforeLast('.', path)
        val srt = File("$base.srt")
        return if (srt.exists() && srt.isFile) Uri.fromFile(srt) else null
    }
}
