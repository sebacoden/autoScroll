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
import java.util.concurrent.TimeUnit
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
 * Observa el [ScrollController] (única fuente de verdad runtime) y según el estado
 * arranca/detiene el [ScrollEngine] (gestos) y el [WellbeingService] (temporizador FG).
 *
 * Activación:
 *  - **N swipes hacia arriba** (default): dentro de una app de la allowlist
 *    ([activationApps]), N swipes (configurable) cuentan con [SwipeActivationDetector] →
 *    `controller.start()`.
 *  - **Tap 3 dedos**: `FLAG_REQUEST_MULTI_FINGER_GESTURES` (API 30+) + `onGesture`.
 *
 * Pausa por interacción (auto-resume): con el auto-scroll activo, un scroll manual o un
 * toque (fuera de la ventana de nuestro propio gesto) suspende los swipes durante
 * `pauseOnTouchMs` vía [ScrollEngine.notifyUserInteraction]; si no hay más interacción,
 * reanuda solo. No cambia el estado lógico (sigue `Scrolling`).
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
    @Volatile private var requiredSwipes: Int = SettingsRepository.DEFAULT_REQUIRED_SWIPES
    @Volatile private var pauseOnTouchMs: Long =
        TimeUnit.SECONDS.toMillis(SettingsRepository.DEFAULT_PAUSE_ON_TOUCH_SEC.toLong())
    @Volatile private var activationApps: Set<String> =
        com.freelanzer.autoscroller.data.settings.DefaultActivationApps.PACKAGES

    /** Último paquete en foreground, capturado de los eventos; se usa al abrir una sesión. */
    @Volatile private var lastForegroundPackage: String = ""

    /** App donde se activó la sesión actual; al salir de ella se detiene el auto-scroll. */
    @Volatile private var activeSessionPackage: String = ""

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

        // Settings de activación / pausa, observadas en vivo.
        settingsRepository.swipeActivationEnabledFlow
            .onEach { enabled ->
                swipeActivationEnabled = enabled
                if (!enabled) swipeActivationDetector.reset()
            }
            .launchIn(serviceScope)
        settingsRepository.requiredSwipesToActivateFlow
            .onEach { requiredSwipes = it }
            .launchIn(serviceScope)
        settingsRepository.pauseOnTouchSecondsFlow
            .onEach { pauseOnTouchMs = TimeUnit.SECONDS.toMillis(it.toLong()) }
            .launchIn(serviceScope)
        settingsRepository.activationAppPackagesFlow
            .onEach { activationApps = it }
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
                activeSessionPackage = lastForegroundPackage
                sessionRecorder.onSessionStarted(lastForegroundPackage)
                engine.start(intervalProvider = { controller.intervalMillis.value })
                ContextCompat.startForegroundService(this, wellbeingIntent)
            }

            ScrollState.Idle -> {
                engine.stop()
                sessionRecorder.onSessionEnded(swipeCount = controller.scrollCount.value)
                stopService(wellbeingIntent)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        event.packageName?.toString()?.takeIf { it.isNotBlank() }?.let {
            lastForegroundPackage = it
        }

        val now = SystemClock.elapsedRealtime()
        when (controller.state.value) {
            ScrollState.Scrolling -> {
                if (hasLeftSessionApp(event)) {
                    controller.stop()
                } else {
                    handleUserInteractionWhileScrolling(event, now)
                }
            }
            ScrollState.Idle -> handleSwipeActivation(event, now)
        }
    }

    /**
     * Detecta si el usuario salió de la app donde se activó el auto-scroll (al launcher u
     * otra app), para terminar la sesión. Solo se evalúa en cambios de ventana
     * (`TYPE_WINDOW_STATE_CHANGED`). Se ignoran paquetes de UI de sistema transitorios
     * (barra de notificaciones, etc.) y los cambios entre apps de la allowlist.
     */
    private fun hasLeftSessionApp(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return false
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return false
        if (pkg in SYSTEM_UI_PACKAGES) return false
        // Sigue en la app de la sesión o pasó a otra app de video habilitada → no cortar.
        if (pkg == activeSessionPackage || pkg in activationApps) return false
        return true
    }

    /**
     * Con el auto-scroll activo, una interacción del usuario (scroll manual fuera de la
     * ventana de nuestro propio gesto, o un toque/click) suspende temporalmente los swipes.
     * Cada interacción extiende la pausa; al expirar, el engine reanuda solo.
     */
    private fun handleUserInteractionWhileScrolling(event: AccessibilityEvent, now: Long) {
        val isUserInteraction = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val lastSwipe = controller.lastSwipeAtMs
                val ourOwnGesture = lastSwipe != 0L && now - lastSwipe < PAUSE_DETECTION_WINDOW_MS
                !ourOwnGesture
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> true
            else -> false
        }
        if (isUserInteraction) engine.notifyUserInteraction(pauseOnTouchMs)
    }

    /**
     * Cuenta swipes verticales para activar el auto-scroll, **solo** dentro de una app de la
     * allowlist [activationApps]. El umbral [requiredSwipes] es configurable.
     *
     * No se filtra por signo de `scrollDeltaY`: en feeds tipo RecyclerView un mismo gesto
     * emite varios scrolls con deltaY de signo mezclado (fling + settle). Se cuenta cualquier
     * scroll cuya magnitud supere [MIN_SWIPE_DELTA_PX] (descarta micro-scrolls), y el
     * [SwipeActivationDetector] agrupa la ráfaga del gesto vía su debounce. En API < 28 no
     * hay delta disponible → se cuenta cualquier `TYPE_VIEW_SCROLLED`.
     */
    private fun handleSwipeActivation(event: AccessibilityEvent, now: Long) {
        if (!swipeActivationEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (lastForegroundPackage !in activationApps) {
            swipeActivationDetector.reset()
            return
        }

        val significant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            kotlin.math.abs(event.scrollDeltaY) >= MIN_SWIPE_DELTA_PX
        } else {
            true
        }
        if (!significant) return

        if (swipeActivationDetector.onSwipeUp(now, requiredSwipes)) {
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

        /** Magnitud mínima (px) de un scroll para contarlo como swipe (descarta micro-scrolls). */
        const val MIN_SWIPE_DELTA_PX: Int = 80

        /** UI de sistema que aparece transitoriamente y no debe cortar la sesión. */
        val SYSTEM_UI_PACKAGES: Set<String> = setOf(
            "com.android.systemui",
            "android",
        )
    }
}
