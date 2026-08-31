package com.simpmusic.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.simpmusic.wear.library.LibraryUiState
import com.simpmusic.wear.library.LibraryViewModel
import com.simpmusic.wear.library.WearPlaylist
import com.simpmusic.wear.music.CuentaPlaylist
import com.simpmusic.wear.music.WearSong

/**
 * Menu raiz del reloj.
 *
 * [sonando] es el titulo de lo que se este reproduciendo, o null si no hay nada. Cuando
 * hay algo, aparece un acceso permanente al reproductor: sin el habria que volver a
 * navegar hasta la pantalla de origen solo para pausar.
 */
@Composable
fun HomeScreen(
    onBuscar: () -> Unit,
    onBiblioteca: () -> Unit,
    onDescargas: () -> Unit,
    sonando: String? = null,
    onReproductor: () -> Unit = {},
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ListHeader { Text("SimpMusic") } }
        item { Button(onClick = onBuscar, modifier = Modifier.fillMaxWidth()) { Text("Buscar") } }
        item { Button(onClick = onBiblioteca, modifier = Modifier.fillMaxWidth()) { Text("Mis playlists") } }
        item { Button(onClick = onDescargas, modifier = Modifier.fillMaxWidth()) { Text("Descargas") } }

        if (sonando != null) {
            item { ListHeader { Text("Sonando") } }
            item {
                Button(onClick = onReproductor, modifier = Modifier.fillMaxWidth()) {
                    Text(sonando, maxLines = 2)
                }
            }
        }
    }
}

/** Playlists importadas del backup del movil. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlaylist: (WearPlaylist) -> Unit,
    onPlaylistCuenta: (CuentaPlaylist) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ListHeader { Text("Mis playlists") } }

        when (val s = state) {
            is LibraryUiState.SinBackup -> {
                item {
                    Text(
                        "Importa el backup que exportaste desde el movil",
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Button(
                        onClick = { viewModel.importar() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Importar backup") }
                }
            }
            is LibraryUiState.Cargando -> item { CircularProgressIndicator() }
            is LibraryUiState.Error -> {
                item {
                    Text(
                        s.mensaje,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Button(
                        onClick = { viewModel.importar() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reintentar") }
                }
            }
            is LibraryUiState.Listo -> {
                // Primero las de la cuenta: vienen del servidor y estan siempre al dia.
                if (s.deLaCuenta.isNotEmpty()) {
                    item { ListHeader { Text("De tu cuenta") } }
                    items(s.deLaCuenta) { pl ->
                        Button(onClick = { onPlaylistCuenta(pl) }, modifier = Modifier.fillMaxWidth()) {
                            Text(pl.title, maxLines = 2)
                        }
                    }
                }
                if (s.locales.isNotEmpty()) {
                    item { ListHeader { Text("Del backup") } }
                    items(s.locales) { pl ->
                        Button(onClick = { onPlaylist(pl) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${pl.title} (${pl.songs.size})", maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

/** Canciones de una playlist, con descarga individual o en bloque. */
@Composable
fun PlaylistScreen(
    playlist: WearPlaylist,
    viewModel: LibraryViewModel,
    onReproducir: (WearSong) -> Unit,
) {
    val descargadas by viewModel.descargadas.collectAsStateWithLifecycle()
    val descargando by viewModel.descargando.collectAsStateWithLifecycle()
    val idsDescargados = descargadas.map { it.videoId }.toSet()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ListHeader { Text(playlist.title) } }
        item {
            Button(
                onClick = { viewModel.descargarTodas(playlist) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Descargar todas") }
        }
        items(playlist.songs) { song ->
            val marca = when {
                song.videoId in descargando -> "... "
                song.videoId in idsDescargados -> "[OK] "
                else -> ""
            }
            Button(
                onClick = {
                    if (song.videoId in idsDescargados) onReproducir(song)
                    else viewModel.descargar(song)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("$marca${song.title}", maxLines = 2) }
        }
    }
}

/** Lo que suena sin cobertura. */
@Composable
fun DownloadsScreen(viewModel: LibraryViewModel, onReproducir: (WearSong) -> Unit) {
    val descargadas by viewModel.descargadas.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ListHeader { Text("Descargas") } }
        if (descargadas.isEmpty()) {
            item {
                Text(
                    "Nada descargado todavia.\nDescarga desde una playlist para escuchar sin cobertura.",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(descargadas) { song ->
                Button(onClick = { onReproducir(song) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${song.title} - ${song.artist}", maxLines = 2)
                }
            }
        }
    }
}
