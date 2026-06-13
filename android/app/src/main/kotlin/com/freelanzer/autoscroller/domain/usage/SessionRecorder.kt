package com.freelanzer.autoscroller.domain.usage

import com.freelanzer.autoscroller.core.di.ApplicationScope
import com.freelanzer.autoscroller.core.time.Clock
import com.freelanzer.autoscroller.data.usage.UsageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Registra sesiones de auto-scroll en el [UsageRepository].
 *
 * Mantiene el estado de la sesión en curso (inicio + app) y, al cerrarla, persiste la
 * fila con el contador de swipes. La marca de tiempo viene del [Clock] inyectable (reloj
 * de pared, para agrupar por día) — así la orquestación es testeable en JVM puro.
 *
 * Reglas:
 *  - `onSessionEnded` sin sesión activa: no-op (idempotente).
 *  - Sesiones con 0 swipes se descartan (no aportan a las métricas de bienestar y
 *    ensuciarían los promedios).
 */
@Singleton
class SessionRecorder @Inject constructor(
    private val usageRepository: UsageRepository,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private var active: Boolean = false
    private var startTime: Long = 0L
    private var appPackage: String = UNKNOWN_PACKAGE

    fun onSessionStarted(appPackage: String) {
        active = true
        startTime = clock.nowMillis()
        this.appPackage = appPackage.ifBlank { UNKNOWN_PACKAGE }
    }

    fun onSessionEnded(swipeCount: Int) {
        if (!active) return
        active = false

        val endTime = clock.nowMillis()
        val start = startTime
        val pkg = appPackage
        if (swipeCount <= 0) return

        scope.launch {
            usageRepository.recordSession(
                startTime = start,
                endTime = endTime,
                swipeCount = swipeCount,
                appPackage = pkg,
            )
        }
    }

    private companion object {
        const val UNKNOWN_PACKAGE = "unknown"
    }
}
