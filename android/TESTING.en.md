# Testing — AutoScroller (Android module)

> 🌐 [Español](TESTING.md) · **English**

Guide to what is tested, how to run it and how to reproduce it. The strategy follows the
testing pyramid: many fast, deterministic **unit tests**, some **instrumented** tests
for what needs real Android, and a documented **manual verification** for the
cross-app behavior (which is not worth automating).

---

## 1. Summary

| Level | Where | What it covers | Deterministic | In CI |
|---|---|---|---|---|
| **Unit** (JVM) | `src/test/` | Domain logic, repositories, ViewModels | ✅ | ✅ |
| **Instrumented** | `src/androidTest/` | Room DAO against real SQLite | ✅ | ✅ (with emulator) |
| **Manual** | this document §6 | Real auto-scroll on YouTube/TikTok | ❌ | ❌ (by hand) |

---

## 2. Unit tests (JVM) — `src/test/`

They run on the JVM, without a device. Fast (~1–2 min for the whole suite).

| Test | What it verifies |
|---|---|
| `ScrollControllerTest` | State machine (Idle/Scrolling/Paused), `toggle`, swipe counter, `lastSwipeAtMs`, interval mirror |
| `SwipeActivationDetectorTest` | Activation by N swipes within a time window, reset, limits |
| `SessionRecorderTest` | Session open/close, discarding empty sessions, injectable `Clock` |
| `SettingsRepositoryTest` | Real persistence in DataStore (temporary file): defaults, writing, range validation |
| `SettingsViewModelTest` | Derivation of `SettingsUiState`, setter delegation, service state refresh |
| `EulaViewModelTest` | Persistence of the EULA flag + callback |

**How to run them:**
```bash
./gradlew :app:testDebugUnitTest
```
HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

**Design decisions (best practices):**
- The **ViewModels** are tested with an in-memory `FakeSettingsRepository`
  (`src/test/.../testsupport/`), not against DataStore. Real persistence is covered
  by `SettingsRepositoryTest`. This makes the ViewModel tests instant and deterministic.
- `SettingsRepository` is an **interface** (impl `DataStoreSettingsRepository`) precisely
  to allow the fake.
- `ScrollController` and `SwipeActivationDetector` are **pure Kotlin** (no Android): the
  timestamp is injected from the caller, so they don't depend on `SystemClock`.
- `MainDispatcherRule` + `runTest(scheduler)` give determinism to `stateIn(WhileSubscribed)`.
- The tests that touch real DataStore carry a JUnit `Timeout(15s)` as a safety net.

---

## 3. Instrumented test (Room DAO) — `src/androidTest/`

`ScrollSessionDaoTest` runs against an **in-memory** Room database (real SQLite) on a
device/emulator. It verifies insertion and the aggregation queries (total swipes, usage per
app with `GROUP BY`). It is the **deterministic** e2e of the persistence layer.

**How to run it** (requires a connected emulator or device):
```bash
./gradlew :app:connectedDebugAndroidTest
```
Report: `app/build/reports/androidTests/connected/index.html`.

> The 3-finger gesture and real auto-scroll are **not** tested here (see §6).

---

## 4. How to view the saved data (Room)

The app records each auto-scroll session in `autoscroller_usage.db` (table
`scroll_sessions`).

### a) Database Inspector (Android Studio) — recommended
1. Run the app (debug) on the emulator.
2. **View → Tool Windows → App Inspection → Database Inspector**.
3. Process `com.freelanzer.autoscroller.debug` → table `scroll_sessions`.
4. "Live updates" to see it live, or run SQL:
   ```sql
   SELECT appPackage, SUM(endTime-startTime)/1000 AS seg, COUNT(*) AS sesiones
   FROM scroll_sessions GROUP BY appPackage ORDER BY seg DESC;
   ```

> **Heads up:** recent **emulator images don't ship the `sqlite3` binary**
> (`run-as: exec failed for sqlite3: No such file or directory`). On the emulator use the
> Database Inspector, or the debug log `AutoScrollUsage` (it confirms each write), or pull the
> `.db` with `run-as ... cat` and open it with DB Browser. The `sqlite3` via `run-as` below
> applies to physical devices that do include it.

### b) Via adb (debug build → `run-as`)
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

## 5. How to reproduce the test run (step by step)

1. **Important:** don't trigger a build/sync in Android Studio while you're running Gradle
   from the terminal — on Windows both fight over `R.jar` and the build hangs.
2. Stop old daemons (optional but recommended if there were hangs):
   ```bash
   ./gradlew --stop
   ```
3. Unit tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
4. Build + lint + unit (what the AS "Build" button runs):
   ```bash
   ./gradlew :app:build
   ```
5. Instrumented (with the emulator running):
   ```bash
   ./gradlew :app:connectedDebugAndroidTest
   ```

---

## 6. Manual verification — real auto-scroll (NOT automated)

The cross-app behavior (open YouTube/TikTok, activate and watch the automatic scroll) is
verified **by hand**. It is not part of the automated suite because it would depend on
third-party apps (installation, login, layout that changes) → fragile and non-deterministic.

### Preparation
1. Install the app: `./gradlew :app:installDebug` (or ▶️ Run in AS).
2. Open AutoScroller → accept EULA → grant notifications → enable the accessibility
   service (direct deep-link from the app).

### Verification checklist
| # | Step | Expected result |
|---|---|---|
| 1 | In Settings, move the interval slider | The preview changes its pace |
| 2 | 3-finger tap in any app (physical device) | Auto-scroll starts |
| 3 | Open YouTube Shorts | Every N s it advances to the next video on its own |
| 4 | Do a manual swipe during auto-scroll | It pauses (smart pause) |
| 5 | 3-finger tap again | Resumes |
| 6 | Enable "3 swipes up" in Settings, do 3 swipes | Auto-scroll starts |
| 7 | Let it run until the limit (set it to 5 min) | "You reached your limit" notification |
| 8 | Check Room (Database Inspector) | There are rows in `scroll_sessions` with the app and swipes |

> The 3-finger tap is **not simulable** on the emulator (no native shortcut). Use a
> physical device for steps 2 and 5–6, or the UI button / swipe activation.

### Testing script via adb

`scripts/manual_autoscroll_test.ps1` automates the automatable part of a session (install,
enable accessibility, open YouTube, simulate swipes, verify that the `WellbeingService`
starts, leave the app and verify that the session ends, read the Room sessions):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\manual_autoscroll_test.ps1
```

### Semi-automated E2E of a full session — `scripts/e2e_youtube_session.ps1`

It reproduces **a real YouTube Shorts session** end to end and **verifies itself**
(PASS/FAIL) by reading logcat and Room. Scenario:

1. Opens YouTube on the Shorts feed (deep link `https://www.youtube.com/shorts`).
2. **Starts** the session deterministically (see debug hook below), attributed to YouTube.
3. Runs 30 s of auto-scroll (real swipes via `dispatchGesture`).
4. **Like** on the video (real double-tap); if Shorts does not expose it as an accessibility event,
   it falls back to the `E2E_INTERACT` hook, which exercises **the same production path** of the pause.
5. The session **pauses** and **resumes on its own** after ~3 s without interaction (`pauseOnTouchSeconds`).
6. Runs another 30 s.
7. **Leaves** YouTube (HOME) → the session **ends** when leaving the app.
8. Verifies the cut-off (`WellbeingService` below) and that the **row landed in Room**.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\e2e_youtube_session.ps1   # -RunSeconds 30
```

All the observability comes from **debug-only** logs (`BuildConfig.DEBUG`): tags
`AutoScrollSvc` / `AutoScrollEngine` (state, swipes, pause/resume, cut-off) and
`AutoScrollUsage` (confirms the write to Room after `dao.insert()`).

#### E2E control hook (debug build only)

The 3-finger tap is **not simulable** on the emulator and swipe activation is **not
reliable** in the Shorts player (see finding below). To start/stop/pause the session
deterministically, `AutoScrollService` registers a `BroadcastReceiver`
**only when `BuildConfig.DEBUG`** (it does not exist in release). Actions:

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

`E2E_INTERACT` calls exactly the same `ScrollEngine.notifyUserInteraction(pauseOnTouchMs)`
that a real interaction triggers, so the pause/resume being tested is the production one.
`E2E_DUMP` dumps all rows + aggregations **reading through `UsageRepository`** (the same
path the app uses), so it also verifies the read queries; useful because the emulator
does not ship `sqlite3`. `E2E_CLEAR` resets the database without touching settings or the
service (unlike `pm clear`).

### Important finding — swipe activation and accessibility events

Verified on the emulator with logging (`DEBUG_LOG=true` in `AutoScrollService` +
`adb logcat -s AutoScrollSvc`):

- Swipe activation **depends on the app emitting `TYPE_VIEW_SCROLLED`** with
  scroll magnitude. A single physical gesture generates a burst of events with `scrollDeltaY`
  of mixed sign → that's why the detector uses **debounce** (it does not count the burst as several)
  and **does not filter by sign** (it counts any significant vertical scroll).
- **It works** in `RecyclerView`-based feeds (TikTok, Instagram Reels, and the Shorts *shelf*):
  verified that it activates and starts the `WellbeingService`.
- **It does NOT work** in the **YouTube Shorts fullscreen player**: it uses a custom pager that only
  emits `TYPE_WINDOW_CONTENT_CHANGED` (too noisy to detect swipes), zero
  `TYPE_VIEW_SCROLLED`. It is an inherent limitation of detection via accessibility
  events over heterogeneous third-party apps.
- **The 3-finger tap is the reliable universal activator** (system-level gesture,
  independent of the app) — recommended for YouTube Shorts.
- **The "like" (double-tap) in the Shorts player is also not exposed** as `TYPE_VIEW_CLICKED`
  / `TYPE_VIEW_LONG_CLICKED` to the accessibility service → the interaction pause **does
  not fire** for a like in Shorts. Verified with `e2e_youtube_session.ps1` (it falls back to the
  `E2E_INTERACT` hook). In `RecyclerView` feeds (TikTok / IG Reels) a tap usually does emit the
  event. The pause logic itself (production path) is verified all the same by the hook.
- **Session cut-off:** when leaving the session's app toward the launcher or another app that is not
  enabled (`TYPE_WINDOW_STATE_CHANGED`), auto-scroll stops. Transient system UI packages
  (`com.android.systemui`, `android`) are ignored.

---

## 7. Environment notes

- **Daemon JDK:** 21 (via foojay-resolver). App `jvmTarget`: 17.
- **AGP 9 / Gradle 9.5.1 / Kotlin 2.3.21 / KSP / Room 2.8.4.**
- If a JVM test hangs, check that it doesn't mix `runTest` (virtual clock) with real IO:
  the DataStore tests use `runBlocking`; the ViewModel ones use a fake + `runTest`.
