package com.freelanzer.autoscroller.domain.gesture

/**
 * Detecta la secuencia de "N swipes hacia arriba en una ventana de tiempo" que activa el
 * auto-scroll cuando el usuario está dentro de una app habilitada.
 *
 * Clase pura (sin dependencias de Android): el caller pasa la marca temporal monotónica de
 * cada swipe y el umbral [requiredSwipes] **vigente** (configurable por el usuario), así es
 * testeable en JVM puro y el umbral puede cambiar en runtime sin recrear el detector.
 *
 *  - Cada swipe dentro de [windowMs] del anterior incrementa el contador.
 *  - Si pasa más de [windowMs] entre dos swipes, la secuencia se reinicia.
 *  - Al alcanzar `requiredSwipes`, [onSwipeUp] devuelve `true` una vez y resetea.
 */
class SwipeActivationDetector(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    private var count: Int = 0
    private var lastSwipeAtMs: Long = 0L
    private var hasPrevious: Boolean = false

    /**
     * Registra un swipe hacia arriba en [elapsedRealtimeMs] con el umbral [requiredSwipes].
     * @return `true` si con este swipe se completó la secuencia de activación.
     */
    fun onSwipeUp(elapsedRealtimeMs: Long, requiredSwipes: Int): Boolean {
        val withinWindow = hasPrevious && elapsedRealtimeMs - lastSwipeAtMs <= windowMs
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

    /** Reinicia la secuencia (p. ej. al arrancar por otra vía o cambiar de app). */
    fun reset() {
        count = 0
        lastSwipeAtMs = 0L
        hasPrevious = false
    }

    companion object {
        const val DEFAULT_WINDOW_MS: Long = 3_000L
    }
}
