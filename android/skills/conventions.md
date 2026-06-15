# Convenciones y trampas conocidas

> 🌐 **Español** · [English](conventions.en.md)

## Convenciones de código

- **Idioma:** comentarios, KDoc, mensajes de commit y docs en **español**.
- **Arquitectura limpia y testeable** (requisito explícito del dueño): separar capas, evitar
  acoplamiento entre UI y servicios vía estado compartido inyectado (no broadcasts ni
  estáticos para eso).
- **Dominio testeable en JVM:** `ScrollController` y `SwipeActivationDetector` son **Kotlin
  puro** — el timestamp se inyecta desde el caller, NO se usa `SystemClock` adentro. Mantener
  así para poder testear sin shadowing.
- **Repositorios = interfaces** (`SettingsRepository`, `UsageRepository`) con impl concreta
  (`DataStore…`, `Room…`) y `@Binds` en el módulo Hilt. Permite fakes en tests de ViewModel.
- **Validación de rangos en el repositorio** (contrato), no en el consumidor.
- **Versiones:** siempre vía el version catalog `gradle/libs.versions.toml`.
- **Logging de diagnóstico:** gateado por `BuildConfig.DEBUG` (no debe existir en release).
  Tags: `AutoScrollSvc`, `AutoScrollEngine`, `AutoScrollUsage`.

## Trampas conocidas (NO repetir — ya se pagaron)

### Build / Windows
- **Lock de `R.jar`:** no correr Gradle por terminal y un sync/build de Android Studio a la
  vez. Síntoma: tarea pegada en `processDebugResources` / `testDebugUnitTest` sin avanzar.
- **DataStore flaky en Windows:** dos escrituras seguidas pueden fallar con
  `Unable to rename .tmp`. En tests, partir en una sola escritura.

### Tests
- **`runTest` (reloj virtual) vs IO real:** no mezclar. Tests que tocan DataStore real →
  `runBlocking`; tests de ViewModel → fake en memoria + `runTest(scheduler)` con
  `MainDispatcherRule`. Mezclarlos **cuelga**.
- **`.first { predicado }` no tiene timeout** → puede colgar. Usar Turbine `.test{}` o
  predicados que realmente difieran. Ojo: `SettingsUiState.Initial` coincide con los defaults
  derivados, así que `first { it != Initial }` nunca difiere.
- Los tests de DataStore real llevan `Timeout(15s)` de JUnit como red de seguridad.

### Comportamiento en apps reales (verificado por logcat en emulador)
- **El emulador NO puede simular el tap de 3 dedos** (gesto a nivel sistema). Para e2e en
  emulador, arrancar la sesión con el hook de debug `E2E_START` (ver `debug-hooks.md`).
- **Activación por swipes:** funciona en feeds `RecyclerView` (TikTok, IG Reels, el *shelf* de
  Shorts), pero **NO en el player fullscreen de YouTube Shorts** (no emite `TYPE_VIEW_SCROLLED`,
  solo `TYPE_WINDOW_CONTENT_CHANGED`).
- **El "like" (doble-tap) en YouTube Shorts NO se expone** como `TYPE_VIEW_CLICKED` al
  servicio → la pausa por interacción no dispara por un like en Shorts. En e2e se usa el hook
  `E2E_INTERACT` (mismo camino de producción) como fallback determinista.
- **El emulador NO trae el binario `sqlite3`** (`run-as: exec failed for sqlite3`). Para
  inspeccionar Room: Database Inspector de Android Studio, o el hook `E2E_DUMP` (vuelca por
  `UsageRepository`), o sacar el `.db` con `run-as … cat`.

### Decisiones de diseño que parecen "raras" pero son a propósito
- **Trigger 3 dedos:** NO se usa overlay `WindowManager` (frágil, exige `SYSTEM_ALERT_WINDOW`).
  Se usa `FLAG_REQUEST_MULTI_FINGER_GESTURES` + `onGesture` del AccessibilityService.
- **La pausa por interacción NO cambia `ScrollState`:** vive en el `ScrollEngine` (suspende el
  bucle). La sesión sigue `Scrolling` para no parpadear la UI ni cortar el cronómetro de
  bienestar.
- **`ScrollState.Paused` quedó reservado/sin uso** tras reemplazar la pausa permanente por la
  pausa-con-auto-resume.
