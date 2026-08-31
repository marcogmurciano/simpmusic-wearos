# ADR 0005 — Importar el backup del móvil en vez de sincronizar por Data Layer

**Estado:** aceptada · **Fecha:** 2026-08-31

## Contexto
El reloj debe mostrar las playlists que el usuario ya tiene creadas en el móvil. Esas listas son **locales**: `LocalPlaylistEntity` tiene `youtubePlaylistId` nullable y un `syncState` que admite "no sincronizada", así que **no requieren cuenta de Google**. Pero viven en la base de datos Room del móvil, y Android aísla cada app: **ninguna app puede leer la base de datos de otra**.

Restricción impuesta: **la app de móvil no se toca**. El usuario sigue con el SimpMusic oficial de F-Droid.

## Decisión
El reloj **importa el fichero de backup** que la app de móvil ya sabe exportar. `AutoBackupWorker` genera un ZIP que contiene la base de datos Room completa bajo la entrada `Music Database`. El reloj la extrae y la consulta con **SQLite directo**.

## Alternativas descartadas
- **Sincronización por Wear Data Layer.** Es el camino natural, pero requiere que la app del móvil envíe los datos, y una app sin código de Data Layer no puede. Habría que modificarla — exactamente lo que se quiere evitar.
- **Leer la base de datos del móvil directamente.** Imposible: el sandbox de Android lo prohíbe, con o sin permisos.
- **Usar Room y el módulo `:data` para leer el backup.** Arrastraría toda la inyección de dependencias del core (Room, DataStore, Spotify, IA, letras) por leer tres tablas. Se usa `SQLiteDatabase` en modo solo lectura sobre las tablas `local_playlist`, `song` y `pair_song_local_playlist`.

## Consecuencias
- **El móvil queda intacto.** Se usa la app oficial sin modificar.
- La transferencia es **manual**: exportar en el móvil, llevar el fichero al reloj, importar. No hay sincronización automática.
- El reloj lee un **retrato del momento**: si cambias tus listas en el móvil, hay que reexportar.
- Acoplamiento al esquema de la base de datos del core. Si upstream renombra tablas o columnas, el importador se rompe. Mitigación: son tablas estables y el fallo es visible (mensaje de error, no datos corruptos).

## Nota sobre el almacenamiento
El backup debe quedar donde la app pueda leerlo. Desde Android 10 el almacenamiento con ámbito impide leer `/sdcard/Download` sin permisos ni SAF — se comprobó ejecutando: `EACCES (Permission denied)`. Las rutas válidas son el directorio externo propio de la app o su almacenamiento interno.
