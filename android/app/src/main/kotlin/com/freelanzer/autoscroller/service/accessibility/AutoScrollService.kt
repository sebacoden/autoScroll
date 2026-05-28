package com.freelanzer.autoscroller.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
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
 * El intervalo se lee de `controller.intervalMillis.value` en cada tick del engine, por lo
 * que los cambios de configuración aplican sin reiniciar.
 */
@AndroidEntryPoint
class AutoScrollService : AccessibilityService() {

    @Inject lateinit var controller: ScrollController

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
                stopService(wellbeingIntent)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO(fase 4): pausa inteligente. Estrategia:
        //  - Mantener `lastSwipeAtMs` actualizado por ScrollEngine.
        //  - En TYPE_VIEW_SCROLLED fuera de la ventana [lastSwipeAtMs, +400ms]
        //    se considera scroll manual → controller.pause().
        //  - En doble TYPE_VIEW_CLICKED con misma fuente dentro de 300ms → like detectado → pause().
        // La detección es por-app y frágil; se implementará con tests manuales por target.
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
}
