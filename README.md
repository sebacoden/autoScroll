# AutoScroller

Herramienta de **accesibilidad y bienestar digital** que automatiza el scroll en feeds de
video vertical (YouTube Shorts, TikTok, Instagram Reels, Facebook). Pensada para personas con
movilidad reducida y para quienes quieren consumir contenido sin el gesto repetitivo del
swipe — con límites de tiempo y avisos de bienestar incorporados.

> Estado actual: **app Android funcional** (fases MVP → bienestar → UI → onboarding
> completas). La extensión de navegador queda para una fase posterior. Licencia: Apache 2.0.

---

## ¿Qué hace?

- **Auto-scroll** del feed de video vertical: avanza solo al siguiente video cada N segundos.
- **Se activa con un gesto**, sin tocar la pantalla constantemente:
  - **Tap con 3 dedos** en cualquier parte (activador universal, a nivel sistema).
  - **N swipes hacia arriba** dentro de una app de video de tu lista (configurable).
- **Pausa inteligente con auto-reanudación:** si tocás o hacés scroll manual (p. ej. para dar
  like o leer un comentario), el auto-scroll se suspende y **reanuda solo** tras unos segundos
  sin interacción. No te saca del modo automático.
- **Bienestar digital:** cronómetro de uso en segundo plano y **notificación de aviso** al
  llegar a tu límite de tiempo (default 30 min). Historial de uso por app, **100% local**.
- **Configurable:** intervalo (1–30 s), límite de tiempo (5–180 min), apps donde se activa,
  cantidad de swipes y segundos de pausa.

---

## ¿Cómo funciona? (en breve)

El núcleo es un **`AccessibilityService`** de Android. En lugar de procesar píxeles, observa
eventos de accesibilidad e **inyecta gestos de swipe** a nivel sistema con `dispatchGesture`.
Eso le permite hacer scroll en apps de terceros sin acceso a su contenido ni a la red.

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

Salir de la app de video **termina la sesión** automáticamente. La documentación técnica
completa (capas, máquina de estados, diagramas de secuencia, decisiones de diseño) está en
**[android/docs/ARCHITECTURE.md](android/docs/ARCHITECTURE.md)**.

---

## Privacidad

Todos los datos son **locales** (preferencias en DataStore + historial en Room, en el
almacenamiento privado de la app). **No hay servidores ni red.** El servicio de accesibilidad
no lee contenido personal: solo observa tipos de evento (scroll/click/cambio de ventana) para
orquestar el auto-scroll y la pausa. Al primer inicio se muestra un **EULA** con el descargo
de responsabilidad y la política de datos.

---

## Compilar e instalar

Requisitos: **JDK 17+**, Android SDK (compileSdk 36), un emulador o device (minSdk 26).

```bash
cd android
./gradlew :app:installDebug      # compila e instala el build debug
```

Al abrir la app: aceptar el EULA → conceder notificaciones → **habilitar el servicio de
accesibilidad** (hay un deep-link directo desde la pantalla de ajustes).

> En Windows: no correr Gradle por terminal y un sync/build de Android Studio a la vez
> (pelean por `R.jar` y el build se cuelga).

---

## Testing

Suite por niveles (unit JVM + instrumentado Room + e2e semi-automatizado por adb). Cómo
correrlo y qué cubre: **[android/TESTING.md](android/TESTING.md)**.

```bash
cd android
./gradlew :app:testDebugUnitTest          # unit tests (JVM, rápidos)
./gradlew :app:connectedDebugAndroidTest  # instrumentados (requiere device/emulador)
```

---

## Estructura del repositorio

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

## Hoja de ruta

- [x] **MVP** — `AccessibilityService` con auto-scroll (intervalo configurable).
- [x] **Configuración** — DataStore (intervalo, límite, activadores).
- [x] **Bienestar** — cronómetro en foreground + aviso de límite + historial (Room).
- [x] **UI/UX** — onboarding (EULA), pantalla de ajustes, selector de apps.
- [ ] **Extensión de navegador** — portar la lógica a Manifest V3 (Chrome/Edge/Firefox).

---

## Licencia

[Apache License 2.0](LICENSE).
