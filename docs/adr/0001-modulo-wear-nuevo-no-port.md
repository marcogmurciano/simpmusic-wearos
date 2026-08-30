# ADR 0001 — Módulo `:wearApp` nuevo, no port de la app existente

**Estado:** aceptada · **Fecha:** 2026-08-31

## Contexto
SimpMusic tiene su UI en `composeApp`, escrita con Compose Multiplatform, compartida entre Android y escritorio. La tentación evidente es "adaptar esa UI a pantalla pequeña".

## Decisión
Se crea un módulo `:wearApp` nuevo e independiente, con UI propia en `androidx.wear.compose`, que consume los módulos de lógica ya existentes.

## Motivos
1. **Compose Multiplatform no tiene target de Wear OS.** No es una cuestión de tamaño de pantalla: los componentes de Wear (`ScalingLazyColumn`/`TransformingLazyColumn`, `SwipeDismissableNavHost`, entrada rotatoria, `MaterialTheme` de Wear) viven en `androidx.wear.compose` y no son intercambiables con los de Compose estándar.
2. Aun si fuese técnicamente posible, la UI de reloj **no es la de móvil encogida**: cambian la navegación, la densidad de información y los objetivos táctiles.
3. El repositorio ya está modularizado para esto: `androidApp`, `composeApp` y `desktopApp` conviven sobre el mismo core. Añadir un cuarto consumidor es el patrón existente, no una excepción.

## Consecuencias
- Hay que escribir UI nueva. Se mitiga con Horologist (ADR 0002) y con el fork de Rahyan como cantera.
- El módulo se mantiene aparte; la app de móvil no se toca y no hay riesgo de regresión en ella.
- Dos superficies que mantener a futuro, pero acopladas solo por el core.
