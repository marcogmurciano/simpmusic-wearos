#!/usr/bin/env bash
# Abre la app de musica en el reloj (util si te has salido a la esfera).
set -euo pipefail
cd "$(dirname "$0")"

if ! docker ps --filter name=wearemu --format '{{.Names}}' | grep -q wearemu; then
  echo "El emulador no esta corriendo. Arrancalo con:  ./reloj.sh"
  exit 1
fi

docker exec wearemu bash -lc "adb shell am start -n com.simpmusic.wear/.MainActivity" >/dev/null
echo "App abierta en el reloj."
