package dev.alexis.wirelessdrive.playback

import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.alexis.wirelessdrive.WirelessDriveApplication

@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()

        val container = (application as WirelessDriveApplication).container
        val tokenManager = container.tokenManager
        val apiService = container.apiService

        val authenticatingFactory = AuthenticatingHttpDataSourceFactory(tokenManager)
        val lazyResolvingFactory = LazyResolvingDataSourceFactory(apiService, authenticatingFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(lazyResolvingFactory))
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
            }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val queueExhausted = playbackState == Player.STATE_IDLE && player.mediaItemCount == 0
                if (playbackState == Player.STATE_ENDED || queueExhausted) {
                    stopSelf()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}