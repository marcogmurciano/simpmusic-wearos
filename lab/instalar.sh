#!/usr/bin/env bash
# Compila la app e instala en el emulador que este corriendo, y la abre.
set -euo pipefail
cd "$(dirname "$0")"

echo "==> Compilando..."
./run.sh "./gradlew :wearApp:assembleDebug --no-daemon -q"

echo "==> Esperando al reloj..."
until [ "$(docker exec wearemu bash -lc 'adb shell getprop sys.boot_completed 2>/dev/null' 2>/dev/null | tr -d '\r\n ')" = "1" ]; do
  sleep 5
done

echo "==> Instalando..."
docker exec wearemu bash -lc "adb install -r wearApp/build/outputs/apk/debug/wearApp-debug.apk"

echo "==> Abriendo..."
docker exec wearemu bash -lc "adb shell am start -n com.simpmusic.wear/.MainActivity" >/dev/null
echo "Listo. La app esta abierta en el reloj."
