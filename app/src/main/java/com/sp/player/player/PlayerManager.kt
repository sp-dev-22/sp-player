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

    /** Manually picked subtitle for the current video (null = auto .srt / none). */
    private var manualSubtitleUri: Uri? = null

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null
    private var sleepEndsAtMs: Long = 0

    val sleepRemainingMs: Long
        get() {
            if (sleepRunnable == null) return 0
            return (sleepEndsAtMs - System.currentTimeMillis()).coerceAtLeast(0)
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
                currentIndex = exo.currentMediaItemIndex
                manualSubtitleUri = null
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(ctx, "यह फ़ाइल नहीं चल पाई", Toast.LENGTH_SHORT).show()
                if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() else exo.stop()
            }
        })
        exo.volume = Prefs.appVolume(ctx) / 100f
        exo.setPlaybackSpeed(Prefs.speed(ctx))
        player = exo
        return exo
    }

    /** Play [tracks] starting at [index]. Queue = the list playback started from. */
    fun play(context: Context, tracks: List<Track>, index: Int) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        val exo = getOrCreatePlayer(context)
        manualSubtitleUri = null
        queue = tracks
        currentIndex = index
        val items = tracks.map { buildMediaItem(it, null) }
        exo.setMediaItems(items, index, 0L)
        exo.prepare()
        exo.play()
        val appCtx = context.applicationContext
        if (tracks[index].isVideo) {
            // No notification while watching video; the service is audio-only.
            appCtx.stopService(Intent(appCtx, PlayerService::class.java))
        } else {
            // Background audio: keep the session service alive for the notification.
            ContextCompat.startForegroundService(appCtx, Intent(appCtx, PlayerService::class.java))
        }
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

    /** Video pauses when the app backgrounds (battery). Audio keeps playing. */
    fun pauseIfVideo() {
        if (current?.isVideo == true) player?.pause()
    }

    /** Pause audio (used when the notification is dismissed / service stops). */
    fun pauseIfAudio() {
        if (current?.isVideo == false) player?.pause()
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

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        sleepEndsAtMs = System.currentTimeMillis() + minutes * 60_000L
        val runnable = Runnable {
            sleepRunnable = null
            player?.pause()
            appContext?.let {
                Toast.makeText(it, "Sleep timer: प्लेबैक रुक गया", Toast.LENGTH_SHORT).show()
            }
        }
        sleepRunnable = runnable
        mainHandler.postDelayed(runnable, minutes * 60_000L)
    }

    fun cancelSleepTimer() {
        sleepRunnable?.let { mainHandler.removeCallbacks(it) }
        sleepRunnable = null
        sleepEndsAtMs = 0
    }

    private fun buildMediaItem(track: Track, subtitleUri: Uri?): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.key)
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
