# Onboarding del proyecto (para agentes)

## Qué es

**AutoScroller**: app Android de accesibilidad/bienestar que automatiza el scroll en feeds de
video vertical (YouTube Shorts, TikTok, Reels) inyectando swipes vía un `AccessibilityService`.
Sin red, sin servidores: todo local. Spec original en
[`../../especificaciones_autoscroller.md`](../../especificaciones_autoscroller.md).

## Stack y entorno

- **Kotlin** + Jetpack Compose + Material 3 · **Hilt** (DI) · Coroutines/Flow · **DataStore**
  (preferencias) · **Room** (historial de uso) · Navigation Compose.
- **AGP 9.2 / Gradle 9.5 / Kotlin 2.3 / KSP / Room 2.8** · minSdk 26 · target/compileSdk 36.
- **JDK:** daemon corre con JDK 21; `jvmTarget` de la app = 17.
- **Version catalog** en `gradle/libs.versions.toml` — **no hardcodear versiones** en los
  `build.gradle.kts`.
- **AGP 9 = built-in Kotlin:** el bloque `plugins {}` de `app/build.gradle.kts` NO aplica
  `org.jetbrains.kotlin.android`. La config de Kotlin va en el bloque top-level
  `kotlin { compilerOptions { … } }` (no `android { kotlinOptions { } }`).
- **SDK:** `C:\Users\seba\AppData\Local\Android\Sdk`. Emulador AVD: `Pixel_10_Pro_XL` (API 37).
  `local.properties` (con `sdk.dir`) está gitignored — no versionar.

## Compilar / correr / probar

```bash
cd android
./gradlew :app:installDebug               # compila + instala build debug
./gradlew :app:testDebugUnitTest          # unit tests JVM (rápidos, deterministas)
./gradlew :app:connectedDebugAndroidTest  # instrumentados Room (requiere device/emulador)
./gradlew --stop                          # matar daemons si algo se cuelga
```

Para ver el auto-scroll real o reproducir una sesión e2e por adb, ver
[`debug-hooks.md`](debug-hooks.md) y [`../TESTING.md`](../TESTING.md) §6.

## Arquitectura en una frase

Dos fuentes de verdad inyectadas por Hilt: **`ScrollController`** (estado runtime: Idle ↔
Scrolling) y **`SettingsRepository`** (persistencia DataStore). El **`AutoScrollService`**
(AccessibilityService) observa el controller, maneja el **`ScrollEngine`** (swipes) y el
**`WellbeingService`** (cronómetro foreground), y alimenta el **`SessionRecorder`** (Room).
Diagrama completo y secuencias en [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

## Mapa: "¿dónde toco para…?"

| Quiero cambiar… | Archivo(s) |
|---|---|
| El gesto de swipe (forma, duración, ratios) | `service/accessibility/ScrollEngine.kt` |
| Cuándo arranca/para o se pausa la sesión | `service/accessibility/AutoScrollService.kt` |
| La máquina de estados runtime | `domain/controller/ScrollController.kt` + `ScrollState.kt` |
| La activación por N swipes | `domain/gesture/SwipeActivationDetector.kt` (+ Service) |
| Preferencias / defaults / rangos | `data/settings/SettingsRepository.kt` |
| Persistencia de uso (esquema, queries) | `data/usage/` (Entity/Dao/Database/Repository) |
| Cronómetro / notificaciones de bienestar | `service/wellbeing/` |
| Pantallas (ajustes, EULA, selector de apps) | `ui/settings/`, `ui/eula/`, `ui/apps/` |
| Navegación / rutas | `ui/navigation/AppNavGraph.kt` + `AppRoutes.kt` |
| Config del AccessibilityService | `res/xml/accessibility_service_config.xml` |

## Estado / roadmap

MVP, configuración, bienestar y UI/onboarding **completos**. Pendiente: extensión de
navegador (Manifest V3). Las pantallas actuales son **EULA → Settings (principal) →
AppPicker** (no hay `HomeScreen`; documentación vieja puede mencionarla — está desactualizada).
