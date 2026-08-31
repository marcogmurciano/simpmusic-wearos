# ADR 0007 — Un servidor web en el reloj para recibir el backup

**Estado:** aceptada · **Fecha:** 2026-08-31

## Contexto
El reloj necesita recibir un fichero desde el móvil (el backup, que trae las playlists y la cookie de sesión). Meter un fichero en un reloj es sorprendentemente difícil.

## Alternativas descartadas, todas comprobadas

| Vía | Por qué no |
|---|---|
| Gestor de ficheros en el reloj | Desde Android 11 una app no puede escribir en el directorio de otra |
| `/sdcard/Download` | Devuelve `EACCES` — verificado ejecutando. Un ZIP no es "media", así que no hay permiso limpio |
| Selector de documentos (SAF) | Es la vía correcta en Android, pero en Wear OS la UI del selector es mala o inexistente |
| `adb push` + `run-as` | Solo funciona en compilaciones de depuración. Inservible para uso normal |
| Wear Data Layer | Exige **mismo nombre de paquete y misma firma** en ambas apps: obligaría a sustituir el SimpMusic oficial del móvil por una compilación propia |

## Decisión
La app **levanta un servidor HTTP mínimo en el reloj** (puerto 8080). Muestra su IP, el usuario abre esa dirección en el navegador del móvil, elige el fichero y lo sube. La app lo recibe **directamente en su propio almacenamiento**.

Es el mismo mecanismo que usan las herramientas de sideload de Wear OS, así que hay precedente de que funciona en la plataforma.

## Por qué es la buena
- **Cero permisos.** El fichero nunca pasa por almacenamiento compartido: entra por el socket y se escribe en `filesDir`.
- **Funciona en release**, no solo en depuración.
- **El móvil no se toca.** Basta un navegador.
- Se importa solo al terminar la subida: una acción del usuario, no tres.

## Detalles
- Acepta **una sola subida y se apaga**: dejar un puerto abierto en un reloj no tiene sentido, ni por batería ni por exposición.
- Límite de 64 MB.
- En vez de un parser completo de `multipart/form-data`, se localiza el ZIP dentro del cuerpo por su firma (`PK` al inicio, `PK\x05\x06` del End Of Central Directory al final). Es suficiente para un fichero por subida y evita traer una dependencia.

## Verificado ejecutando
`GET /` sirve el formulario; `POST /` recibe 1315 bytes; el reloj extrae la base de datos, encuentra la cookie (56 caracteres) y lee 2 playlists, todo encadenado sin intervención.

## Lo que queda manual
Sigue siendo una acción del usuario: exportar en el móvil y subir el fichero. Pero es **una sola vez**: después, con la sesión importada, las playlists llegan del servidor de YouTube Music siempre actualizadas ([ADR 0005](0005-importar-backup-en-vez-de-sincronizar.md)).
