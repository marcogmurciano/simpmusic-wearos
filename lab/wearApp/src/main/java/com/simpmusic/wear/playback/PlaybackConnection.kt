package com.simpmusic.wear.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.horologist.media.data.repository.PlayerRepositoryImpl
import com.google.common.util.concurrent.ListenableFuture

/**
 * El puente del proyecto (ADR 0003).
 *
 * Une el servicio de reproducción con la UI ya hecha de Horologist. Funciona porque
 * `MediaController` implementa `androidx.media3.common.Player`, que es exactamente lo que
 * `PlayerRepositoryImpl.connect(player, onClose)` acepta.
 *
 * Ciclo de vida: `connect()` lanza IllegalStateException si se llama dos veces sobre la
 * misma instancia (Horologist tiene un TODO al respecto). Una instancia por conexión.
 */
@UnstableApi
class PlaybackConnection(private val context: Context) {

    private companion object { const val TAG = "WearPlayback" }

    val playerRepository: PlayerRepositoryImpl = PlayerRepositoryImpl()

    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun connect() {
        check(controllerFuture == null) { "PlaybackConnection ya conectada" }

        val token = SessionToken(
            context,
            ComponentName(context, WearPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        Log.i(TAG, "MediaController conectado: isConnected=${controller.isConnected}")
                        playerRepository.connect(controller) {
                            MediaController.releaseFuture(future)
                        }
                        Log.i(TAG, "repo.connected=${playerRepository.connected.value}")
                    }
                    .onFailure { Log.e(TAG, "fallo al conectar MediaController", it) }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun close() {
        playerRepository.close()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }
}
