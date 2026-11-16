package com.sp.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sp.player.player.PlayerManager

/**
 * MediaSessionService for background audio: owns the MediaSession and the
 * notification. It never releases the player (PlayerManager owns it for the
 * process lifetime), so stopping the service never kills video playback.
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerManager.getOrCreatePlayer(this)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // Notification dismissed / service stopped: stop background audio, keep the
        // player and queue so the app can resume from the mini-player.
        PlayerManager.pauseIfAudio()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
