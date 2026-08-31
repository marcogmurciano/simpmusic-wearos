#!/usr/bin/env bash
# Da internet al emulador de Wear OS.
#
# Los AVD de Wear OS arrancan con la wifi virtual (netsim) SIN conectar y sin
# ruta por defecto IPv4, asi que el reloj cree tener red pero no pasa trafico.
# Esto lo arregla. Hay que ejecutarlo tras cada arranque del emulador.
set -euo pipefail
a() { docker exec wearemu bash -lc "adb $*"; }

echo "==> root"; a "root" >/dev/null 2>&1 || true; sleep 4
echo "==> conectando a la wifi virtual"
a "shell 'cmd wifi connect-network AndroidWifi open'" >/dev/null 2>&1 || true
sleep 6
echo "==> ruta por defecto"
a "shell 'ip route add 10.0.2.0/24 dev wlan0 table wlan0'" >/dev/null 2>&1 || true
a "shell 'ip route add default via 10.0.2.2 dev wlan0 table wlan0'" >/dev/null 2>&1 || true
sleep 2
if docker exec wearemu bash -lc "adb shell 'ping -c1 -W4 music.youtube.com'" >/dev/null 2>&1; then
  echo "OK: el reloj tiene internet."
else
  echo "FALLO: sigue sin internet. Revisa 'docker logs wearemu'."; exit 1
fi
