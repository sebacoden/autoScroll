package com.freelanzer.autoscroller.service.accessibility

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.freelanzer.autoscroller.domain.controller.ScrollController
import com.freelanzer.autoscroller.domain.controller.ScrollState
import com.freelanzer.autoscroller.service.wellbeing.WellbeingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Servicio de accesibilidad que orquesta el auto-scroll.
 *
 * Observa el [ScrollController] (única fuente de verdad runtime, §3.B de la especificación)
 * y según el estado:
 *  - Arranca o detiene el [ScrollEngine] que dispara los gestos.
 *  - Arranca o detiene el [WellbeingService] (foreground service del temporizador).
 *
 * Funcionalidades agregadas (fase 4):
 *  - **Trigger 3 dedos**: cuando el usuario lo habilita en Ajustes, se setea el flag
 *    `FLAG_REQUEST_MULTI_FINGER_GESTURES` dinámicamente y se escucha `onGesture` para
 *    el `GESTURE_3_FINGER_SINGLE_TAP`, que invoca `controller.toggle()`. Esto reemplaza
 *    el enfoque de overlay del spec original (más limpio: sin `SYSTEM_ALERT_WINDOW`,
 *    sin bloqueo de toques, gestión nativa por el sistema de accesibilidad).
 *  - **Pausa inteligente**: ante un `TYPE_VIEW_SCROLLED` fuera de la ventana
 *    `[lastSwipeAtMs, +PAUSE_DETECTION_WINDOW_MS]` se considera scroll manual del
 *    usuario y se invoca `controller.pause()`. Limitación conocida: detectar
 *    doble-toque (like) requiere análisis del árbol de nodos por aplicación; no
 *    implementado aún por fragilidad per-app.
 */
@AndroidEntryPoint
class AutoScrollService : AccessibilityService() {

    @Inject lateinit var controller: ScrollController
    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: ScrollEngine
    private var observerJob: Job? = null

    private val wellbeingIntent: Intent
        get() = Intent(this, WellbeingService::class.java)

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = ScrollEngine(
            service = this,
            scope = serviceScope,
            onScrollPerformed = controller::onScrollPerformed,
        )

        observerJob = controller.state
            .onEach { state -> onStateChanged(state) }
            .launchIn(serviceScope)

        // Aplica/quita el flag multi-finger según preferencia del usuario.
        // El flag y `onGesture(AccessibilityGestureEvent)` requieren API 30; en versiones
        // anteriores el feature simplemente no se ofrece.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            settingsRepository.threeFingerTriggerEnabledFlow
                .onEach(::applyMultiFingerFlag)
                .launchIn(serviceScope)
        }
    }

    private fun onStateChanged(state: ScrollState) {
        when (state) {
            ScrollState.Scrolling -> {
                engine.start(intervalProvider = { controller.intervalMillis.value })
                ContextCompat.startForegroundService(this, wellbeingIntent)
            }

            ScrollState.Idle,
            ScrollState.Paused -> {
                engine.stop()
                if (state == ScrollState.Idle) stopService(wellbeingIntent)
                // Bajo Paused mantenemos el WellbeingService corriendo: el usuario sigue
                // consumiendo contenido, el contador de bienestar debe seguir.
            }
        }
    }

    /**
     * Pausa inteligente: descarta los eventos de scroll generados por nuestro propio
     * `dispatchGesture` (dentro de la ventana posterior) y trata los demás como scroll
     * manual del usuario, activando [ScrollState.Paused].
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (controller.state.value != ScrollState.Scrolling) return

        val now = SystemClock.elapsedRealtime()
        val lastSwipe = controller.lastSwipeAtMs
        val withinOurGestureWindow = lastSwipe != 0L &&
            now - lastSwipe < PAUSE_DETECTION_WINDOW_MS
        if (withinOurGestureWindow) return

        controller.pause()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        if (gestureEvent.gestureId == GESTURE_3_FINGER_SINGLE_TAP) {
            controller.toggle()
            return true
        }
        return super.onGesture(gestureEvent)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyMultiFingerFlag(enabled: Boolean) {
        val current = serviceInfo ?: return
        val updatedFlags = if (enabled) {
            current.flags or AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
        } else {
            current.flags and AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES.inv()
        }
        if (updatedFlags == current.flags) return
        serviceInfo = current.apply { flags = updatedFlags }
    }

    override fun onInterrupt() {
        engine.stop()
        stopService(wellbeingIntent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        observerJob?.cancel()
        engine.stop()
        controller.stop()
        stopService(wellbeingIntent)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        /**
         * Ventana posterior a cada `dispatchGesture` durante la cual los eventos de scroll
         * se atribuyen a nuestro propio swipe (y no a una acción manual del usuario).
         * Cubre con holgura la duración del gesto (250 ms) más animaciones de fling.
         */
        const val PAUSE_DETECTION_WINDOW_MS: Long = 1_200L
    }
}
