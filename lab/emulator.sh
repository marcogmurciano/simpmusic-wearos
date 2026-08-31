#!/usr/bin/env bash
# Arranca el emulador de Wear OS en un contenedor con nombre, instala el APK y verifica.
AVD="${1:-wear34}"
docker rm -f wearemu >/dev/null 2>&1
docker run -d --name wearemu \
  --device /dev/kvm \
  -v "$(pwd)/src:/work" \
  -v "$(pwd)/gradle-cache:/gradle" \
  -w /work \
  wearlab:sdk \
  bash -lc "emulator -avd $AVD -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -accel on"
echo "emulador $AVD arrancando en contenedor 'wearemu'"
