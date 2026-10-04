package com.musicmr.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

object PlayerHolder {
    private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player = p
        return p
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publish()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            publish()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            publish()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val p = PlayerHolder.get(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val s = MediaSession.Builder(this, p).setSessionActivity(open).build()
        session = s
        addSession(s)
        p.addListener(listener)
        publish()
    }

    private fun publish() {
        val p = PlayerHolder.get(this)
        val md = p.currentMediaItem?.mediaMetadata
        WidgetPrefs.saveNow(
            this,
            md?.title?.toString().orEmpty(),
            md?.artist?.toString().orEmpty(),
            md?.artworkUri?.toString().orEmpty(),
            p.isPlaying
        )
        val app = applicationContext
        Thread { WidgetUpdater.updateAll(app) }.start()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        PlayerHolder.get(this).removeListener(listener)
        WidgetPrefs.setPlaying(this, false)
        val app = applicationContext
        Thread { WidgetUpdater.updateAll(app) }.start()
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
