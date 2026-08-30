# ADR 0002 — Horologist como capa de UI y de descargas

**Estado:** aceptada · **Fecha:** 2026-08-31

## Contexto
Escribir desde cero un reproductor de reloj implica: pantalla de reproducción con controles adaptados a la esfera, control de volumen por corona, selector de salida Bluetooth, pantallas de navegación de catálogo y un sistema de descargas offline consciente de la red. Es mucho trabajo y está resuelto.

## Decisión
Usar **Horologist 0.7.15** (`horologist-media-ui`, `-media-ui-model`, `-media-data`, `-audio-ui`, `-network-awareness`, `-compose-layout`) como capa de presentación y de descargas.

## Alternativas consideradas
- **UI propia con wear-compose a pelo** — es lo que hace el fork de Rahyan. Da control total pero obliga a resolver a mano volumen, salida de audio y descargas. Descartada por coste.
- **Horologist 0.8.x-alpha** — versión más reciente en Maven pero sin changelog útil y sin adopción visible. Descartada por riesgo.

## Motivos
- Aporta hechas `PlayerScreen`, `BrowseScreen`, `EntityScreen`, `VolumeScreen` y `PlaylistDownloadScreen`.
- `MediaDownloadService` más `network-awareness` cubre el offline (F3) casi entero.
- Es de Google y está pensado específicamente para este caso; su app de ejemplo es un reproductor de streaming con descargas.
- **Existe prueba de que funciona:** `AdamNiederer/cassette` corre esta combinación exacta (Horologist 0.7.15 + Media3 1.10.1 + wear-compose 1.6.2) en una app real.

## Consecuencias
- La UI queda atada a las decisiones de diseño de Horologist. Aceptable: son las recomendadas por la plataforma.
- Hay que adaptar el cableado de DI, porque el ejemplo de Horologist usa Hilt y SimpMusic usa Koin. Solo cambia el wiring, no las clases.
