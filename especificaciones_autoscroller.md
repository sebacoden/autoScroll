# Especificaciones Técnicas: Proyecto Auto-Scroller

Este documento contiene la arquitectura, requisitos técnicos y consideraciones de diseño para el desarrollo de una aplicación móvil (Android) y una extensión de navegador (Chrome/Edge/Firefox) de auto-scroll para contenido tipo Reels/TikToks.

---

## 1. Objetivo del Proyecto
Crear una herramienta de automatización de scroll ("Auto-scroller") para plataformas de video vertical que mejore la accesibilidad y el bienestar digital del usuario.

## 2. Tecnologías Recomendadas
* **Móvil (Android):** Kotlin.
* **Navegador:** JavaScript (ES6+), Manifest V3.
* **Arquitectura:** Basada en servicios de fondo, detectores de eventos y almacenamiento local.

## 3. Especificaciones Técnicas (Android - Kotlin)
### A. Motor de Automatización
* **Tecnología:** `AccessibilityService`.
* **Simulación de gestos:** `dispatchGesture` o `performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)`.
* **Detección de contenido:** Observar `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED` en lugar de procesamiento de píxeles para optimizar rendimiento y batería.

### B. Funcionalidades Core
* **Activador:** Detección de "3 dedos" mediante `WindowManager` overlay (capa transparente) capturando eventos de `MotionEvent` con `event.pointerCount == 3`.
* **Control de flujo:** Máquina de estados (Idle, Scrolling, Paused).
* **Pausa inteligente:** Detectar el "doble toque" (Like) y el "scroll manual" (hacia atrás) para pausar la automatización mediante el análisis del árbol de nodos.

### C. Bienestar Digital
* **Persistencia:** Jetpack DataStore para guardar configuraciones (`limite_tiempo`, `alerta_activa`).
* **Temporizador:** Foreground Service para evitar la suspensión por parte del sistema.
* **Advertencia:** Notificación de alta prioridad o Overlay flotante tras alcanzar el límite configurado (default: 30 min).

## 4. Especificaciones Técnicas (Navegador - JS)
### A. Arquitectura
* **Content Scripts:** Inyección en `tiktok.com`, `instagram.com`, `youtube.com/shorts`.
* **Observación:** `MutationObserver` para detectar cambios en el DOM tras la carga dinámica de nuevos videos.

### B. Automatización
* **Detección de video:** Escucha del evento `ended` en el elemento `<video>`.
* **Acción:** Disparo de evento de teclado (`ArrowDown`) o `window.scrollBy`.

## 5. Consideraciones Legales y de Licencia
* **Licencia:** Apache License 2.0 (recomendada por protección contra patentes y cláusula de exclusión de garantía).
* **Monetización:** Modelo Freemium, anuncios (AdMob), o funciones PRO.
* **Protección legal:**
    * Implementar pantalla de EULA (Términos de uso) al primer inicio.
    * Descargo de responsabilidad ("as-is") sobre el bienestar digital.
    * Protección de marca mediante registro de nombre/logo (no mediante licencia de software).

## 6. Hoja de Ruta de Desarrollo
1.  **MVP:** Implementar `AccessibilityService` básico para scroll automático (intervalo 4s fijo).
2.  **Configuración:** Integrar DataStore para variables dinámicas (tiempo, alertas).
3.  **Bienestar:** Implementar el contador de tiempo y la lógica de advertencia.
4.  **UI/UX:** Diseño de interfaz para ajustes en la app.
5.  **Extensión:** Portar la lógica de detección de video (evento `ended`) a la extensión de navegador.

---
*Documento generado para uso compartido con herramientas de IA asistida.*
