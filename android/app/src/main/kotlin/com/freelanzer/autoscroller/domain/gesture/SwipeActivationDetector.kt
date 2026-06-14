package com.freelanzer.autoscroller.domain.gesture

/**
 * Cuenta "N swipes verticales en una ventana de tiempo" para activar el auto-scroll dentro
 * de una app habilitada.
 *
 * Clase pura (sin Android): el caller pasa la marca temporal monotónica de cada scroll y el
 * umbral [requiredSwipes] vigente (configurable en runtime).
 *
 * **Debounce de ráfagas:** un solo gesto físico de swipe genera *varios* eventos de scroll
 * en pocos ms (fling + settle del RecyclerView, con deltaY de signo mezclado). Para que el
 * conteo mapee 1:1 con gestos del usuario, se ignoran eventos que lleguen dentro de
 * [minGapMs] del último contado. Por eso NO se filtra por signo de deltaY en el caller:
 * cualquier scroll vertical significativo cuenta, y el debounce agrupa la ráfaga.
 *
 *  - Eventos dentro de [minGapMs] del último contado → ignorados (misma ráfaga).
 *  - Si pasa más de [windowMs] entre swipes contados → la secuencia se reinicia.
 *  - Al alcanzar `requiredSwipes`, [onSwipeUp] devuelve `true` una vez y resetea.
 */
class SwipeActivationDetector(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val minGapMs: Long = DEFAULT_MIN_GAP_MS,
) {

    private var count: Int = 0
    private var lastSwipeAtMs: Long = 0L
    private var hasPrevious: Boolean = false

    /**
     * Registra un scroll vertical en [elapsedRealtimeMs] con el umbral [requiredSwipes].
     * @return `true` si con este swipe se completó la secuencia de activación.
     */
    fun onSwipeUp(elapsedRealtimeMs: Long, requiredSwipes: Int): Boolean {
        if (hasPrevious) {
            val gap = elapsedRealtimeMs - lastSwipeAtMs
            // Misma ráfaga (un mismo gesto físico): ignorar sin alterar el conteo.
            if (gap < minGapMs) return false
            // Gesto nuevo dentro de la ventana → continúa la secuencia; fuera → reinicia.
            count = if (gap <= windowMs) count + 1 else 1
        } else {
            count = 1
        }
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

        /** Gap mínimo entre swipes contados; agrupa la ráfaga de eventos de un mismo gesto. */
        const val DEFAULT_MIN_GAP_MS: Long = 400L
    }
}
