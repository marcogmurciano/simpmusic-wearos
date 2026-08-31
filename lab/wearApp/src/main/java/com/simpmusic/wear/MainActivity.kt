package com.simpmusic.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import com.simpmusic.wear.playback.PlaybackConnection
import com.simpmusic.wear.ui.WearApp

@UnstableApi
class MainActivity : ComponentActivity() {

    private lateinit var playbackConnection: PlaybackConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Una instancia por conexión: PlayerRepositoryImpl no admite reconectar.
        playbackConnection = PlaybackConnection(this).apply { connect() }

        setContent {
            WearApp(playerRepository = playbackConnection.playerRepository)
        }
    }

    override fun onDestroy() {
        playbackConnection.close()
        super.onDestroy()
    }
}
