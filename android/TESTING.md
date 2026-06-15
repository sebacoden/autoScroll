# Testing — AutoScroller (módulo Android)

Guía de qué está testeado, cómo correrlo y cómo replicarlo. La estrategia sigue la
pirámide de tests: muchos **unit tests** rápidos y deterministas, algunos **instrumentados**
para lo que necesita Android real, y una **verificación manual** documentada para el
comportamiento cross-app (que no conviene automatizar).

---

## 1. Resumen

| Nivel | Dónde | Qué cubre | Determinista | En CI |
|---|---|---|---|---|
| **Unit** (JVM) | `src/test/` | Lógica de dominio, repositorios, ViewModels | ✅ | ✅ |
| **Instrumentado** | `src/androidTest/` | Room DAO contra SQLite real | ✅ | ✅ (con emulador) |
| **Manual** | este documento §6 | Auto-scroll real en YouTube/TikTok | ❌ | ❌ (a mano) |

---

## 2. Unit tests (JVM) — `src/test/`

Corren en la JVM, sin device. Rápidos (~1–2 min todo el suite).

| Test | Qué verifica |
|---|---|
| `ScrollControllerTest` | Máquina de estados (Idle/Scrolling/Paused), `toggle`, contador de swipes, `lastSwipeAtMs`, espejo del intervalo |
| `SwipeActivationDetectorTest` | Activación por N swipes en ventana de tiempo, reset, límites |
| `SessionRecorderTest` | Apertura/cierre de sesión, descarte de sesiones vacías, `Clock` inyectable |
| `SettingsRepositoryTest` | Persistencia real en DataStore (archivo temporal): defaults, escritura, validación de rangos |
| `SettingsViewModelTest` | Derivación de `SettingsUiState`, delegación de setters, refresh del estado del servicio |
| `EulaViewModelTest` | Persistencia del flag de EULA + callback |

**Cómo correrlos:**
```bash
./gradlew :app:testDebugUnitTest
```
Reporte HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.

**Decisiones de diseño (buenas prácticas):**
- Los **ViewModels** se testean con un `FakeSettingsRepository` en memoria
  (`src/test/.../testsupport/`), no contra DataStore. La persistencia real queda cubierta
  por `SettingsRepositoryTest`. Esto hace los tests de ViewModel instantáneos y deterministas.
- `SettingsRepository` es una **interfaz** (impl `DataStoreSettingsRepository`) justamente
  para permitir el fake.
- `ScrollController` y `SwipeActivationDetector` son **Kotlin puro** (sin Android): el
  timestamp se inyecta desde el caller, así no dependen de `SystemClock`.
- `MainDispatcherRule` + `runTest(scheduler)` dan determinismo al `stateIn(WhileSubscribed)`.
- Los tests que tocan DataStore real llevan un `Timeout(15s)` de JUnit como red de seguridad.

---

## 3. Test instrumentado (Room DAO) — `src/androidTest/`

`ScrollSessionDaoTest` corre contra una base Room **in-memory** (SQLite real) en un
device/emulador. Verifica inserción y las queries de agregación (total de swipes, uso por
app con `GROUP BY`). Es el e2e **determinista** de la capa de persistencia.

**Cómo correrlo** (requiere un emulador o device conectado):
```bash
./gradlew :app:connectedDebugAndroidTest
```
Reporte: `app/build/reports/androidTests/connected/index.html`.

> El gesto de 3 dedos y el auto-scroll real **no** se testean acá (ver §6).

---

## 4. Cómo ver los datos guardados (Room)

La app registra cada sesión de auto-scroll en `autoscroller_usage.db` (tabla
`scroll_sessions`).

### a) Database Inspector (Android Studio) — recomendado
1. Corré la app (debug) en el emulador.
2. **View → Tool Windows → App Inspection → Database Inspector**.
3. Proceso `com.freelanzer.autoscroller.debug` → tabla `scroll_sessions`.
4. "Live updates" para verlo en vivo, o correr SQL:
   ```sql
   SELECT appPackage, SUM(endTime-startTime)/1000 AS seg, COUNT(*) AS sesiones
   FROM scroll_sessions GROUP BY appPackage ORDER BY seg DESC;
   ```

> **Ojo:** las imágenes recientes de **emulador no traen el binario `sqlite3`**
> (`run-as: exec failed for sqlite3: No such file or directory`). En emulador usar el
> Database Inspector, o el log debug `AutoScrollUsage` (confirma cada escritura), o sacar el
> `.db` con `run-as ... cat` y abrirlo con DB Browser. El `sqlite3` por `run-as` de abajo
> aplica a devices físicos que sí lo incluyen.

### b) Por adb (build debug → `run-as`)
```bash
ADB="$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"   # ajustar ruta

# Listar DBs
& $ADB shell run-as com.freelanzer.autoscroller.debug ls databases/

# Query directa
& $ADB shell run-as com.freelanzer.autoscroller.debug \
    sqlite3 databases/autoscroller_usage.db "SELECT * FROM scroll_sessions;"

# Sacar el archivo y abrirlo con DB Browser for SQLite
& $ADB exec-out run-as com.freelanzer.autoscroller.debug \
    cat databases/autoscroller_usage.db > usage.db
```

---

## 5. Cómo replicar la corrida de tests (paso a paso)

1. **Importante:** no dispares un build/sync en Android Studio mientras corrés Gradle
   desde la terminal — en Windows ambos pelean por `R.jar` y el build se cuelga.
2. Parar daemons viejos (opcional pero recomendado si hubo cuelgues):
   ```bash
   ./gradlew --stop
   ```
3. Unit tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
4. Build + lint + unit (lo que corre el botón "Build" de AS):
   ```bash
   ./gradlew :app:build
   ```
5. Instrumentados (con emulador encendido):
   ```bash
   ./gradlew :app:connectedDebugAndroidTest
   ```

---

## 6. Verificación manual — auto-scroll real (NO automatizado)

El comportamiento cross-app (abrir YouTube/TikTok, activar y ver el scroll automático) se
verifica **a mano**. No es parte del suite automatizado porque dependería de apps de
terceros (instalación, login, layout que cambia) → frágil y no determinista.

### Preparación
1. Instalar la app: `./gradlew :app:installDebug` (o ▶️ Run en AS).
2. Abrir AutoScroller → aceptar EULA → conceder notificaciones → habilitar el servicio de
   accesibilidad (deep-link directo desde la app).

### Checklist de verificación
| # | Paso | Resultado esperado |
|---|---|---|
| 1 | En Ajustes, mover el slider de intervalo | La vista previa cambia de ritmo |
| 2 | Tap con 3 dedos en cualquier app (device físico) | Inicia el auto-scroll |
| 3 | Abrir YouTube Shorts | Cada N s avanza al siguiente video solo |
| 4 | Hacer un swipe manual durante el auto-scroll | Se pausa (pausa inteligente) |
| 5 | Tap con 3 dedos de nuevo | Reanuda |
| 6 | Activar "3 swipes hacia arriba" en Ajustes, hacer 3 swipes | Inicia el auto-scroll |
| 7 | Dejar correr hasta el límite (ponerlo en 5 min) | Notificación "Llegaste a tu límite" |
| 8 | Verificar Room (Database Inspector) | Hay filas en `scroll_sessions` con la app y swipes |

> El tap de 3 dedos **no es simulable** en el emulador (no hay shortcut nativo). Usar un
> device físico para los pasos 2 y 5–6, o el botón de la UI / la activación por swipes.

### Script de testing por adb

`scripts/manual_autoscroll_test.ps1` automatiza lo automatizable de una sesión (instalar,
habilitar accesibilidad, abrir YouTube, simular swipes, verificar que el `WellbeingService`
arranca, salir de la app y verificar que la sesión termina, leer las sesiones de Room):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\manual_autoscroll_test.ps1
```

### E2E semi-automatizado de una sesión completa — `scripts/e2e_youtube_session.ps1`

Reproduce **una sesión real en YouTube Shorts** de punta a punta y la **verifica sola**
(PASS/FAIL) leyendo logcat y Room. Escenario:

1. Abre YouTube en el feed de Shorts (deep link `https://www.youtube.com/shorts`).
2. **Inicia** la sesión de forma determinista (ver hook de debug abajo), atribuida a YouTube.
3. Corre 30 s de auto-scroll (swipes reales vía `dispatchGesture`).
4. **Like** en el video (doble-tap real); si Shorts no lo expone como evento de accesibilidad,
   cae al hook `E2E_INTERACT`, que ejercita **el mismo camino de producción** de la pausa.
5. La sesión **se pausa** y **reanuda sola** tras ~3 s sin interacción (`pauseOnTouchSeconds`).
6. Corre otros 30 s.
7. **Sale** de YouTube (HOME) → la sesión **termina** al dejar la app.
8. Verifica el corte (`WellbeingService` abajo) y que la **fila quedó en Room**.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\e2e_youtube_session.ps1   # -RunSeconds 30
```

Toda la observabilidad viene de logs **solo-debug** (`BuildConfig.DEBUG`): tags
`AutoScrollSvc` / `AutoScrollEngine` (estado, swipes, pausa/reanudación, corte) y
`AutoScrollUsage` (confirma la escritura en Room tras `dao.insert()`).

#### Hook de control e2e (solo build debug)

El tap de 3 dedos **no es simulable** en emulador y la activación por swipes **no es
confiable** en el player de Shorts (ver hallazgo abajo). Para arrancar/parar/pausar la
sesión de forma determinista, `AutoScrollService` registra un `BroadcastReceiver`
**únicamente cuando `BuildConfig.DEBUG`** (no existe en release). Acciones:

```powershell
$PKG = "com.freelanzer.autoscroller.debug"
# Iniciar (extra opcional pkg = app atribuida a la sesión)
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_START   -p $PKG --es pkg com.google.android.youtube
# Simular una interacción del usuario (like/toque) -> pausa con auto-resume
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_INTERACT -p $PKG
# Terminar la sesión
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_STOP    -p $PKG
# Volcar la base Room a logcat (tag AutoScrollUsage), leyendo por UsageRepository
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_DUMP    -p $PKG
# Borrar todo el historial de uso (dao.clear())
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_CLEAR   -p $PKG
```

`E2E_INTERACT` llama exactamente al mismo `ScrollEngine.notifyUserInteraction(pauseOnTouchMs)`
que dispara una interacción real, así que la pausa/reanudación testeada es la de producción.
`E2E_DUMP` vuelca todas las filas + agregaciones **leyendo por `UsageRepository`** (el mismo
camino que la app), así que también verifica las queries de lectura; útil porque el emulador
no trae `sqlite3`. `E2E_CLEAR` resetea la base sin tocar ajustes ni el servicio (a diferencia
de `pm clear`).

### Hallazgo importante — activación por swipes y eventos de accesibilidad

Verificado en emulador con logging (`DEBUG_LOG=true` en `AutoScrollService` +
`adb logcat -s AutoScrollSvc`):

- La activación por swipes **depende de que la app emita `TYPE_VIEW_SCROLLED`** con
  magnitud de scroll. Un mismo gesto físico genera una ráfaga de eventos con `scrollDeltaY`
  de signo mezclado → por eso el detector usa **debounce** (no cuenta la ráfaga como varios)
  y **no filtra por signo** (cuenta cualquier scroll vertical significativo).
- **Funciona** en feeds basados en `RecyclerView` (TikTok, Instagram Reels, y el *shelf* de
  Shorts): verificado que activa y arranca el `WellbeingService`.
- **NO funciona** en el **player fullscreen de YouTube Shorts**: usa un pager custom que solo
  emite `TYPE_WINDOW_CONTENT_CHANGED` (demasiado ruidoso para detectar swipes), cero
  `TYPE_VIEW_SCROLLED`. Es una limitación inherente de la detección por eventos de
  accesibilidad sobre apps de terceros heterogéneas.
- **El tap de 3 dedos es el activador universal confiable** (gesto a nivel sistema,
  independiente de la app) — recomendado para YouTube Shorts.
- **El "like" (doble-tap) en el player de Shorts tampoco se expone** como `TYPE_VIEW_CLICKED`
  / `TYPE_VIEW_LONG_CLICKED` al servicio de accesibilidad → la pausa por interacción **no
  dispara** por un like en Shorts. Verificado con `e2e_youtube_session.ps1` (cae al hook
  `E2E_INTERACT`). En feeds `RecyclerView` (TikTok / IG Reels) un toque sí suele emitir el
  evento. La lógica de pausa en sí (production path) queda igualmente verificada por el hook.
- **Corte de sesión:** al salir de la app de la sesión hacia el launcher u otra app no
  habilitada (`TYPE_WINDOW_STATE_CHANGED`), el auto-scroll se detiene. Se ignoran paquetes
  de UI de sistema transitorios (`com.android.systemui`, `android`).

---

## 7. Notas de entorno

- **JDK del daemon:** 21 (vía foojay-resolver). `jvmTarget` de la app: 17.
- **AGP 9 / Gradle 9.5.1 / Kotlin 2.3.21 / KSP / Room 2.8.4.**
- Si un test JVM se cuelga, revisar que no mezcle `runTest` (reloj virtual) con IO real:
  los tests de DataStore usan `runBlocking`; los de ViewModel usan fake + `runTest`.
