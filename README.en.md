# AutoScroller

> 🌐 [Español](README.md) · **English**

An **accessibility and digital wellbeing** tool that automates scrolling through vertical
video feeds (YouTube Shorts, TikTok, Instagram Reels, Facebook). Designed for people with
reduced mobility and for anyone who wants to consume content without the repetitive swipe
gesture — with built-in time limits and wellbeing reminders.

> Current status: **functional Android app** (MVP → wellbeing → UI → onboarding phases
> complete). The browser extension is left for a later phase. License: Apache 2.0.

---

## What does it do?

- **Auto-scroll** of the vertical video feed: automatically advances to the next video every N seconds.
- **Activated with a gesture**, without constantly touching the screen:
  - **Three-finger tap** anywhere (universal, system-level trigger).
  - **N upward swipes** within a video app from your list (configurable).
- **Smart pause with auto-resume:** if you tap or scroll manually (e.g., to like or read a
  comment), auto-scroll is suspended and **resumes on its own** after a few seconds without
  interaction. It does not pull you out of automatic mode.
- **Digital wellbeing:** background usage timer and **reminder notification** when you reach
  your time limit (default 30 min). Per-app usage history, **100% local**.
- **Configurable:** interval (1–30 s), time limit (5–180 min), apps where it activates,
  number of swipes, and pause seconds.

---

## How does it work? (in brief)

The core is an Android **`AccessibilityService`**. Instead of processing pixels, it observes
accessibility events and **injects swipe gestures** at the system level with `dispatchGesture`.
That lets it scroll third-party apps without accessing their content or the network.

```
Activación (3 dedos / swipes)
        │
        ▼
ScrollController (estado: Idle ↔ Scrolling)
        │  observa
        ▼
AutoScrollService ──▶ ScrollEngine ──▶ swipe ↑ cada N s (dispatchGesture)
        │                   ▲
        │                   └── pausa temporal al tocar (auto-resume)
        ├──▶ WellbeingService (cronómetro + aviso de límite)
        └──▶ SessionRecorder ──▶ Room (historial de uso, local)
```

Leaving the video app **ends the session** automatically. The complete technical
documentation (layers, state machine, sequence diagrams, design decisions) is in
**[android/docs/ARCHITECTURE.en.md](android/docs/ARCHITECTURE.en.md)**.

---

## Privacy

All data is **local** (preferences in DataStore + history in Room, in the app's private
storage). **There are no servers and no network.** The accessibility service does not read
personal content: it only observes event types (scroll/click/window change) to orchestrate
auto-scroll and the pause. On first launch an **EULA** is shown with the disclaimer and the
data policy.

---

## Build and install

Requirements: **JDK 17+**, Android SDK (compileSdk 36), an emulator or device (minSdk 26).

```bash
cd android
./gradlew :app:installDebug      # compila e instala el build debug
```

When opening the app: accept the EULA → grant notifications → **enable the accessibility
service** (there is a direct deep link from the settings screen).

> On Windows: don't run Gradle from the terminal and an Android Studio sync/build at the same
> time (they fight over `R.jar` and the build hangs).

---

## Testing

Layered suite (JVM unit + instrumented Room + semi-automated adb e2e). How to run it and what
it covers: **[android/TESTING.en.md](android/TESTING.en.md)**.

```bash
cd android
./gradlew :app:testDebugUnitTest          # unit tests (JVM, rápidos)
./gradlew :app:connectedDebugAndroidTest  # instrumentados (requiere device/emulador)
```

---

## Repository structure

```
autoScroll/
├── README.md                       ← este archivo
├── especificaciones_autoscroller.md  Especificación original del proyecto
├── LICENSE                         Apache 2.0
└── android/                        Módulo Android (Kotlin + Compose)
    ├── app/src/main/kotlin/…       Código de la app
    ├── docs/                       Documentación técnica (arquitectura, diagramas)
    ├── skills/                     Contexto para agentes de IA que trabajen en el repo
    ├── scripts/                    Scripts de testing/e2e por adb (PowerShell)
    └── TESTING.md                  Guía de testing
```

---

## Roadmap

- [x] **MVP** — `AccessibilityService` with auto-scroll (configurable interval).
- [x] **Configuration** — DataStore (interval, limit, triggers).
- [x] **Wellbeing** — foreground timer + limit reminder + history (Room).
- [x] **UI/UX** — onboarding (EULA), settings screen, app picker.
- [ ] **Browser extension** — port the logic to Manifest V3 (Chrome/Edge/Firefox).

---

## License

[Apache License 2.0](LICENSE).
