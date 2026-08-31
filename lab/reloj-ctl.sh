#!/usr/bin/env bash
# Mando a distancia del reloj. La barra lateral del emulador Wear OS no tiene
# boton de Inicio, asi que estos son los controles fiables.
#
#   ./reloj-ctl.sh apps       lista de aplicaciones
#   ./reloj-ctl.sh musica     abre SimpMusic Wear
#   ./reloj-ctl.sh esfera     vuelve a la esfera del reloj
#   ./reloj-ctl.sh atras      gesto de volver atras
#   ./reloj-ctl.sh foto       guarda captura.png
#   ./reloj-ctl.sh donde      dice que hay en pantalla ahora
set -euo pipefail
cd "$(dirname "$0")"

if ! docker ps --filter name=wearemu --format '{{.Names}}' | grep -q wearemu; then
  echo "El emulador no esta corriendo. Arrancalo con:  ./reloj.sh"; exit 1
fi
a() { docker exec wearemu bash -lc "adb $*"; }

case "${1:-donde}" in
  apps)   a "shell input keyevent KEYCODE_HOME" >/dev/null; echo "Lista de aplicaciones abierta." ;;
  musica) a "shell am start -n com.simpmusic.wear/.MainActivity" >/dev/null 2>&1; echo "SimpMusic Wear abierta." ;;
  esfera) # Volver atras las veces que haga falta hasta la esfera (SysUiActivity).
          for _ in 1 2 3 4; do
            actual=$(a "shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity" || true)
            case "$actual" in *SysUiActivity*) break ;; esac
            a "shell input keyevent KEYCODE_BACK" >/dev/null; sleep 1.5
          done
          echo "De vuelta en la esfera." ;;
  atras)  a "shell input keyevent KEYCODE_BACK" >/dev/null; echo "Atras." ;;
  foto)   docker exec wearemu bash -lc "adb exec-out screencap -p" > captura.png; echo "Guardada en captura.png" ;;
  donde)  a "shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity" \
            | grep -oE "[a-z0-9._]+/[A-Za-z0-9._]+" | head -1 ;;
  *) sed -n '2,12p' "$0" | sed 's/^# \?//' ;;
esac
