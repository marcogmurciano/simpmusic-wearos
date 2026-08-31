package com.simpmusic.wear.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Servicio de reproducción del reloj.
 *
 * F1: ExoPlayer propio para validar el camino completo (servicio -> MediaController ->
 * PlayerRepositoryImpl -> PlayerScreen) con audio real.
 *
 * F2 sustituirá este player por el pipeline de SimpMusic (:media3 del core, que resuelve
 * las URLs vía kotlinYtmusicScraper). El resto de la cadena no cambia: la UI habla con
 * el servicio por MediaController, así que da igual qué Player haya detrás.
 */
@OptIn(UnstableApi::class)
class WearPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // En un reloj el audio sale por Bluetooth; que no siga sonando si se va la salida.
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
