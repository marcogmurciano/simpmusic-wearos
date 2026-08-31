# Probar el reloj tú mismo, con ventana

El emulador se abre como una **ventana normal de tu escritorio**. No hace falta instalar nada en el sistema anfitrión: ni Android Studio, ni el SDK, ni adb. Todo vive en el contenedor.

## Arrancar

```bash
cd ~/simpmusic-wear-lab
./reloj.sh              # Wear OS 5   (API 34)
./reloj.sh wear35       # Wear OS 5.1 (API 35)
```

La ventana del reloj aparece en 30-60 segundos. Después:

```bash
./instalar.sh           # compila, instala y abre la app en el reloj
```

Para apagarlo: `docker rm -f wearemu`

## Si te sales de la app

Al pulsar el botón lateral vuelves a la esfera del reloj, como en uno de verdad. Para volver a la app:

```bash
./abrir.sh          # la reabre al instante
```

O desde el propio reloj: botón lateral (o tecla Inicio) → lista de aplicaciones → bajar con la **rueda del ratón** → **SimpMusic Wear**.

La reproducción no se corta al salir: el servicio de medios sigue en segundo plano, así que al volver encuentras la pista donde estaba.

## La barra lateral NO tiene botón de Inicio

Conviene saberlo antes de buscarlo: en los AVD de Wear OS la barra de herramientas solo trae **Cerrar, Minimizar, Encendido, Volumen +, Volumen − y Captura**. No hay botón de Inicio ni de Atrás, porque un reloj real no los tiene: usa gestos y el botón físico lateral. El de encendido solo apaga y enciende la pantalla, y el de volumen abre un deslizador, así que parece que no hacen nada.

Para navegar, usa el mando por terminal:

```bash
./reloj-ctl.sh apps       # lista de aplicaciones
./reloj-ctl.sh musica     # abre SimpMusic Wear
./reloj-ctl.sh esfera     # vuelve a la esfera del reloj
./reloj-ctl.sh atras      # un paso atrás
./reloj-ctl.sh foto       # guarda captura.png
./reloj-ctl.sh donde      # dice qué hay en pantalla ahora
```

`esfera` insiste hasta llegar de verdad a la esfera, porque el número de pasos atrás depende de dónde estés.

## Cómo se maneja con el ratón

| Gesto real en el reloj | En la ventana |
|---|---|
| Tocar | Clic |
| Deslizar (volver atrás, navegar) | Arrastrar con el botón izquierdo |
| **Corona giratoria** (volumen, scroll) | **Rueda del ratón** |
| Botón lateral | Barra de herramientas lateral de la ventana |

La barra lateral del emulador también da acceso a rotación, capturas, y al panel de *Extended controls* (batería, red, ubicación).

## Requisitos del anfitrión

- **Docker** y **`/dev/kvm`** (sin KVM el emulador va inservible de lento).
- Un servidor gráfico X11 o **Wayland con XWayland** — `reloj.sh` monta `/tmp/.X11-unix` y ejecuta `xhost +local:` por ti. Verificado funcionando en Wayland con GNOME/mutter.
- **Audio (opcional):** si existe `/run/user/<uid>/pulse/native` (PulseAudio o PipeWire), el script lo pasa al contenedor y el sonido del reloj sale por tus altavoces. Si no existe, el emulador funciona igual pero mudo.

## Qué persiste entre sesiones

`android-home/` guarda los AVD y el keystore de debug, montado como volumen. Es decir: **las apps que instales y los ajustes que toques en el reloj siguen ahí** la próxima vez que lo arranques. Y el keystore estable evita el `INSTALL_FAILED_UPDATE_INCOMPATIBLE` de firmas cambiantes.

## Trastear a mano

```bash
# Una shell dentro del contenedor del emulador
docker exec -it wearemu bash

# Desde el anfitrión, cualquier comando adb
docker exec wearemu bash -lc "adb shell dumpsys media_session | head -40"
docker exec wearemu bash -lc "adb logcat -s WearPlayback"

# Captura de la pantalla del reloj
docker exec wearemu bash -lc "adb exec-out screencap -p" > captura.png
```

## Si la ventana no aparece

1. Comprueba que el contenedor sigue vivo: `docker ps --filter name=wearemu`
2. Mira el arranque: `docker logs -f wearemu`
3. Busca un fallo de librería: `docker logs wearemu 2>&1 | grep -i "error while loading"`
   — así se descubrió que faltaba `libxkbfile1`, ya incluida en `Dockerfile.gui`.
4. Verifica que la ventana existe aunque no la veas:
   `xwininfo -root -children | grep -i "Android Emulator"`

## Alternativa sin entorno gráfico

Si trabajas por SSH o sin escritorio, usa el modo headless original (`emulator.sh`) y saca capturas con `adb exec-out screencap`. Es como se validó F1 la primera vez.
