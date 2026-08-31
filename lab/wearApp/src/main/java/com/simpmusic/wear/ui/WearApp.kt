package com.simpmusic.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.google.android.horologist.media.model.Media
import com.google.android.horologist.media.repository.PlayerRepository
import com.google.android.horologist.media.ui.screens.player.PlayerScreen
import com.google.android.horologist.media.ui.state.PlayerViewModel
import com.simpmusic.wear.music.MusicViewModel

private object Rutas {
    const val BUSCAR = "buscar"
    const val REPRODUCTOR = "reproductor"
}

/**
 * Raiz de la UI del reloj: buscar -> reproducir.
 *
 * SwipeDismissableNavHost da el gesto nativo de Wear OS (deslizar desde el borde
 * izquierdo para volver), en vez de un boton de atras que un reloj no tiene.
 */
@UnstableApi
@Composable
fun WearApp(playerRepository: PlayerRepository, consultaInicial: String? = null) {
    val navController = rememberSwipeDismissableNavController()
    val musicViewModel: MusicViewModel = viewModel()
    val playerViewModel = remember { PlayerViewModel(playerRepository) }
    val connected by playerRepository.connected.collectAsStateWithLifecycle()

    LaunchedEffect(consultaInicial) {
        if (!consultaInicial.isNullOrBlank()) musicViewModel.search(consultaInicial)
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Rutas.BUSCAR,
    ) {
        composable(Rutas.BUSCAR) {
            SearchScreen(
                viewModel = musicViewModel,
                onSongClick = { song ->
                    // connect() es asincrono: sin conexion, setMedia seria un no-op silencioso.
                    if (!connected) return@SearchScreen
                    musicViewModel.resolveStream(
                        song = song,
                        onResolved = { resuelta, url ->
                            playerRepository.setMedia(
                                Media(
                                    id = resuelta.videoId,
                                    uri = url,
                                    title = resuelta.title,
                                    artist = resuelta.artist,
                                    artworkUri = resuelta.thumbnail,
                                ),
                            )
                            playerRepository.play()
                            navController.navigate(Rutas.REPRODUCTOR)
                        },
                        onError = { /* el estado de error ya se muestra en la lista */ },
                    )
                },
            )
        }

        composable(Rutas.REPRODUCTOR) {
            PlayerScreen(
                playerViewModel = playerViewModel,
                volumeViewModel = viewModel(factory = VolumeViewModel.Factory),
            )
        }
    }
}
