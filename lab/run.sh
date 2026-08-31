#!/usr/bin/env bash
# Ejecuta un comando dentro del contenedor con proyecto, caché de Gradle y
# ~/.android persistentes. Persistir ~/.android es IMPRESCINDIBLE: si no, el
# keystore de debug se regenera en cada contenedor y `adb install -r` falla con
# INSTALL_FAILED_UPDATE_INCOMPATIBLE al cambiar la firma en cada build.
exec docker run --rm -i \
  --device /dev/kvm \
  -v "$(pwd)/src:/work" \
  -v "$(pwd)/gradle-cache:/gradle" \
  -v "$(pwd)/android-home:/root/.android" \
  -w /work \
  -e GRADLE_USER_HOME=/gradle \
  wearlab:sdk bash -lc "$*"
