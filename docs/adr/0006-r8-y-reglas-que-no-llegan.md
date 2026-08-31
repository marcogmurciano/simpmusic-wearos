# ADR 0006 — R8 activado, y las reglas que el scraper no exporta

**Estado:** aceptada · **Fecha:** 2026-08-31

## Contexto
El PR [#1864](https://github.com/maxrave-dev/SimpMusic/pull/1864) de SimpMusic murió porque R8 rompía carátulas y metadatos en release mientras en debug funcionaba. F4 consistía en activar R8 sin repetir ese fallo.

## Lo que se encontró al hacerlo

**1. R8 ni siquiera compilaba.** Rhino (`org.mozilla.javascript`), el motor JS que el scraper usa para descifrar las firmas de YouTube, referencia `java.beans.*`, que no existe en Android. Sin `-dontwarn java.beans.**` el build falla antes de empezar.

> Nota: una exploración previa concluyó que el scraper "no usa Rhino". Es incorrecto: sí lo arrastra. El error de R8 lo demostró.

**2. El scraper no exporta sus reglas.** `core/service/kotlinYtmusicScraper/proguard-rules.pro` contiene las reglas de `kotlinx.serialization` que su parseo necesita, pero **`consumer-rules.pro` está vacío (0 bytes)** y el módulo no declara `consumerProguardFiles`. En Gradle, `proguard-rules.pro` de una librería solo aplica al minificar esa librería por separado; lo que llega a quien la consume es `consumer-rules.pro`.

Consecuencia: al minificar la app de reloj, R8 borra los serializadores y **la búsqueda deja de funcionar solo en release**. Es el mismo patrón que mató al PR #1864 — funciona en debug, falla en release — pero por una causa distinta.

## Decisión
`wearApp/proguard-rules.pro` incluye:
- Las reglas aplicables del PR #1864: callbacks de `Player$Listener`, campos `artworkData`/`artworkUri` de `MediaMetadata`, callbacks de `MediaSession$Callback` y corrutinas. Se omiten las de `DelegatingForwardingPlayer` y `CrossfadeExoPlayerAdapter`: esta app no los usa.
- `-dontwarn java.beans.**` por Rhino.
- Las reglas de `kotlinx.serialization` **copiadas del scraper**, ya que no llegan solas.
- Ktor y OkHttp.

## Resultado verificado ejecutando

| Métrica | Debug | Release (R8) |
|---|---|---|
| Tamaño del APK | 191 MB | **93 MB** |
| PSS bajo reproducción | — | **58 MB** |
| Arranque en frío | — | **873 ms** |
| `classes.dex` | — | 9,0 MB |

Con el APK de release instalado: la búsqueda devuelve resultados reales, el stream resuelve (itag 251), `state=PLAYING`, y la UI muestra título y artista correctos ("Giorgio by / Daft Punk"). **El fallo de carátulas del PR #1864 no se reproduce.**

Los 58 MB de PSS resuelven el riesgo nº 4 del plan: el core cabe holgadamente en un reloj de 2 GB, incluso arrastrando el scraper.

## Lo que NO está verificado
**El consumo de batería.** Un emulador no lo mide de forma significativa: no hay radio real, ni pantalla física, ni salida Bluetooth. Requiere hardware.
