# Laboratorio: contenedor con el producto compilado y probado

Contenedor Docker con el toolchain completo (JDK 21, Android SDK 37, Gradle 9.5.1, emuladores de Wear OS acelerados por KVM) que **compila un APK de reloj y lo ejecuta**.

## Qué se validó de verdad, ejecutando

| Verificación | Resultado |
|---|---|
| **Busca en YouTube Music** (sin login) | ✅ "daft punk" devuelve resultados reales |
| **Reproduce streaming real** | ✅ Opus itag 251, `state=PLAYING`, posición avanzando |
| **Importa las playlists del móvil** | ✅ 2 playlists leídas del backup, sin tocar la app del móvil |
| **Descarga para offline** | ✅ 5/5 canciones, 26 MB en el reloj |
| **Reproduce EN MODO AVIÓN** | ✅ `state=PLAYING`, posición 6,4s → 9,3s sin red |
| Importa la sesión del backup | ✅ cookie leída del `preferences_pb` con parser propio |
| **Release ofuscado con R8** | ✅ 191 MB → 93 MB, búsqueda y metadatos correctos |
| Memoria bajo reproducción | ✅ **58 MB de PSS** (holgado en un reloj de 2 GB) |
| Arranque en frío | ✅ 873 ms |
| **Tile en la esfera** | ✅ "Escuchar — 2 canciones sin conexion", con el dato real |
| El core de SimpMusic compila (`:media3`) | ✅ `BUILD SUCCESSFUL in 4m 27s` |
| El módulo `:wearApp` produce APK | ✅ `wearApp-debug.apk`, 81 MB |
| Es una app de reloj de verdad | ✅ `uses-feature: android.hardware.type.watch` |
| Tests unitarios del contrato con Horologist | ✅ 3 tests, 0 fallos |
| Arranca en el emulador Wear OS (API 34) | ✅ proceso vivo, sin crash |
| **El puente del ADR 0003 funciona** | ✅ `isConnected=true` → `repo.connected=true` |
| La UI de Horologist pinta en la esfera 384x384 | ✅ ver capturas |
| Carga una pista y la muestra | ✅ "Tono de prueba / SimpMusic Wear" |
| **Reproduce audio real** | ✅ `state=PLAYING(3)` → `position=8011` (los 8 s completos) |

![Pista cargada](../docs/img/player-cargado.png) ![Reproduciendo](../docs/img/player-reproduciendo.png)

## Probarlo tú mismo, con ventana

El emulador se abre como una ventana normal del escritorio (X11 o Wayland con XWayland), sin instalar nada en el anfitrión:

```bash
./reloj.sh          # abre el reloj en una ventana
./instalar.sh       # compila, instala y abre la app dentro
```

Ratón para tocar, arrastrar para deslizar, **rueda del ratón para la corona giratoria**. Guía completa en [USO-INTERACTIVO.md](USO-INTERACTIVO.md).

## Uso por línea de comandos

```bash
# 1. Construir la imagen (~3.6 GB: SDK 37 + system images de Wear)
docker build -t wearlab:sdk .

# 2. Clonar el proyecto junto a este directorio, con el submódulo del core
git clone --recurse-submodules https://github.com/maxrave-dev/SimpMusic.git src

# 3. Copiar wearApp/ dentro de src/, registrar ":wearApp" en settings.gradle.kts
#    y añadir las versiones de wear/horologist a gradle/libs.versions.toml

# 4. Generar la pista de prueba
python3 gen-test-tone.py src/wearApp/src/main/res/raw/test_tone.wav

# 5. Compilar y probar
./run.sh "./gradlew :wearApp:assembleDebug"
./run.sh "./gradlew :wearApp:testDebugUnitTest"

# 6. Emulador + instalar + ejecutar
./emulator.sh wear34
docker exec wearemu bash -lc "adb install -r wearApp/build/outputs/apk/debug/wearApp-debug.apk"
docker exec wearemu bash -lc "adb shell am start -n com.simpmusic.wear/.MainActivity"
docker exec wearemu bash -lc "adb exec-out screencap -p" > captura.png
```

## Las cuatro trampas que costaron tiempo

Están resueltas en los scripts, pero conviene conocerlas.

**1. El keystore de debug debe persistir.** Si `~/.android` vive dentro del contenedor efímero, cada build firma con una clave nueva y `adb install -r` falla con `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Peor: si se redirige la salida a `/dev/null`, el fallo pasa desapercibido y **se acaba probando el APK viejo durante un buen rato**. Por eso `run.sh` monta `android-home:/root/.android`.

**2. `VolumeViewModel` no tiene constructor sin argumentos.** `viewModel()` a secas revienta con `NoSuchMethodException` en cuanto se compone `PlayerScreen`. Hay que usar `viewModel(factory = VolumeViewModel.Factory)`. No se detecta compilando: solo ejecutando.

**3. `connect()` es asíncrono.** Llamar a `setMedia()` justo después es un no-op silencioso — no lanza, simplemente no carga nada. Hay que esperar a que `playerRepository.connected` sea `true`.

**4. Las APIs de Horologist son experimentales.** Sin `optIn` de `ExperimentalHorologistApi` y `UnstableApi` en `compilerOptions`, el módulo no compila. Los errores son de opt-in, no de resolución — señal de que los tipos sí encajan.

## Detalle del entorno

- **Base:** `eclipse-temurin:21-jdk-jammy` (el proyecto exige `jvmToolchain(21)` y `JavaVersion.VERSION_21`)
- **SDK:** `platforms;android-37.0` + `build-tools;37.0.0` (el proyecto usa `compileSdk = 37`, AGP 9.2.1)
- **Emuladores:** AVD `wear34` (Wear OS 5, API 34) y `wear35` (Wear OS 5.1, API 35-ext15), ambos x86_64 para aprovechar KVM
- **Requiere `/dev/kvm`** en el anfitrión; sin él el emulador va inservible de lento

> **Corrección al plan:** no existe system image de Wear OS 6 (API 36) en el SDK a fecha de 2026-08-31. La más nueva es Wear OS 5.1 (API 35-ext15). Los AVD del plan se ajustaron a 34 y 35.

## Estado real del código

**Las seis fases del plan (F0–F5) están implementadas y verificadas ejecutando**, salvo los dos puntos que se indican abajo.

El reloj busca en YouTube Music, reproduce en streaming, importa las playlists y la sesión del móvil, descarga canciones, las reproduce sin cobertura, corre ofuscado con R8 y ofrece un tile en la esfera.

Limitaciones conocidas, a propósito:
- La importación del backup es **manual**: exportas en el móvil, llevas el fichero, importas. Sin sincronización automática, para no tocar la app del móvil ([ADR 0005](../docs/adr/0005-importar-backup-en-vez-de-sincronizar.md)).
- No hay biblioteca **remota** de YouTube Music (tus "me gusta", tus playlists de YT): eso sí exige la cookie de Google. Las playlists locales no.
- Las descargas van en el proceso de la app, no en un servicio en primer plano: si cierras la app a media descarga, se corta. Los ficheros a medias se descartan (`.parcial` que no se renombra), así que no quedan reproducciones rotas.

## Lo que NO está verificado

Dos cosas, y conviene decirlo claro:

1. **La respuesta real del servidor de YouTube Music.** El flujo de sesión se probó con una cookie sintética: se extrae bien y la petición sale, pero traer tus playlists de verdad requiere una cuenta de Google real.
2. **La complicación en la esfera.** Está registrada y el sistema la ve, y usa el mismo origen de datos que el tile (que sí se verificó mostrando el número correcto). Pero asignarla a una esfera requiere el editor de esferas, que el emulador no expone por línea de comandos.
3. **El consumo de batería.** Un emulador no lo mide de forma significativa. Requiere hardware.
