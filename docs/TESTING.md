# Estrategia de pruebas y entornos de depuración

> Contexto: proyecto de uso personal. La cobertura se concentra donde los fallos son **caros de diagnosticar a mano** (mapeo de estado, descargas, release ofuscada), no en perseguir un porcentaje.

## 1. Los dos entornos, y para qué sirve cada uno

Ningún entorno por sí solo valida una app de música en reloj. Se usan en paralelo.

### Entorno A — Emulador Wear OS

Creación vía Android Studio (*Device Manager → Create device → Wear OS*). Se recomiendan dos AVD:

| AVD | API | Uso |
|---|---|---|
| `wear_api34_round` | 34 (Wear OS 5) | Suelo de compatibilidad |
| `wear_api36_round` | 36 (Wear OS 6) | Target de publicación |

Emparejamiento con teléfono mediante el **Wear OS pairing assistant** de Android Studio, necesario para probar el flujo de login por Data Layer (F2). Sirve tanto un emulador de teléfono como un teléfono físico.

**Sirve para:** iteración rápida de UI, navegación, lógica de pantallas, screenshot tests, flujo Data Layer.

**NO sirve para** (y por eso existe el entorno B):
- Audio Bluetooth real — el emulador saca el sonido por el host, no valida el camino A2DP ni la reconexión.
- Consumo de batería.
- Comportamiento de red del scraper desde la IP del reloj.
- Ambient mode y doze reales.

### Entorno B — Reloj físico

Dos vías, según la fase:

**Desarrollo diario — wireless debugging.** Ajustes → Sistema → Acerca de → Versiones → tocar *Número de compilación* x7; luego Opciones de desarrollador → *Depuración ADB* + *Depuración inalámbrica* + **Pantalla activa al cargar**. Emparejar con `adb pair <ip>:<puerto>` y conectar con `adb connect <ip>:<puerto_distinto>`. Reloj en el cargador y Wi-Fi en modo *siempre activado*.

> Si el reloj es un Pixel Watch 2 o 3, el cargador USB-C transporta datos y se puede hacer ADB por cable, que es más estable. En Pixel Watch 4 y en los Galaxy Watch (carga Qi) no existe esa opción.

**Builds sueltas sin ADB — Wear APK Install.** App instalable desde la Play Store del reloj; levanta un servidor web local, se sube el APK desde el navegador del móvil y lo instala con el PackageInstaller nativo. Útil para probar un release sin montar ADB.

## 2. Pirámide de pruebas

### Unitarias (JVM, sin dispositivo)

Framework: JUnit + `kotlinx-coroutines-test` + Turbine para `Flow`. Estructura **AAA** (Arrange-Act-Assert), nombres que describen el comportamiento.

Objetivo prioritario — **el adapter de F1**, que es la pieza de diseño propia y por tanto la que más puede fallar:

```
mapea estado de Player a UI: reproduciendo con media cargada emite estado Ready
mapea estado de Player a UI: buffering no se reporta como pausado
propaga el comando de siguiente pista al MediaController
al desconectarse el MediaController el repositorio emite estado desconectado
```

También: ViewModels de browse/search (F2), y mappers de dominio → modelos de UI de Horologist.

Doblar el `Player` de Media3 con un fake es viable: la interfaz es grande pero solo se usa un subconjunto. Media3 publica `androidx.media3.test.utils` con ayudas para esto — **conviene comprobar** si sus utilidades encajan antes de escribir un fake a mano.

### Screenshot tests (JVM, Robolectric)

Roborazzi + `WearPreviewDevices` / `WearPreviewFontScales`. Cada pantalla se captura en **redonda y cuadrada** — la geometría circular recorta esquinas y es donde aparecen los fallos de layout que no se ven en un móvil.

Cubrir las cuatro pantallas principales (player, browse, entity, downloads) en: estado normal, título largo (marquesina), sin carátula, y escala de fuente grande.

> Regla aprendida: un fallo visual no se descarta leyendo el texto del nodo. Si algo se sospecha recortado, se compara la caja del texto con la del contenedor.

### Instrumentadas (emulador + reloj)

- **F1:** el servicio arranca, el `MediaController` conecta, play/pause y transición de pista.
- **F3:** descargar → aparece en la biblioteca offline → reproducir sin red.
- Navegación con `SwipeDismissableNavHost`: entrar y salir de cada pantalla sin perder estado.

### Checklist manual (solo reloj físico)

Lo que ninguna suite automatiza. Ejecutar entero al final de F4 y ante cualquier cambio del servicio de reproducción:

- [ ] Suena por auriculares BT emparejados **al reloj** (no al móvil).
- [ ] La corona controla el volumen; el selector de salida lista los auriculares.
- [ ] La reproducción sobrevive a apagar la pantalla y al **ambient mode**.
- [ ] Modo avión + biblioteca descargada → reproduce.
- [ ] Doze durante una descarga larga → la descarga termina o reanuda.
- [ ] Desconectar y reconectar los auriculares BT → la reproducción se recupera.
- [ ] Sin el móvil cerca → la app funciona (verificación de que es realmente standalone).
- [ ] **Carátula y metadatos correctos en build de release** ← el fallo exacto del PR #1864.
- [ ] Una hora de streaming: anotar % de batería consumida.

## 3. Presupuestos de rendimiento

Medidos en reloj real durante F4, no antes:

| Métrica | Herramienta | Umbral de alarma |
|---|---|---|
| Memoria en reproducción | `adb shell dumpsys meminfo <pkg>` | PSS sostenido alto para un dispositivo de 2 GB → recortar módulos del core |
| Batería, 1 h streaming BT | `dumpsys batterystats` | Si el consumo hace inviable una sesión de ejercicio, priorizar offline |
| Arranque en frío | `adb shell am start -W` | Percepción de lentitud al abrir |
| Jank de scroll | Perfetto / Macrobenchmark | Solo si se percibe a ojo |

No se fijan números absolutos por adelantado: no hay medición previa de este core en un reloj y un umbral inventado sería ruido. Se toma la primera medición como línea base y se vigila la regresión.

## 4. Depuración: recetas concretas

```bash
# Logs solo de la app y del stack de medios
adb logcat --pid=$(adb shell pidof -s com.simpmusic.wear) MediaSession:V ExoPlayer:V '*:S'

# ¿Está viva la sesión de medios?
adb shell dumpsys media_session

# Estado del servicio en primer plano
adb shell dumpsys activity services | grep -A20 PlaybackService

# Memoria bajo reproducción
adb shell dumpsys meminfo com.simpmusic.wear

# Captura de pantalla del reloj
adb exec-out screencap -p > watch.png
```

**Layout Inspector** de Android Studio funciona sobre el reloj conectado y es la vía rápida para los recortes de la pantalla redonda.

Para el scraper (riesgo nº 1 del plan), la depuración útil es el log de las peticiones HTTP: interceptor de OkHttp en debug que registre código de respuesta y tamaño de cuerpo, para distinguir un fallo de parsing de un bloqueo de YouTube.

## 5. Integración continua

GitHub Actions con `./gradlew :wearApp:testDebugUnitTest` y `lint` en cada push. Los tests instrumentados **no** se ejecutan en CI: requieren emulador Wear con aceleración y el coste de mantenerlo no compensa en un proyecto personal. Se corren en local antes de cerrar cada fase.
