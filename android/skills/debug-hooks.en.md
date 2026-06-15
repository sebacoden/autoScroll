# Debug and observability hooks

> 🌐 [Español](debug-hooks.md) · **English**

To drive and observe the service deterministically in e2e tests (where the 3-finger tap
cannot be simulated and swipe-based activation is not reliable across all apps), the
`AutoScrollService` registers a **`BroadcastReceiver` in debug builds only**
(`if (BuildConfig.DEBUG)`; it does not exist in release).

## Actions (broadcast via adb)

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

- `E2E_INTERACT` calls exactly the same `ScrollEngine.notifyUserInteraction(pauseOnTouchMs)`
  as a real interaction → the pause/resume being tested is **the production one**.
- `E2E_DUMP` reads through `UsageRepository.observeSessions/observeUsageByApp/observeTotalSwipes`
  (the same path the app uses), so it also exercises the read queries.
- `E2E_CLEAR` is preferable to `pm clear` because it does NOT reset settings or disable the service.

## Enabling the accessibility service via adb (for tests)

```powershell
$SVC = "com.freelanzer.autoscroller.debug/com.freelanzer.autoscroller.service.accessibility.AutoScrollService"
adb shell settings put secure enabled_accessibility_services $SVC
adb shell settings put secure accessibility_enabled 1
```

## Observing via logcat

```bash
adb logcat -s AutoScrollSvc:D AutoScrollEngine:D AutoScrollUsage:D
```

| Tag | What it logs |
|---|---|
| `AutoScrollSvc` | Session state (INICIADA/TERMINADA — started/ended), detected interactions, cutoff when leaving the app, receiver actions |
| `AutoScrollEngine` | `swipe #N`, `PAUSADO por interacción` (paused by interaction), `REANUDADO tras pausa` (resumed after pause), engine started/stopped |
| `AutoScrollUsage` | Writes to Room after `dao.insert()` and the `E2E_DUMP` dump |

## Ready-to-use e2e script

`scripts/e2e_youtube_session.ps1` orchestrates and **self-verifies** (PASS/FAIL) a full session
on YouTube Shorts using these hooks. See [`../TESTING.en.md`](../TESTING.en.md) §6.
