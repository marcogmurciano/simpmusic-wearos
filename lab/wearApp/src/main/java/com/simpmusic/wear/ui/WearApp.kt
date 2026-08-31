package com.simpmusic.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.simpmusic.wear.library.LibraryViewModel
import com.simpmusic.wear.library.WearPlaylist
import com.simpmusic.wear.music.MusicViewModel
import com.simpmusic.wear.music.WearSong

private object Rutas {
    const val INICIO = "inicio"
    const val BUSCAR = "buscar"
    const val BIBLIOTECA = "biblioteca"
    const val PLAYLIST = "playlist"
    const val DESCARGAS = "descargas"
    const val REPRODUCTOR = "reproductor"
}

/**
 * Raiz de la UI del reloj.
 *
 * SwipeDismissableNavHost da el gesto nativo de Wear OS (deslizar desde el borde
 * izquierdo para volver), en vez de un boton de atras que un reloj no tiene.
 */
@UnstableApi
@Composable
fun WearApp(playerRepository: PlayerRepository, consultaInicial: String? = null) {
    val nav = rememberSwipeDismissableNavController()
    val musicViewModel: MusicViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val playerViewModel = remember { PlayerViewModel(playerRepository) }
    val connected by playerRepository.connected.collectAsStateWithLifecycle()

    // La playlist abierta se guarda aqui: pasar objetos por argumentos de ruta
    // obligaria a serializarla entera en la URL.
    var playlistAbierta by remember { mutableStateOf<WearPlaylist?>(null) }

    LaunchedEffect(consultaInicial) {
        if (!consultaInicial.isNullOrBlank()) {
            musicViewModel.search(consultaInicial)
            nav.navigate(Rutas.BUSCAR)
        }
    }

    /** Reproduce: usa el fichero local si esta descargada, y si no el stream. */
    fun reproducir(song: WearSong) {
        if (!connected) return
        val offline = libraryViewModel.uriOffline(song.videoId)
        if (offline != null) {
            playerRepository.setMedia(
                Media(song.videoId, offline, song.title, song.artist, artworkUri = song.thumbnail),
            )
            playerRepository.play()
            nav.navigate(Rutas.REPRODUCTOR)
        } else {
            musicViewModel.resolveStream(
                song = song,
                onResolved = { s, url ->
                    playerRepository.setMedia(
                        Media(s.videoId, url, s.title, s.artist, artworkUri = s.thumbnail),
                    )
                    playerRepository.play()
                    nav.navigate(Rutas.REPRODUCTOR)
                },
                onError = { },
            )
        }
    }

    SwipeDismissableNavHost(navController = nav, startDestination = Rutas.INICIO) {
        composable(Rutas.INICIO) {
            HomeScreen(
                onBuscar = { nav.navigate(Rutas.BUSCAR) },
                onBiblioteca = { nav.navigate(Rutas.BIBLIOTECA) },
                onDescargas = { nav.navigate(Rutas.DESCARGAS) },
            )
        }
        composable(Rutas.BUSCAR) {
            SearchScreen(viewModel = musicViewModel, onSongClick = ::reproducir)
        }
        composable(Rutas.BIBLIOTECA) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onPlaylist = { playlistAbierta = it; nav.navigate(Rutas.PLAYLIST) },
            )
        }
        composable(Rutas.PLAYLIST) {
            playlistAbierta?.let { pl ->
                PlaylistScreen(playlist = pl, viewModel = libraryViewModel, onReproducir = ::reproducir)
            }
        }
        composable(Rutas.DESCARGAS) {
            DownloadsScreen(viewModel = libraryViewModel, onReproducir = ::reproducir)
        }
        composable(Rutas.REPRODUCTOR) {
            PlayerScreen(
                playerViewModel = playerViewModel,
                volumeViewModel = viewModel(factory = VolumeViewModel.Factory),
            )
        }
    }
}
