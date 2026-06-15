# Conventions and known pitfalls

> 🌐 [Español](conventions.md) · **English**

## Code conventions

- **Language:** comments, KDoc, commit messages and docs are in **Spanish**.
- **Clean and testable architecture** (explicit owner requirement): separate layers, avoid
  coupling between UI and services via injected shared state (no broadcasts or
  statics for that).
- **JVM-testable domain:** `ScrollController` and `SwipeActivationDetector` are **pure
  Kotlin** — the timestamp is injected from the caller, `SystemClock` is NOT used inside. Keep
  it that way so we can test without shadowing.
- **Repositories = interfaces** (`SettingsRepository`, `UsageRepository`) with a concrete impl
  (`DataStore…`, `Room…`) and `@Binds` in the Hilt module. Allows fakes in ViewModel tests.
- **Range validation in the repository** (contract), not in the consumer.
- **Versions:** always via the version catalog `gradle/libs.versions.toml`.
- **Diagnostic logging:** gated by `BuildConfig.DEBUG` (must not exist in release).
  Tags: `AutoScrollSvc`, `AutoScrollEngine`, `AutoScrollUsage`.

## Known pitfalls (DO NOT repeat — already paid for)

### Build / Windows
- **`R.jar` lock:** do not run Gradle from the terminal and an Android Studio sync/build at the
  same time. Symptom: task stuck on `processDebugResources` / `testDebugUnitTest` without progressing.
- **DataStore flaky on Windows:** two writes in a row can fail with
  `Unable to rename .tmp`. In tests, split into a single write.

### Tests
- **`runTest` (virtual clock) vs real IO:** do not mix. Tests that touch real DataStore →
  `runBlocking`; ViewModel tests → in-memory fake + `runTest(scheduler)` with
  `MainDispatcherRule`. Mixing them **hangs**.
- **`.first { predicate }` has no timeout** → it can hang. Use Turbine `.test{}` or
  predicates that actually differ. Watch out: `SettingsUiState.Initial` matches the derived
  defaults, so `first { it != Initial }` never differs.
- Real DataStore tests carry a JUnit `Timeout(15s)` as a safety net.

### Behavior in real apps (verified via logcat on emulator)
- **The emulator CANNOT simulate the 3-finger tap** (system-level gesture). For e2e on
  emulator, start the session with the debug hook `E2E_START` (see `debug-hooks.en.md`).
- **Swipe activation:** works in `RecyclerView` feeds (TikTok, IG Reels, the Shorts *shelf*),
  but **NOT in the YouTube Shorts fullscreen player** (it does not emit `TYPE_VIEW_SCROLLED`,
  only `TYPE_WINDOW_CONTENT_CHANGED`).
- **The "like" (double-tap) on YouTube Shorts is NOT exposed** as `TYPE_VIEW_CLICKED` to the
  service → interaction pause does not fire on a like in Shorts. In e2e the hook
  `E2E_INTERACT` is used (same production path) as a deterministic fallback.
- **The emulator does NOT ship the `sqlite3` binary** (`run-as: exec failed for sqlite3`). To
  inspect Room: Android Studio's Database Inspector, or the `E2E_DUMP` hook (dumps via
  `UsageRepository`), or pull the `.db` with `run-as … cat`.

### Design decisions that look "weird" but are intentional
- **3-finger trigger:** a `WindowManager` overlay is NOT used (fragile, requires `SYSTEM_ALERT_WINDOW`).
  We use `FLAG_REQUEST_MULTI_FINGER_GESTURES` + `onGesture` from the AccessibilityService.
- **Interaction pause does NOT change `ScrollState`:** it lives in the `ScrollEngine` (suspends the
  loop). The session stays `Scrolling` so the UI does not flicker and the wellbeing timer is not
  cut off.
- **`ScrollState.Paused` ended up reserved/unused** after replacing the permanent pause with the
  pause-with-auto-resume.
