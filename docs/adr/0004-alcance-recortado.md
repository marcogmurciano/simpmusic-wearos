# ADR 0004 — Alcance recortado y exclusión de módulos

**Estado:** aceptada · **Fecha:** 2026-08-31

## Contexto
SimpMusic en móvil tiene letras sincronizadas de cuatro proveedores, vídeos musicales a 1080p, Spotify Canvas, SponsorBlock, ecualizador, Last.fm, Discord RPC, escucha compartida, "Year in Review" y Android Auto. Un reloj de 2 GB de RAM con pantalla de 45 mm no es el sitio para casi nada de eso.

## Decisión
El módulo `:wearApp` compila **solo** contra `:domain`, `:data`, `:common`, `:kotlinYtmusicScraper` y `:media3`. Quedan fuera: `:spotify`, `:lastfm`, `:aiService`, `:lyricsService`, `:autoEqService`, `:cast`, `:kizzy`, `:listenTogether`, `:media-jvm*`, `:crashlytics`.

## Motivos
- **Es gratis:** el repositorio ya tiene esas piezas como módulos Gradle separados, varias con variantes `-empty` diseñadas precisamente para desactivarlas. No hay que arrancar nada, solo no incluirlo.
- Reduce el APK, la superficie de fallo y el consumo de memoria — que es la duda abierta sobre si el core cabe cómodo en el reloj.
- Concentra el esfuerzo en lo que un reloj sí aporta: escuchar música sin llevar el móvil encima.

## Consecuencias
- Si más adelante se quiere una de esas funciones (letras, por ejemplo), se añade el módulo y su pantalla; nada lo impide estructuralmente.
- La app de reloj es deliberadamente más pobre que la de móvil. Es la decisión correcta, no una limitación temporal.
