#!/usr/bin/env bash
# Vuelca el ultimo crash de la app. Ejecutalo JUSTO DESPUES de que se cierre sola.
docker exec wearemu bash -lc "adb logcat -d -b crash" 2>/dev/null | tail -60
echo "--- ultimos errores de la app ---"
docker exec wearemu bash -lc "adb logcat -d 2>/dev/null | grep -iE 'AndroidRuntime|WearMusic|WearPlayback|WearDownloads|WearImport'" | tail -25
