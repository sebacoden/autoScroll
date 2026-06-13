package com.freelanzer.autoscroller.domain.gesture

/**
 * Detecta la secuencia de "N swipes hacia arriba en una ventana de tiempo" que el usuario
 * usa para **activar** el auto-scroll manualmente sin tocar la app.
 *
 * Clase pura (sin dependencias de Android): el caller le pasa la marca temporal
 * monotónica de cada swipe, así es testeable en JVM puro.
 *
 *  - Cada swipe hacia arriba dentro de [windowMs] del anterior incrementa el contador.
 *  - Si pasa más de [windowMs] entre dos swipes, la secuencia se reinicia (el primero
 *    cuenta como swipe 1 de una nueva tanda).
 *  - Al alcanzar [requiredSwipes], [onSwipeUp] devuelve `true` UNA vez y resetea el
 *    contador (evita re-disparos en cascada).
 */
class SwipeActivationDetector(
    private val requiredSwipes: Int = DEFAULT_REQUIRED_SWIPES,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    private var count: Int = 0
    private var lastSwipeAtMs: Long = 0L
    private var hasPrevious: Boolean = false

    /**
     * Registra un swipe hacia arriba en [elapsedRealtimeMs].
     * @return `true` si con este swipe se completó la secuencia de activación.
     */
    fun onSwipeUp(elapsedRealtimeMs: Long): Boolean {
        // Usamos un flag explícito en vez de `lastSwipeAtMs != 0L`: el timestamp 0 es un
        // valor válido (en tests; `SystemClock.elapsedRealtime()` es ~0 justo tras el boot).
        val withinWindow = hasPrevious &&
            elapsedRealtimeMs - lastSwipeAtMs <= windowMs
        count = if (withinWindow) count + 1 else 1
        lastSwipeAtMs = elapsedRealtimeMs
        hasPrevious = true

        return if (count >= requiredSwipes) {
            reset()
            true
        } else {
            false
        }
    }

    /** Reinicia la secuencia (p. ej. cuando el auto-scroll arranca por otra vía). */
    fun reset() {
        count = 0
        lastSwipeAtMs = 0L
        hasPrevious = false
    }

    companion object {
        const val DEFAULT_REQUIRED_SWIPES: Int = 3
        const val DEFAULT_WINDOW_MS: Long = 3_000L
    }
}
