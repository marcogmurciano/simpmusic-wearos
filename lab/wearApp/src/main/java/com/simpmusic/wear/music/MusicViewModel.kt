package com.simpmusic.wear.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val songs: List<WearSong>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class MusicViewModel(
    private val source: MusicSource = MusicSource(),
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        _state.value = SearchUiState.Loading
        viewModelScope.launch {
            _state.value = source.search(query).fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) SearchUiState.Error("Sin resultados")
                    else SearchUiState.Results(songs)
                },
                onFailure = { SearchUiState.Error(it.message ?: "Error de red") },
            )
        }
    }

    /** Resuelve la URL de audio y la entrega al llamante para cargarla en el reproductor. */
    fun resolveStream(song: WearSong, onResolved: (WearSong, String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            source.streamUrl(song.videoId).fold(
                onSuccess = { onResolved(song, it) },
                onFailure = { onError(it.message ?: "No se pudo reproducir") },
            )
        }
    }
}
