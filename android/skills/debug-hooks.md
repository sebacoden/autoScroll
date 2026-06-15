# Hooks de debug y observabilidad

> 🌐 **Español** · [English](debug-hooks.en.md)

Para manejar y observar el servicio de forma determinista en tests e2e (donde el tap de 3
dedos no es simulable y la activación por swipes no es confiable en todas las apps), el
`AutoScrollService` registra un **`BroadcastReceiver` solo en builds debug**
(`if (BuildConfig.DEBUG)`; no existe en release).

## Acciones (broadcast por adb)

```powershell
$PKG = "com.freelanzer.autoscroller.debug"
# Iniciar sesión (extra opcional pkg = app atribuida a la sesión)
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_START    -p $PKG --es pkg com.google.android.youtube
# Simular interacción del usuario (like/toque) → pausa con auto-resume
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_INTERACT -p $PKG
# Terminar sesión
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_STOP     -p $PKG
# Volcar la base Room a logcat (leyendo por UsageRepository)
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_DUMP     -p $PKG
# Borrar todo el historial de uso (dao.clear()) sin tocar ajustes/servicio
adb shell am broadcast -a com.freelanzer.autoscroller.E2E_CLEAR    -p $PKG
```

- `E2E_INTERACT` llama exactamente al mismo `ScrollEngine.notifyUserInteraction(pauseOnTouchMs)`
  que una interacción real → la pausa/reanudación testeada es **la de producción**.
- `E2E_DUMP` lee por `UsageRepository.observeSessions/observeUsageByApp/observeTotalSwipes`
  (el mismo camino que la app), así que también verifica las queries de lectura.
- `E2E_CLEAR` es preferible a `pm clear` porque NO resetea ajustes ni desactiva el servicio.

## Habilitar el servicio de accesibilidad por adb (para tests)

```powershell
$SVC = "com.freelanzer.autoscroller.debug/com.freelanzer.autoscroller.service.accessibility.AutoScrollService"
adb shell settings put secure enabled_accessibility_services $SVC
adb shell settings put secure accessibility_enabled 1
```

## Observar por logcat

```bash
adb logcat -s AutoScrollSvc:D AutoScrollEngine:D AutoScrollUsage:D
```

| Tag | Qué loguea |
|---|---|
| `AutoScrollSvc` | Estado de sesión (INICIADA/TERMINADA), interacciones detectadas, corte por salir de la app, acciones del receiver |
| `AutoScrollEngine` | `swipe #N`, `PAUSADO por interacción`, `REANUDADO tras pausa`, engine iniciado/detenido |
| `AutoScrollUsage` | Escritura en Room tras `dao.insert()` y el volcado de `E2E_DUMP` |

## Script e2e listo para usar

`scripts/e2e_youtube_session.ps1` orquesta y **auto-verifica** (PASS/FAIL) una sesión completa
en YouTube Shorts usando estos hooks. Ver [`../TESTING.md`](../TESTING.md) §6.
