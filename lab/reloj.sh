#!/usr/bin/env bash
# Abre el emulador de Wear OS en una VENTANA de tu escritorio.
#
#   ./reloj.sh              -> Wear OS 5   (API 34)
#   ./reloj.sh wear35       -> Wear OS 5.1 (API 35)
#
# Se interactua con el raton: arrastrar = deslizar, rueda = corona giratoria.
set -euo pipefail
AVD="${1:-wear34}"
cd "$(dirname "$0")"

# Permitir que el contenedor pinte en tu sesion grafica (XWayland).
xhost +local: >/dev/null 2>&1 || true

docker rm -f wearemu >/dev/null 2>&1 || true

# Audio: si hay servidor PulseAudio/PipeWire del usuario, se pasa al contenedor.
PULSE_ARGS=()
if [ -S "/run/user/$(id -u)/pulse/native" ]; then
  PULSE_ARGS=(-v "/run/user/$(id -u)/pulse:/run/user/1000/pulse"
              -e "PULSE_SERVER=unix:/run/user/1000/pulse/native")
fi

docker run -d --name wearemu \
  --device /dev/kvm \
  -e DISPLAY="${DISPLAY:-:0}" \
  -v /tmp/.X11-unix:/tmp/.X11-unix \
  "${PULSE_ARGS[@]}" \
  -v "$(pwd)/src:/work" \
  -v "$(pwd)/gradle-cache:/gradle" \
  -v "$(pwd)/android-home:/root/.android" \
  -p 5555:5555 \
  -w /work \
  wearlab:gui \
  bash -lc "emulator -avd $AVD -gpu swiftshader_indirect -no-snapshot -accel on -no-boot-anim -dns-server 8.8.8.8,1.1.1.1"

echo "Emulador '$AVD' arrancando. La ventana del reloj aparecera en unos 30-60 s."
echo
echo "  Ver el arranque:      docker logs -f wearemu"
echo "  Instalar la app:      ./instalar.sh"
echo "  Apagar:               docker rm -f wearemu"
