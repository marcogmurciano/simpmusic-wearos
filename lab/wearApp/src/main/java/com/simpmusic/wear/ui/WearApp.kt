package com.simpmusic.wear.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.google.android.horologist.media.model.Media
import com.google.android.horologist.media.repository.PlayerRepository
import com.google.android.horologist.media.ui.screens.player.PlayerScreen
import com.google.android.horologist.media.ui.state.PlayerViewModel

/**
 * Raiz de la UI del reloj.
 *
 * PlayerScreen, sus controles y el mapeo de estado los pone Horologist: aqui solo se le
 * entrega el PlayerRepository que el puente ha conectado al servicio.
 */
@UnstableApi
@Composable
fun WearApp(playerRepository: PlayerRepository) {
    val playerViewModel = remember { PlayerViewModel(playerRepository) }

    // F1: pista fija para validar el camino completo servicio -> controller -> UI -> audio.
    // F2 la sustituye por la biblioteca real via :domain.
    //
    // OJO: connect() es asincrono. Cargar la pista antes de que el MediaController este
    // conectado es un no-op silencioso; hay que esperar a connected == true.
    val connected by playerRepository.connected.collectAsStateWithLifecycle()
    LaunchedEffect(connected) {
        Log.i("WearPlayback", "LaunchedEffect connected=$connected")
        if (!connected) return@LaunchedEffect
        playerRepository.setMedia(
            Media(
                id = "test-tone",
                uri = "android.resource://com.simpmusic.wear/raw/test_tone",
                title = "Tono de prueba",
                artist = "SimpMusic Wear",
            ),
        )
        Log.i("WearPlayback", "setMedia llamado; currentMedia=${playerRepository.currentMedia.value}")
    }

    // VolumeViewModel no tiene constructor sin argumentos: necesita su factoria.
    // (Descubierto ejecutando en el emulador: NoSuchMethodException en <init>.)
    PlayerScreen(
        playerViewModel = playerViewModel,
        volumeViewModel = viewModel(factory = VolumeViewModel.Factory),
    )
}
