# Arquitectura

> Las firmas de este documento se verificaron contra el tag **`v0.7.15`** de `google/horologist` el 2026-08-31. El código Kotlin son **borradores sin compilar** — en la máquina donde se redactó el plan no había JDK ni Android SDK. Sirven para fijar la forma de la solución, no como código listo.

## Grafo de módulos

```
                        ┌─────────────────────┐
                        │      :wearApp       │  ← lo único que se escribe
                        │  wear-compose UI    │
                        │  PlaybackConnection │
                        └──────────┬──────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
     ┌────────▼────────┐  ┌────────▼────────┐  ┌────────▼────────┐
     │   Horologist    │  │  core SimpMusic │  │     Media3      │
     │   0.7.15        │  │  (submódulo)    │  │     1.11.0      │
     │ media-ui        │  │ :domain         │  │ exoplayer       │
     │ media-ui-model  │  │ :data           │  │ session         │
     │ media-data      │  │ :common         │  │                 │
     │ audio-ui        │  │ :kotlinYtmusic… │  │                 │
     │ network-aware…  │  │ :media3         │  │                 │
     └─────────────────┘  └─────────────────┘  └─────────────────┘

Excluidos a propósito (ADR 0004):
  :spotify  :lastfm  :aiService  :lyricsService  :autoEqService
  :cast  :kizzy  :listenTogether  :media-jvm*  :crashlytics
```

## La pieza central: `PlaybackConnection`

Todo el proyecto se apoya en un puente de unas pocas docenas de líneas. El servicio de reproducción de SimpMusic ya existe y funciona; la UI de Horologist ya existe y funciona; lo único que falta es unirlos.

```
   PlayerScreen  (Horologist, hecho)
        │  observa
        ▼
   PlayerViewModel(playerRepository)          ← horologist-media-ui-model
        │
        ▼
   PlayerRepositoryImpl                       ← horologist-media-data
        │  .connect(player, onClose)
        ▼
   MediaController                            ← Media3, implementa Player
        │  IPC
        ▼
   MediaLibraryService de SimpMusic           ← el core, sin tocar
        │
        ▼
   kotlinYtmusicScraper → URL de stream → ExoPlayer
```

### Borrador del puente

```kotlin
// BORRADOR SIN COMPILAR — fija la forma, no es código final
class PlaybackConnection(private val context: Context) {

    val playerRepository = PlayerRepositoryImpl()

    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        val token = SessionToken(
            context,
            ComponentName(context, WearPlaybackService::class.java),
        )
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            // connect() acepta cualquier androidx.media3.common.Player;
            // MediaController lo es.
            playerRepository.connect(f.get()) { MediaController.releaseFuture(f) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun close() {
        playerRepository.close()   // quita el listener, invoca onClose, libera el player
        future?.let(MediaController::releaseFuture)
    }
}
```

**Ciclo de vida — importa.** `PlayerRepositoryImpl.connect` lanza `IllegalStateException("previously connected")` si se llama dos veces sobre la misma instancia, y su código lleva un `// TODO support a cycle of changing players`. Regla: una instancia por conexión, `close()` al soltar, y no reconectar la misma. Atarlo al ciclo de vida del `ViewModel` o de la `Activity`, no a la composición.

## API de Horologist que se consume (verificada en v0.7.15)

### `PlayerRepository` — interfaz que la UI espera
`com.google.android.horologist.media.repository.PlayerRepository`, en el artefacto `horologist-media`. Marcada `@ExperimentalHorologistApi`.

Propiedades, todas `StateFlow`:
`connected: Boolean` · `availableCommands: Set<Command>` · `currentMedia: Media?` · `latestPlaybackState: PlaybackStateEvent` · `shuffleModeEnabled: Boolean` · `seekBackIncrement: Duration?` · `seekForwardIncrement: Duration?`

Métodos: `play()` · `pause()` · `seekToDefaultPosition(mediaIndex)` · `hasPreviousMedia()` · `skipToPreviousMedia()` · `hasNextMedia()` · `skipToNextMedia()` · `seekBack()` · `seekForward()` · `setShuffleModeEnabled(Boolean)` · `setMedia(Media)` · `setMediaList(List<Media>)` · `setMediaList(List<Media>, index, position)` · `addMedia(Media)` · `addMedia(index, Media)` · `removeMedia(index)` · `clearMediaList()` · `getMediaCount()` · `getMediaAt(index)` · `getCurrentMediaIndex()` · `setPlaybackSpeed(Float)`

> **No existen** `prepare()`, `seekToNextMedia()`, `mediaPosition`, `playing` ni `repeatMode`. La posición y el estado viven dentro de `latestPlaybackState`: `PlaybackStateEvent(playbackState, cause, timestamp)` da `createPositionPredictor()`, y `PlaybackState` expone `isPlaying`, `currentPosition`, `duration`, `playbackSpeed`, `isLive`. `play()` hace `prepare()` por dentro si el player está en `STATE_IDLE` — por eso la interfaz no lo necesita.

Esto es relevante para el plan C (implementar la interfaz a mano): son 7 flujos y ~19 métodos, no un par de funciones.

### `PlayerRepositoryImpl` — la implementación que se reutiliza
`horologist-media-data`. Constructor con todo por defecto:

```kotlin
PlayerRepositoryImpl(
    mediaMapper: MediaMapper = MediaMapper(MediaExtrasMapperNoopImpl),
    mediaItemMapper: MediaItemMapper = MediaItemMapper(MediaItemExtrasMapperNoopImpl),
    playbackStateMapper: PlaybackStateMapper = PlaybackStateMapper(),
) : PlayerRepository, Closeable
```

Los *mappers* son el punto de extensión para llevar metadatos propios de SimpMusic (identificadores de YouTube, artista, carátula) a los modelos `Media` de Horologist. Se empieza con los por defecto y solo se personalizan si falta información en la UI.

### `PlayerViewModel` — sin envoltorio propio
`com.google.android.horologist.media.ui.state.PlayerViewModel` (artefacto `horologist-media-ui-model`; ojo, el package es `...ui.state`, no `...ui.model`):

```kotlin
public open class PlayerViewModel(playerRepository: PlayerRepository) : ViewModel() {
    public val playerUiState: StateFlow<PlayerUiState>
    public val playerUiController: PlayerUiController
}
```

Es `open`: si hace falta añadir estado propio (por ejemplo, si la pista está descargada), se hereda en vez de reimplementar.

### `PlayerScreen` — dos variantes
`horologist-media-ui`, package `...media.ui.screens.player`. La versión con estado:

```kotlin
PlayerScreen(
    playerViewModel: PlayerViewModel,
    volumeViewModel: VolumeViewModel,
    modifier: Modifier = Modifier,
    mediaDisplay: MediaDisplay = { DefaultMediaInfoDisplay(it) },
    controlButtons: ControlButtons = { c, s -> DefaultPlayerScreenControlButtons(c, s) },
    buttons: SettingsButtons = { },
    background: PlayerBackground = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
)
```

Y una variante **sin estado**, de slots puros, que recibe composables sueltos. `cassette` usa esta segunda para conservar su propio diseño aprovechando el layout de Horologist — es la vía si el aspecto por defecto no convence.

`horologist-media-ui-material3` ofrece **la misma firma** con componentes Material 3 Expressive, importando `PlayerViewModel`/`PlayerUiState` del mismo package de `ui-model`.

## Dependencias (coordenadas verificadas en Maven Central, todas publicadas en 0.7.15)

Grupo `com.google.android.horologist`:

| Artefacto | Uso |
|---|---|
| `horologist-media` | Interfaz `PlayerRepository` y modelos |
| `horologist-media-data` | `PlayerRepositoryImpl`, `MediaDownloadService` |
| `horologist-media-ui` **o** `horologist-media-ui-material3` | Pantallas |
| `horologist-media-ui-model` | `PlayerViewModel`, `PlayerUiState` |
| `horologist-audio-ui` **o** `horologist-audio-ui-material3` | `VolumeScreen`, salida BT |
| `horologist-compose-layout` | Layout para pantalla redonda, rotary |
| `horologist-network-awareness` | Descargas condicionadas a la red |

> Solo `media-ui` y `audio-ui` tienen variante `-material3`. **No existen** `-material3` de `media`, `media-data`, `media-ui-model`, `compose-layout`, `media3-backend` ni `network-awareness`.

Resto del stack, con las versiones que `cassette` demuestra funcionando juntas:
`androidx.media3:media3-exoplayer|media3-session` **1.10.1–1.11.0** · `androidx.wear.compose:compose-material3|compose-foundation|compose-navigation` **1.6.2** · `androidx.wear:wear-ongoing` 1.1.0 · Room 2.8.4 · Koin (el del core; el ejemplo de Horologist usa Hilt, solo cambia el wiring).

## Configuración del módulo

```kotlin
// wearApp/build.gradle.kts — BORRADOR
android {
    namespace = "com.simpmusic.wear"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.simpmusic.wear"  // ver nota de Data Layer
        minSdk = 30      // Wear OS 3+
        targetSdk = 36
    }
    buildTypes {
        release {
            isMinifyEnabled = true          // solo a partir de F4
            proguardFiles(/* reglas del PR #1864 */)
        }
        debug { isMinifyEnabled = false }   // el bug de R8 no existe aquí
    }
}
```

```xml
<!-- AndroidManifest.xml — imprescindible para que sea app de reloj -->
<uses-feature android:name="android.hardware.type.watch" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<application>
    <meta-data android:name="com.google.android.wearable.standalone" android:value="true" />
    <service android:name=".playback.WearPlaybackService"
             android:foregroundServiceType="mediaPlayback" android:exported="true">
        <intent-filter>
            <action android:name="androidx.media3.session.MediaLibraryService" />
            <action android:name="android.media.browse.MediaBrowserService" />
        </intent-filter>
    </service>
</application>
```

> **Nota sobre `applicationId`:** el login asistido por teléfono usa la Data Layer de Wear, que exige que ambas apps compartan `applicationId` y firma. El fork de Rahyan usa el mismo id que la app de móvil. Si se prefiere un id distinto para poder convivir con SimpMusic instalado, hay que renunciar al relay y usar el fallback de inyectar la sesión por `adb`.

## Disposición de paquetes propuesta

```
wearApp/src/main/java/com/simpmusic/wear/
├── di/                    módulos Koin del reloj
├── playback/
│   ├── WearPlaybackService.kt      envuelve el MediaLibraryService del core
│   └── PlaybackConnection.kt       ← el puente (arriba)
├── ui/
│   ├── WearApp.kt                  SwipeDismissableNavHost
│   ├── player/                     PlayerScreen + VolumeScreen
│   ├── browse/                     biblioteca, playlists, álbumes
│   ├── search/
│   └── downloads/
├── data/
│   └── DownloadModule.kt           MediaDownloadService + network rules
└── auth/
    └── WearDataLayerListenerService.kt   relay de login (portado de Rahyan)
```

## Flujo de datos

**Reproducir una canción de la biblioteca**

1. `BrowseScreen` (Horologist) muestra el catálogo obtenido de `:domain`.
2. Al tocar, un mapper propio convierte la entidad de SimpMusic en `Media` de Horologist.
3. `playerUiController.setMediaList(...)` o `setMedia(...)` → `PlayerRepositoryImpl` → `MediaController` → IPC → servicio.
4. El servicio usa el pipeline del core: `kotlinYtmusicScraper` resuelve la URL, ExoPlayer reproduce.
5. El estado vuelve por el `Player.Listener` que `PlayerRepositoryImpl` instaló → `latestPlaybackState` → `PlayerUiState` → recomposición de `PlayerScreen`.

Los pasos 4 y 5 son código existente y probado. Lo nuevo es el 2 y el cableado del 3.

## Supuestos a validar en F1

Ordenados por riesgo, con el criterio de fallo explícito:

1. **El scraper resuelve URLs desde la red del reloj.** Si YouTube trata distinto esas peticiones, cae el streaming y el plan pivota a offline-first. *Probar el primer día.*
2. **`PlayerRepositoryImpl.connect(mediaController)` produce estado correcto.** Ninguna app conocida hace exactamente esto (ADR 0003). Si los mappers por defecto pierden metadatos, se personalizan; si el modelo no encaja, plan C.
3. **Media3 1.11.0 del core convive con `horologist-media-data` 0.7.15.** Si choca, bajar a 1.10.1 como `cassette`.
4. **El core cabe en memoria.** Se mide en F4, pero si en F1 ya hay presión de memoria, adelantar el recorte de módulos.
