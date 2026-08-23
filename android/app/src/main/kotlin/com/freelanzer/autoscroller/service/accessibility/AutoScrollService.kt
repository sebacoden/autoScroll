package com.freelanzer.autoscroller.service.accessibility

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.freelanzer.autoscroller.BuildConfig
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.freelanzer.autoscroller.data.usage.UsageRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
    @Inject lateinit var usageRepository: UsageRepository

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

    /**
     * Receiver de control para los tests e2e — **solo registrado en builds debug**. Permite
     * disparar el inicio/fin de sesión y simular una interacción del usuario desde `adb`,
     * de forma determinista (el tap de 3 dedos no es simulable y la activación por swipes no
     * es confiable en todas las apps). No existe en release. Ver `scripts/e2e_youtube_session.ps1`.
     */
    private var debugControlReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = ScrollEngine(
            service = this,
            scope = serviceScope,
            onScrollPerformed = controller::onScrollPerformed,
        )

        if (BuildConfig.DEBUG) registerDebugControlReceiver()

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
                logd { "SESIÓN INICIADA en '$lastForegroundPackage'" }
            }

            ScrollState.Idle -> {
                engine.stop()
                val swipes = controller.scrollCount.value
                sessionRecorder.onSessionEnded(swipeCount = swipes)
                stopService(wellbeingIntent)
                logd { "SESIÓN TERMINADA ('$activeSessionPackage', $swipes swipes)" }
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
                    logd { "salió de la app de sesión → '${event.packageName}', deteniendo" }
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
        if (isUserInteraction) {
            logd { "interacción detectada (${eventTypeName(event.eventType)})" }
            engine.notifyUserInteraction(pauseOnTouchMs)
        }
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

        val isScrollEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        // 👇 clave: aceptar ambos tipos de eventos
        if (!isScrollEvent && !isContentChange) return

        if (lastForegroundPackage !in activationApps) {
            swipeActivationDetector.reset()
            return
        }

        val significant = when {
            // Caso ideal: scroll real con delta
            isScrollEvent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                kotlin.math.abs(event.scrollDeltaY) >= MIN_SWIPE_DELTA_PX
            }

            // Scroll sin delta (API vieja o raro)
            isScrollEvent -> true

            // 👇 CLAVE para dispositivos físicos
            isContentChange -> true

            else -> false
        }

        if (!significant) return

        // 👇 log MUY útil para debug real
        logd {
            "event=${eventTypeName(event.eventType)} " +
                    "deltaY=${if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else "NA"} " +
                    "pkg=$lastForegroundPackage"
        }

        if (swipeActivationDetector.onSwipeUp(now, requiredSwipes)) {
            logd { "activación por $requiredSwipes swipes en '$lastForegroundPackage'" }
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
        debugControlReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugControlReceiver = null
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Registra el receiver de control e2e (solo debug). Acciones:
     *  - [ACTION_START]: inicia la sesión; extra opcional `pkg` fuerza el paquete atribuido
     *    (útil para que la sesión quede registrada a nombre de YouTube).
     *  - [ACTION_STOP]: termina la sesión.
     *  - [ACTION_INTERACT]: simula una interacción del usuario (like/toque) → pausa con
     *    auto-resume, igual que [handleUserInteractionWhileScrolling].
     */
    private fun registerDebugControlReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_START -> {
                        intent.getStringExtra(EXTRA_PACKAGE)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { lastForegroundPackage = it }
                        logd { "[debug] ACTION_START (pkg='$lastForegroundPackage')" }
                        controller.start()
                    }
                    ACTION_STOP -> {
                        logd { "[debug] ACTION_STOP" }
                        controller.stop()
                    }
                    ACTION_INTERACT -> {
                        logd { "[debug] ACTION_INTERACT (simula like/toque)" }
                        engine.notifyUserInteraction(pauseOnTouchMs)
                    }
                    ACTION_DUMP -> {
                        logd { "[debug] ACTION_DUMP (volcando Room)" }
                        serviceScope.launch { dumpUsageDatabase() }
                    }
                    ACTION_CLEAR -> {
                        logd { "[debug] ACTION_CLEAR (borrando historial Room)" }
                        serviceScope.launch { usageRepository.clear() }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_START)
            addAction(ACTION_STOP)
            addAction(ACTION_INTERACT)
            addAction(ACTION_DUMP)
            addAction(ACTION_CLEAR)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        debugControlReceiver = receiver
        logd { "[debug] receiver de control e2e registrado" }
    }

    /**
     * Vuelca el contenido de la base de uso (Room) a logcat **leyendo por el camino real de
     * la app** (`UsageRepository`), no por SQLite crudo — así el dump verifica también que
     * las queries de lectura devuelven lo persistido. Solo se invoca desde ACTION_DUMP (debug).
     */
    private suspend fun dumpUsageDatabase() {
        val sessions = usageRepository.observeSessions().first()
        val total = usageRepository.observeTotalSwipes().first()
        val byApp = usageRepository.observeUsageByApp().first()
        Log.d(DB_TAG, "==== DUMP Room: ${sessions.size} sesiones, total $total swipes ====")
        sessions.forEach { s ->
            Log.d(
                DB_TAG,
                "sesion id=${s.id} pkg=${s.appPackage} swipes=${s.swipeCount} " +
                    "dur=${s.endTime - s.startTime}ms start=${s.startTime} end=${s.endTime}",
            )
        }
        byApp.forEach { u ->
            Log.d(DB_TAG, "porApp pkg=${u.appPackage} dur=${u.totalDurationMs}ms sesiones=${u.sessionCount}")
        }
        Log.d(DB_TAG, "==== FIN DUMP ====")
    }

    /** Log de debug barato: el lambda solo se evalúa en builds debug. */
    private inline fun logd(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private fun eventTypeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "scrolled"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "clicked"
        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "longClicked"
        else -> type.toString()
    }

    private companion object {
        const val TAG: String = "AutoScrollSvc"
        const val DB_TAG: String = "AutoScrollUsage"

        // Acciones del receiver de control e2e (solo debug). Ver registerDebugControlReceiver().
        const val ACTION_START: String = "com.freelanzer.autoscroller.E2E_START"
        const val ACTION_STOP: String = "com.freelanzer.autoscroller.E2E_STOP"
        const val ACTION_INTERACT: String = "com.freelanzer.autoscroller.E2E_INTERACT"
        const val ACTION_DUMP: String = "com.freelanzer.autoscroller.E2E_DUMP"
        const val ACTION_CLEAR: String = "com.freelanzer.autoscroller.E2E_CLEAR"
        const val EXTRA_PACKAGE: String = "pkg"

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
