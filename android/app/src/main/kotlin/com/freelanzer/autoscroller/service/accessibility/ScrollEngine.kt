package com.freelanzer.autoscroller.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Motor de scroll: orquesta la repetición del gesto de swipe vertical sobre la pantalla.
 *
 *  - El intervalo se lee de [intervalProvider] **en cada ciclo**, de modo que un cambio
 *    en la configuración aplica al próximo tick sin reiniciar el motor.
 *  - Cada swipe ejecutado dispara [onScrollPerformed] (alimenta el contador de la UI).
 *
 * No conoce el estado de la app ni la fuente del trigger; el [AutoScrollService] lo arranca
 * cuando observa [com.freelanzer.autoscroller.domain.controller.ScrollState.Scrolling].
 */
class ScrollEngine(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val onScrollPerformed: () -> Unit,
) {

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Inicia el bucle de scroll. Idempotente: si ya está corriendo, no hace nada. */
    fun start(intervalProvider: () -> Long) {
        if (isRunning) return
        job = scope.launch {
            // Pequeño margen para que el usuario abandone la app antes del primer swipe.
            delay(STARTUP_DELAY_MS)
            while (isActive) {
                performSwipeUp()
                onScrollPerformed()
                delay(intervalProvider().coerceAtLeast(MIN_INTERVAL_MS))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun performSwipeUp() {
        val (width, height) = screenSize() ?: return
        val midX = width / 2f
        val startY = height * SWIPE_START_RATIO
        val endY = height * SWIPE_END_RATIO

        val path = Path().apply {
            moveTo(midX, startY)
            lineTo(midX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS))
            .build()

        service.dispatchGesture(gesture, /* callback = */ null, /* handler = */ null)
    }

    private fun screenSize(): Pair<Int, Int>? {
        val wm = service.getSystemService(WindowManager::class.java) ?: return null
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private companion object {
        const val STARTUP_DELAY_MS: Long = 800L
        const val SWIPE_DURATION_MS: Long = 250L
        const val SWIPE_START_RATIO: Float = 0.80f
        const val SWIPE_END_RATIO: Float = 0.20f
        const val MIN_INTERVAL_MS: Long = 200L
    }
}
