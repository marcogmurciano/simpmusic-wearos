# Referencias verificadas

Todo lo de esta página se comprobó consultando la fuente el **2026-08-31**. Lo que no se pudo verificar está marcado como tal, en vez de rellenado a ojo.

## Fuentes de código reutilizable

### SimpMusic (upstream)
- Repo: <https://github.com/maxrave-dev/SimpMusic> — GPL-3.0, Kotlin, ~10.8k estrellas, activo (commits de agosto 2026).
- Core como submódulo git: <https://github.com/maxrave-dev/core>
- Módulos declarados en `settings.gradle.kts` (verificado leyendo el fichero): `androidApp`, `composeApp`, `desktopApp`, `common`, `data`, `domain`, `ktorExt`, `kotlinYtmusicScraper`, `spotify`, `aiService`, `autoEqService`, `lyricsService`, `media-jvm`, `media-jvm-ui`, `media3`, `media3-ui`, `crashlytics(-empty)`, `cast(-empty)`, `lastfm(-empty)`, `kizzy`, `listenTogether`.
- Stack: Media3 **1.11.0** (`MediaLibraryService`, `DelegatingForwardingPlayer`), Room 2.8.4, DataStore, Koin BOM 4.2.2, Kotlin 2.4.10, Compose Multiplatform 1.11.1. minSdk 26.
- Extracción de YouTube: módulo propio `kotlinYtmusicScraper`, **Kotlin nativo sobre OkHttp** — sin NewPipeExtractor y sin motor JS (Rhino). Esto es lo que hace el port ligero.

### Fork con módulo Wear ya escrito — la cantera
- Repo: <https://github.com/Rahyan/SimpMusic-WearOS> — rama `main`, último push **2026-02-24**, descripción "A simple WearOS music app using YouTube Music for backend. WIP." GPL-3.0.
- Contiene `wearApp/`: **50 ficheros, ~470 KB de Kotlin**. Pantallas `NowPlayingScreen` (92 KB), `SearchScreen`, `DiscoverScreen`, `LibraryScreen`, `DownloadsScreen`, `DownloadedCollectionsScreen`, `QueueScreen`, `VolumeScreen`, `LoginScreen`/`AccountsScreen`, álbumes/artistas/playlists/podcasts. Además `SimpMusicTileService` (tile) y `PlaybackStatusComplicationService` (complicación).
- Login asistido desde el teléfono: `WearDataLayerListenerService` (31 KB) en el reloj + `wearbridge/WearLoginRelayActivity` en el lado móvil.
- Stack del módulo: wear-compose material3 + foundation + navigation, **sin Horologist**; compileSdk/targetSdk 36, minSdk 26, Koin, Coil, tiles/protolayout, play-services-wearable. Manifest con `com.google.android.wearable.standalone=true`.
- **Advertencia:** está construido sobre una reestructuración propia del repo (`androidApp/`→`app/`, paquetes movidos, target desktop eliminado). Es cantera de patrones y pantallas, **no un merge directo**.

### PR #1864 — las reglas ProGuard
- <https://github.com/maxrave-dev/SimpMusic/pull/1864> — "feat: Wear OS Support + Possible Release Fix (R8/ProGuard)", autor bguerraDev, cerrado. El mantenedor respondió *"Rollback it"* y *"Make PR to Core"*.
- Diagnóstico: R8 en release eliminaba `artworkData`, rompiendo carátulas y metadatos en la sesión de medios; en debug funcionaba.
- Aporta **+82 líneas en `proguard-rules.pro`**: `-keep` de `DelegatingForwardingPlayer` y `CrossfadeExoPlayerAdapter`, `-keepclassmembers` de `GenericMediaMetadata`/`GenericMediaItem`, del campo `player` de `androidx.media3.common.ForwardingPlayer`, de los callbacks `onMediaItemTransition`/`onMediaMetadataChanged`/`onAvailableCommandsChanged`/`onVolumeChanged` en implementadores de `Player$Listener`, de `MediaMetadata.artworkData`/`artworkUri`, de `com.maxrave.media3.session.**` y de los callbacks de `MediaSession$Callback`/`MediaLibrarySession$Callback`; más keeps de coroutines y `-dontwarn coil.**`.
- Los PRs #1732 y #1733 (autor Rahyan) son el mismo mega-diff de 928 ficheros, cerrados por el propio autor con *"Mistake on my end"* — los abrió contra upstream por error. No aportan nada que no esté en su fork.

## Horologist — el toolkit de medios

- Docs: <https://google.github.io/horologist/media-toolkit/> · Repo: <https://github.com/google/horologist>
- **Versión estable a usar: 0.7.15.** Existe `0.8.4-alpha` (agosto 2026) pero sin changelog útil; las apps reales usan la 0.7.15.
- La rama `main` pina `androidx.wear.compose:*` a **1.6.2** — hay soporte material3 con artefactos dedicados `horologist-media-ui-material3` y `horologist-audio-ui-material3`.

| Artefacto (`com.google.android.horologist:`) | Contenido |
|---|---|
| `horologist-media` | Dominio agnóstico del player: interfaz `PlayerRepository`, `MediaDownloadRepository`, `PlaylistDownloadRepository`, modelos `MediaDownload`/`PlaylistDownload` |
| `horologist-media-data` | Implementación sobre Media3: `PlayerRepositoryImpl`, `MediaDownloadService`, `DownloadManagerListener`, `DownloadProgressMonitor`, DAO Room `MediaDownloadDao` |
| `horologist-media3-backend` | Player Media3 adaptado a reloj (evita el altavoz interno), `NetworkAwareDownloadListener` |
| `horologist-media-ui` / `-material3` | Pantallas hechas: `PlayerScreen`, `BrowseScreen`, `EntityScreen`, `PlaylistDownloadScreen`, `PlaylistDownloadBrowseScreen` |
| `horologist-media-ui-model` | `PlayerViewModel` y mappers de estado a UI |
| `horologist-audio` / `horologist-audio-ui[-material3]` | Volumen por corona y cambio de salida BT: `VolumeScreen` |
| `horologist-network-awareness[-ui/-okhttp/-db]` | Reglas de red (descargas solo por wifi, etc.) |
| `horologist-compose-layout` | Utilidades de layout para pantalla redonda |

### App de ejemplo del toolkit
`media/sample` en el repo (paquete `com.google.android.horologist.mediasample`), estilo UAMP, con Hilt. Incluye lo que interesa copiar: `data/service/playback/PlaybackService.kt` + `UampMediaLibrarySessionCallback` (MediaLibraryService de Media3), `data/service/download/MediaDownloadServiceImpl.kt`, `di/DownloadModule.kt`, `di/config/UampNetworkingRules.kt`, offload de audio (`AudioOffloadManager`), tile (`MediaCollectionsTileService`) y complicación (`MediaStatusComplicationService`).

## App FOSS de referencia — arquitectura funcionando

**[AdamNiederer/cassette](https://github.com/AdamNiederer/cassette)** — AGPL-3.0, player local standalone para Wear, push agosto 2026. Es la demostración de la arquitectura propuesta en producción:
- Horologist **0.7.15** (`horologist-media-ui`, `horologist-media-data`, `horologist-audio-ui`, `compose-layout`) + Media3 **1.10.1** + wear-compose material/material3/foundation **1.6.2** + Hilt + Room + Paging.
- minSdk 34, targetSdk 37, `useLibrary("wear-sdk")`.
- Estructura: `data/repositories/PlayerRepository.kt`, `playback/PlaybackService.kt`, `presentation/views/{PlayerView,LibraryView,QueueView,LyricsView}`, `presentation/viewmodels/PlayerViewModel.kt`, más tile y complicación.
- **Ante cualquier duda de cableado entre Media3 y la UI de Horologist, mirar aquí primero.**

**[Windkracht8/WearMusicPlayer](https://github.com/Windkracht8/WearMusicPlayer)** — GPL-3.0, dos módulos `wear/` + `mobile/`. Media3 1.9.3 + wear-compose-material3 1.5.6 + solo `horologist-compose-layout` (no usa el media toolkit); player propio. Útil como referencia de manifest, permisos (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `wear-ongoing`) y de app companion.

Otras vistas sin inspeccionar a fondo: `celestialtaha/Weavelet`, `fimbulent/jellyfin-wearos`, `RTekaya/RTM` (reconstrucción KMP de SimpMusic que menciona Wear pero **sin módulo wear materializado** en su árbol a fecha de consulta).

> No existe ningún cliente de YouTube Music para Wear OS open source y funcional aparte del fork de Rahyan.

## Plataforma Wear OS

| Wear OS | Android | API | Fuente |
|---|---|---|---|
| 6 | 16 | **36** | [versions/6/setup](https://developer.android.com/training/wearables/versions/6/setup) |
| 5.1 | 15 | **35** | [versions/5-1](https://developer.android.com/training/wearables/versions/5-1) |
| 5 | 14 | **34** | [versions/5-1](https://developer.android.com/training/wearables/versions/5-1) |
| 4 | 13 | 33 | *No verificado en página oficial directa* |

- **targetSdk:** desde el **31-08-2026** Google Play exige API **35+** para apps Wear nuevas y actualizaciones ([requisitos de target API](https://developer.android.com/google/play/requirements/target-sdk)). Irrelevante para distribución por sideload, pero se cumple igualmente.
- **minSdk:** la guía oficial ([creating](https://developer.android.com/training/wearables/get-started/creating)) no fija un número; recomienda los defaults del template *Empty Wear App* (standalone, Compose for Wear OS, `TransformingLazyColumn`, `SwipeDismissableNavHost`). En la práctica: cassette usa 34, WearMusicPlayer 30, el fork de Rahyan 26. **30 es el suelo razonable hoy** (Wear OS 3+).
- **Compose Multiplatform no tiene target de Wear OS.** La UI del reloj es `androidx.wear.compose` obligatoriamente — este es el motivo por el que hay que escribir UI nueva en vez de reutilizar `composeApp`.

## Depuración (documentación oficial)

[Debug a Wear OS app](https://developer.android.com/training/wearables/get-started/debugging):
- Emulador Wear OS con **Wear OS pairing assistant** para emparejar con teléfono físico o virtual.
- **Depuración por Bluetooth ya no está soportada desde Wear OS 3**; la vía inalámbrica es Wi-Fi.
- USB solo en relojes cuya cuna de carga transporte datos.

## Instalación en el reloj

- **Sin ADB:** apps de Wear OS que levantan un servidor web local y usan el PackageInstaller nativo — *Wear APK Install*, *AnExplorer*, *WearLoad*. Se instalan desde la Play Store del reloj.
- **Con ADB desde el móvil:** [Wear Installer 2](https://freepoc.org/wear-installer-2-help-page/) lleva su propio ADB embebido; requiere depuración inalámbrica activada.
- **Aurora Store no sirve:** es un cliente de Play Store y SimpMusic no está (ni puede estar) en ese catálogo.
- **F-Droid no sirve:** no existe cliente de F-Droid para Wear OS.
- **Cable:** Pixel Watch 2 y 3 sí (el cargador USB-C lleva datos); Pixel Watch 1 requiere modificar el cable; Pixel Watch 4 no; Galaxy Watch con carga Qi es imposible.
