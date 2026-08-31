package com.simpmusic.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.simpmusic.wear.music.SearchUiState
import com.simpmusic.wear.music.MusicViewModel
import com.simpmusic.wear.music.WearSong

/**
 * Busqueda de canciones en el reloj.
 *
 * La entrada de texto usa el selector de voz/teclado del sistema Wear OS: en un reloj
 * nadie escribe con teclado, se dicta.
 */
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    onSongClick: (WearSong) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) viewModel.search(spoken)
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ListHeader { Text("Buscar musica") } }

        item {
            Button(
                onClick = { voiceLauncher.launch(voiceSearchIntent()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dictar busqueda") }
        }

        when (val s = state) {
            is SearchUiState.Idle -> item {
                Text(
                    "Pulsa para buscar una cancion",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            is SearchUiState.Loading -> item { CircularProgressIndicator() }
            is SearchUiState.Error -> item {
                Text(
                    s.message,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            is SearchUiState.Results -> items(s.songs) { song ->
                Button(
                    onClick = { onSongClick(song) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${song.title} - ${song.artist}", maxLines = 2)
                }
            }
        }
    }
}

private fun voiceSearchIntent() =
    android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
    }
