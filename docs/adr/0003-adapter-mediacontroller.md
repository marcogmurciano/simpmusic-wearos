# ADR 0003 — Conectar la UI al servicio existente vía `MediaController`

**Estado:** aceptada · **Fecha:** 2026-08-31 · **Es la única decisión de diseño propia del proyecto**

## Contexto
La UI de Horologist consume la interfaz `PlayerRepository` de `horologist-media`. Hay que darle una implementación que reproduzca música de YouTube Music. El core de SimpMusic ya tiene un `MediaLibraryService` de Media3 con la resolución de streams, la cola y el crossfade resueltos dentro de un `DelegatingForwardingPlayer`.

## Decisión
**Opción A:** reutilizar el servicio de reproducción de SimpMusic tal cual, conectarse a él con un `MediaController` de Media3 y pasar ese controller a la implementación `PlayerRepositoryImpl` que trae `horologist-media-data`.

La clave que lo hace posible, **verificada contra el tag `v0.7.15`** de google/horologist:

```kotlin
public fun connect(player: Player, onClose: () -> Unit)
```

`connect` acepta cualquier `androidx.media3.common.Player`, y `MediaController` implementa esa interfaz. No hace falta ninguna adaptación de tipos.

## Alternativas
- **Opción B — backend propio de Horologist** (`horologist-media3-backend`) construyendo un ExoPlayer nuevo en el reloj. Obligaría a reimplementar dentro de ese pipeline toda la resolución de URLs del scraper. Descartada: duplica la parte más delicada del core.
- **Opción C — implementar `PlayerRepository` a mano.** Plan de contingencia si `PlayerRepositoryImpl` resulta demasiado rígido. La interfaz tiene 7 propiedades y ~19 métodos (ver `ARCHITECTURE.md`), así que el coste real es mayor que una estimación ingenua.
- **Opción D — compartir un `ExoPlayer` singleton** entre repositorio y servicio, sin `MediaController`. Es lo que hace `cassette`. Descartada aquí: el servicio de SimpMusic ya construye su propio player con el pipeline del scraper, y romperlo para inyectar uno externo es justo lo que se quiere evitar.

## Consecuencias
- Cero lógica de reproducción nueva: el scraper, la cola y el manejo de errores son los ya probados en móvil.
- El punto de fallo se concentra en un adapter pequeño y fácil de cubrir con tests unitarios.
- Dependencia de que las versiones de Media3 del core (1.11.0) y de `horologist-media-data` sean compatibles. Si chocan, `cassette` demuestra que 1.10.1 funciona con Horologist 0.7.15.
- `PlayerRepositoryImpl.connect` lanza `IllegalStateException("previously connected")` si se llama dos veces, y su código lleva un `// TODO support a cycle of changing players`. **El ciclo de vida importa:** una instancia por conexión, y `close()` al desconectar.

## Nota de honestidad
Ninguna app open source conocida hace exactamente esto (`MediaController` → `PlayerRepositoryImpl`). `cassette` usa la opción D. La viabilidad se apoya en la firma pública de `connect`, que es explícita al aceptar cualquier `Player`, no en un precedente. **Es el primer supuesto que hay que validar en F1.**
