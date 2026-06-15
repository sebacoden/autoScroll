# Project onboarding (for agents)

> 🌐 [Español](project-onboarding.md) · **English**

## What it is

**AutoScroller**: an Android accessibility/wellbeing app that automates scrolling through
vertical-video feeds (YouTube Shorts, TikTok, Reels) by injecting swipes via an `AccessibilityService`.
No network, no servers: everything is local. Original spec in
[`../../especificaciones_autoscroller.md`](../../especificaciones_autoscroller.md).

## Stack and environment

- **Kotlin** + Jetpack Compose + Material 3 · **Hilt** (DI) · Coroutines/Flow · **DataStore**
  (preferences) · **Room** (usage history) · Navigation Compose.
- **AGP 9.2 / Gradle 9.5 / Kotlin 2.3 / KSP / Room 2.8** · minSdk 26 · target/compileSdk 36.
- **JDK:** the daemon runs with JDK 21; the app's `jvmTarget` = 17.
- **Version catalog** in `gradle/libs.versions.toml` — **do not hardcode versions** in the
  `build.gradle.kts` files.
- **AGP 9 = built-in Kotlin:** the `plugins {}` block in `app/build.gradle.kts` does NOT apply
  `org.jetbrains.kotlin.android`. The Kotlin config goes in the top-level block
  `kotlin { compilerOptions { … } }` (not `android { kotlinOptions { } }`).
- **SDK:** `C:\Users\seba\AppData\Local\Android\Sdk`. AVD emulator: `Pixel_10_Pro_XL` (API 37).
  `local.properties` (with `sdk.dir`) is gitignored — do not commit it.

## Build / run / test

```bash
cd android
./gradlew :app:installDebug               # compila + instala build debug
./gradlew :app:testDebugUnitTest          # unit tests JVM (rápidos, deterministas)
./gradlew :app:connectedDebugAndroidTest  # instrumentados Room (requiere device/emulador)
./gradlew --stop                          # matar daemons si algo se cuelga
```

To see the real auto-scroll or replay an e2e session over adb, see
[`debug-hooks.en.md`](debug-hooks.en.md) and [`../TESTING.en.md`](../TESTING.en.md) §6.

## Architecture in one sentence

Two sources of truth injected by Hilt: **`ScrollController`** (runtime state: Idle ↔
Scrolling) and **`SettingsRepository`** (DataStore persistence). The **`AutoScrollService`**
(AccessibilityService) observes the controller, drives the **`ScrollEngine`** (swipes) and the
**`WellbeingService`** (foreground timer), and feeds the **`SessionRecorder`** (Room).
Full diagram and sequences in [`../docs/ARCHITECTURE.en.md`](../docs/ARCHITECTURE.en.md).

## Map: "where do I change…?"

| I want to change… | File(s) |
|---|---|
| The swipe gesture (shape, duration, ratios) | `service/accessibility/ScrollEngine.kt` |
| When the session starts/stops or pauses | `service/accessibility/AutoScrollService.kt` |
| The runtime state machine | `domain/controller/ScrollController.kt` + `ScrollState.kt` |
| Activation by N swipes | `domain/gesture/SwipeActivationDetector.kt` (+ Service) |
| Preferences / defaults / ranges | `data/settings/SettingsRepository.kt` |
| Usage persistence (schema, queries) | `data/usage/` (Entity/Dao/Database/Repository) |
| Timer / wellbeing notifications | `service/wellbeing/` |
| Screens (settings, EULA, app picker) | `ui/settings/`, `ui/eula/`, `ui/apps/` |
| Navigation / routes | `ui/navigation/AppNavGraph.kt` + `AppRoutes.kt` |
| AccessibilityService config | `res/xml/accessibility_service_config.xml` |

## Status / roadmap

MVP, configuration, wellbeing, and UI/onboarding are **complete**. Pending: browser
extension (Manifest V3). The current screens are **EULA → Settings (main) →
AppPicker** (there is no `HomeScreen`; old documentation may mention it — that is outdated).
