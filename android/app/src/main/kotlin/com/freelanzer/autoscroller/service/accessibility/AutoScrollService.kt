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
import com.freelanzer.autoscroller.domain.gesture.SwipeActivationDetector
import com.freelanzer.autoscroller.domain.usage.SessionRecorder
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
 * y según el estado arranca/detiene el [ScrollEngine] (gestos) y el [WellbeingService]
 * (temporizador en foreground).
 *
 * Métodos de activación:
 *  - **Tap 3 dedos** (default): flag `FLAG_REQUEST_MULTI_FINGER_GESTURES` (API 30+) +
 *    `onGesture(GESTURE_3_FINGER_SINGLE_TAP)` → `controller.toggle()`.
 *  - **3 swipes hacia arriba** (opt-in): en `onAccessibilityEvent`, si el auto-scroll
 *    está detenido y la feature está habilitada, los swipes verticales hacia arriba se
 *    cuentan con [SwipeActivationDetector]; al llegar al umbral, `controller.start()`.
 *
 * Pausa inteligente: con el auto-scroll activo, un `TYPE_VIEW_SCROLLED` fuera de la
 * ventana posterior a nuestro propio gesto se interpreta como scroll manual → `pause()`.
 */
@AndroidEntryPoint
class AutoScrollService : AccessibilityService() {

    @Inject lateinit var controller: ScrollController
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sessionRecorder: SessionRecorder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: ScrollEngine
    private val swipeActivationDetector = SwipeActivationDetector()
    private var observerJob: Job? = null

    @Volatile private var swipeActivationEnabled: Boolean =
        SettingsRepository.DEFAULT_SWIPE_ACTIVATION_ENABLED

    /** Último paquete en foreground, capturado de los eventos; se usa al abrir una sesión. */
    @Volatile private var lastForegroundPackage: String = ""

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

        // Preferencia de activación por 3 swipes hacia arriba.
        settingsRepository.swipeActivationEnabledFlow
            .onEach { enabled ->
                swipeActivationEnabled = enabled
                if (!enabled) swipeActivationDetector.reset()
            }
            .launchIn(serviceScope)

        // Trigger 3 dedos: flag y `onGesture(AccessibilityGestureEvent)` requieren API 30.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            settingsRepository.threeFingerTriggerEnabledFlow
                .onEach(::applyMultiFingerFlag)
                .launchIn(serviceScope)
        }
    }

    private fun onStateChanged(state: ScrollState) {
        when (state) {
            ScrollState.Scrolling -> {
                swipeActivationDetector.reset()
                sessionRecorder.onSessionStarted(lastForegroundPackage)
                engine.start(intervalProvider = { controller.intervalMillis.value })
                ContextCompat.startForegroundService(this, wellbeingIntent)
            }

            ScrollState.Idle,
            ScrollState.Paused -> {
                engine.stop()
                if (state == ScrollState.Idle) {
                    sessionRecorder.onSessionEnded(swipeCount = controller.scrollCount.value)
                    stopService(wellbeingIntent)
                }
                // Bajo Paused mantenemos el WellbeingService y la sesión abiertos: el
                // usuario sigue consumiendo contenido y puede reanudar el auto-scroll.
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        event.packageName?.toString()?.takeIf { it.isNotBlank() }?.let {
            lastForegroundPackage = it
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

        val now = SystemClock.elapsedRealtime()
        when (controller.state.value) {
            ScrollState.Scrolling -> handleSmartPause(now)
            ScrollState.Idle, ScrollState.Paused -> handleSwipeActivation(event, now)
        }
    }

    /**
     * Pausa inteligente: descarta los eventos de scroll generados por nuestro propio
     * `dispatchGesture` (ventana posterior) y trata el resto como scroll manual → pausa.
     */
    private fun handleSmartPause(now: Long) {
        val lastSwipe = controller.lastSwipeAtMs
        val withinOurGestureWindow = lastSwipe != 0L &&
            now - lastSwipe < PAUSE_DETECTION_WINDOW_MS
        if (withinOurGestureWindow) return
        controller.pause()
    }

    /**
     * Cuenta swipes hacia arriba para activar el auto-scroll. "Hacia arriba" = avanzar en
     * el feed; en API 28+ se infiere del signo de `scrollDeltaY` (> 0). En versiones
     * previas no hay delta disponible, así que se cuenta cualquier scroll vertical.
     */
    private fun handleSwipeActivation(event: AccessibilityEvent, now: Long) {
        if (!swipeActivationEnabled) return

        val isUpward = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            event.scrollDeltaY > 0
        } else {
            true
        }
        if (!isUpward) return

        if (swipeActivationDetector.onSwipeUp(now)) {
            controller.start()
        }
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
