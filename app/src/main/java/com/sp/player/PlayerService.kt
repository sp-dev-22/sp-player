package com.sp.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sp.player.player.PlayerManager

/**
 * MediaSessionService for background audio: owns the MediaSession and the
 * notification. It never releases the player (PlayerManager owns it for the
 * process lifetime), so stopping the service never kills video playback.
 *
 * v1.0.0 crash (fixed here): the service was started with
 * startForegroundService, which gives it ~5 seconds to call startForeground —
 * but the session was never added via addSession(), so Media3's notification
 * manager never posted the media notification at all and Android killed the
 * app ~5s after audio play started, every time. Two fixes:
 *  1. addSession() in onCreate (removeSession in onDestroy) so the media
 *     notification is actually managed.
 *  2. A placeholder notification via startForeground() immediately in
 *     onCreate, so the 5s deadline is met even before Media3's async
 *     controller connects. It uses Media3's own notification id (1001), so
 *     the real media notification replaces it in place — no duplicate.
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundPlaceholder()
        val player = PlayerManager.getOrCreatePlayer(this)
        mediaSession = MediaSession.Builder(this, player).build()
        mediaSession?.let { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // Notification dismissed / service stopped: stop background audio, keep the
        // player and queue so the app can resume from the mini-player.
        PlayerManager.pauseIfAudio()
        mediaSession?.let { removeSession(it) }
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun startForegroundPlaceholder() {
        val channelId = "sp_player_playback"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("SP Player")
            .setContentText("Audio chal raha hai")
            .setOngoing(true)
            .build()
        startForeground(
            DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
            notification
        )
    }
}
