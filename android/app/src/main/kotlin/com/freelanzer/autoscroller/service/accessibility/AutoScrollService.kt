package com.freelanzer.autoscroller.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.freelanzer.autoscroller.domain.controller.ScrollController
import com.freelanzer.autoscroller.domain.controller.ScrollState
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
 * Servicio de accesibilidad que ejecuta el auto-scroll.
 *
 * Observa el [ScrollController] (fuente única de verdad, ver §3.B de la especificación)
 * y arranca o detiene el [ScrollEngine] según el estado actual. El intervalo se lee de
 * `controller.intervalMillis.value` en cada tick del engine, de modo que cambios en la
 * configuración aplican sin reiniciar.
 */
@AndroidEntryPoint
class AutoScrollService : AccessibilityService() {

    @Inject lateinit var controller: ScrollController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: ScrollEngine
    private var observerJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = ScrollEngine(
            service = this,
            scope = serviceScope,
            onScrollPerformed = controller::onScrollPerformed,
        )
        observerJob = controller.state
            .onEach { state ->
                when (state) {
                    ScrollState.Scrolling -> engine.start(
                        intervalProvider = { controller.intervalMillis.value },
                    )
                    ScrollState.Idle, ScrollState.Paused -> engine.stop()
                }
            }
            .launchIn(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Reservado para fases futuras: detección de doble-toque (like) y scroll manual
        // para activar ScrollState.Paused. Ver §3.B de la especificación.
    }

    override fun onInterrupt() {
        engine.stop()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        observerJob?.cancel()
        engine.stop()
        controller.stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
