package com.freelanzer.autoscroller.domain.gesture

/**
 * Igual que antes pero con soporte opcional para una "señal global" que el caller
 * puede inyectar (p. ej. heurística desde AccessibilityService). Todos los nombres
 * y la lógica temporal/debounce se mantienen.
 */
class SwipeActivationDetector(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val minGapMs: Long = DEFAULT_MIN_GAP_MS,
) {

    // --- estado original (nombres y semántica idénticos) ---
    private var count: Int = 0
    private var lastSwipeAtMs: Long = 0L
    private var hasPrevious: Boolean = false

    // --- NUEVO: señal global y modo de activación (NO rompe comportamiento por defecto) ---
    enum class ActivationMode {
        SWIPES_ONLY, // COMPORTAMIENTO POR DEFECTO: solo el conteo de swipes
        GLOBAL_ONLY, // solo la señal global activa
        EITHER,      // cualquiera de los dos activa
        BOTH         // requiere ambos
    }

    private var activationMode: ActivationMode = ActivationMode.SWIPES_ONLY
    private var globalGestureDetected: Boolean = false
    private var globalConfidence: Float = 0f
    private var globalConfidenceThreshold: Float = 0.6f

    /**
     * Setea la forma en la que se combinan el conteo de swipes y la señal global.
     * Por defecto es SWIPES_ONLY para mantener el comportamiento actual.
     */
    fun setActivationMode(mode: ActivationMode) {
        activationMode = mode
    }

    /**
     * Inyecta/actualiza la señal global (p. ej. heurística externa). No modifica la
     * lógica de conteo; solo se evalúa junto al resultado de los swipes según el mode.
     *
     * confidence: 0..1
     */
    fun setGlobalGesture(detected: Boolean, confidence: Float = 1f) {
        globalGestureDetected = detected
        globalConfidence = confidence
    }

    fun clearGlobalGesture() {
        globalGestureDetected = false
        globalConfidence = 0f
    }

    fun setGlobalConfidenceThreshold(threshold: Float) {
        globalConfidenceThreshold = threshold
    }

    // --- helper privado para centralizar la regla de combinación ---
    private fun resolveActivation(swipesActivated: Boolean): Boolean {
        val globalDetected = globalGestureDetected && globalConfidence >= globalConfidenceThreshold
        return when (activationMode) {
            ActivationMode.SWIPES_ONLY -> swipesActivated
            ActivationMode.GLOBAL_ONLY -> globalDetected
            ActivationMode.EITHER -> swipesActivated || globalDetected
            ActivationMode.BOTH -> swipesActivated && globalDetected
        }
    }

    /**
     * Registra un scroll vertical en [elapsedRealtimeMs] con el umbral [requiredSwipes].
     * Mantiene la lógica original; si se alcanza el umbral resetea y devuelve true.
     *
     * NOTA: la firma y el comportamiento por defecto no cambian:
     * - si activationMode == SWIPES_ONLY la función se comporta exactamente como antes.
     * - si hay una señal global y el modo lo permite, la activación puede venir por esa vía.
     */
    fun onSwipeUp(elapsedRealtimeMs: Long, requiredSwipes: Int): Boolean {
        if (hasPrevious) {
            val gap = elapsedRealtimeMs - lastSwipeAtMs
            // Misma ráfaga (un mismo gesto físico): ignorar sin alterar el conteo.
            if (gap < minGapMs) {
                // antigua rama: retornaba false inmediatamente.
                // Ahora: evaluamos también la señal global (si corresponde al mode).
                return resolveActivation(swipesActivated = false)
            }
            // Gesto nuevo dentro de la ventana → continúa la secuencia; fuera → reinicia.
            count = if (gap <= windowMs) count + 1 else 1
        } else {
            count = 1
        }
        lastSwipeAtMs = elapsedRealtimeMs
        hasPrevious = true

        val swipesActivated = if (count >= requiredSwipes) {
            reset()
            true
        } else {
            false
        }

        return resolveActivation(swipesActivated)
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