package com.freelanzer.autoscroller.domain.gesture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests del [SwipeActivationDetector]. Clase pura → JVM puro.
 * Usamos `minGapMs = 0` salvo en los tests de debounce, para aislar la lógica de ventana.
 *
 * Mantengo los tests originales tal cual y agrego algunos para la señal "global".
 */
class SwipeActivationDetectorTest {

    private fun detector(windowMs: Long = 3_000L, minGapMs: Long = 0L) =
        SwipeActivationDetector(windowMs = windowMs, minGapMs = minGapMs)

    @Test
    fun `three swipes within window trigger activation`() {
        val d = detector()
        assertThat(d.onSwipeUp(0L, requiredSwipes = 3)).isFalse()
        assertThat(d.onSwipeUp(1_000L, requiredSwipes = 3)).isFalse()
        assertThat(d.onSwipeUp(2_000L, requiredSwipes = 3)).isTrue()
    }

    @Test
    fun `counter resets after triggering`() {
        val d = detector()
        d.onSwipeUp(0L, 3); d.onSwipeUp(1_000L, 3)
        assertThat(d.onSwipeUp(2_000L, 3)).isTrue()
        assertThat(d.onSwipeUp(2_500L, 3)).isFalse()
        assertThat(d.onSwipeUp(3_000L, 3)).isFalse()
        assertThat(d.onSwipeUp(3_500L, 3)).isTrue()
    }

    @Test
    fun `swipe outside window restarts the sequence`() {
        val d = detector()
        d.onSwipeUp(0L, 3); d.onSwipeUp(1_000L, 3)
        assertThat(d.onSwipeUp(5_000L, 3)).isFalse()
        assertThat(d.onSwipeUp(6_000L, 3)).isFalse()
        assertThat(d.onSwipeUp(7_000L, 3)).isTrue()
    }

    @Test
    fun `configurable threshold - single swipe triggers when requiredSwipes is 1`() {
        assertThat(detector().onSwipeUp(0L, requiredSwipes = 1)).isTrue()
    }

    @Test
    fun `debounce - burst within minGap counts as one swipe`() {
        val d = detector(minGapMs = 400L)
        // Ráfaga de un mismo gesto: 4 eventos en <400ms → cuentan como 1.
        assertThat(d.onSwipeUp(0L, requiredSwipes = 2)).isFalse()   // cuenta 1
        assertThat(d.onSwipeUp(100L, requiredSwipes = 2)).isFalse() // ignorado (ráfaga)
        assertThat(d.onSwipeUp(250L, requiredSwipes = 2)).isFalse() // ignorado (ráfaga)
        // Segundo gesto, pasado el minGap:
        assertThat(d.onSwipeUp(800L, requiredSwipes = 2)).isTrue()  // cuenta 2 → activa
    }

    @Test
    fun `debounce - two physical gestures with three required`() {
        val d = detector(minGapMs = 400L)
        // Gesto 1 (ráfaga)
        d.onSwipeUp(0L, 3); d.onSwipeUp(150L, 3); d.onSwipeUp(300L, 3)
        // Gesto 2 (ráfaga)
        d.onSwipeUp(800L, 3); d.onSwipeUp(950L, 3)
        // Gesto 3 → debería activar (3 gestos distintos)
        assertThat(d.onSwipeUp(1_400L, 3)).isTrue()
    }

    @Test
    fun `reset clears partial sequence`() {
        val d = detector()
        d.onSwipeUp(0L, 3); d.onSwipeUp(1_000L, 3)
        d.reset()
        assertThat(d.onSwipeUp(1_500L, 3)).isFalse()
        assertThat(d.onSwipeUp(2_000L, 3)).isFalse()
        assertThat(d.onSwipeUp(2_500L, 3)).isTrue()
    }

    // -----------------------
    // Tests nuevos para "señal global"
    // -----------------------

    @Test
    fun `global signal activates in EITHER mode even if swipe count not reached`() {
        val d = detector()
        // Por defecto SWIPES_ONLY no activaría con requiredSwipes alto
        assertThat(d.onSwipeUp(0L, requiredSwipes = 5)).isFalse()

        // Activamos modo EITHER y seteamos señal global de confianza alta
        d.setActivationMode(SwipeActivationDetector.ActivationMode.EITHER)
        d.setGlobalGesture(detected = true, confidence = 0.9f)

        // Llamada a onSwipeUp debe devolver true debido a la señal global
        assertThat(d.onSwipeUp(10L, requiredSwipes = 5)).isTrue()
    }

    @Test
    fun `both mode requires both conditions`() {
        val d = detector()

        d.setActivationMode(SwipeActivationDetector.ActivationMode.BOTH)

        // Solo swipes → no activa
        d.onSwipeUp(0L, 3)
        d.onSwipeUp(500L, 3)
        assertThat(d.onSwipeUp(1_000L, 3)).isFalse()

        // Con señal global + swipes → activa
        val d2 = detector()
        d2.setActivationMode(SwipeActivationDetector.ActivationMode.BOTH)
        d2.setGlobalGesture(true, 0.9f)

        d2.onSwipeUp(0L, 3)
        d2.onSwipeUp(500L, 3)
        assertThat(d2.onSwipeUp(1_000L, 3)).isTrue()
    }

    @Test
    fun `global confidence below threshold does not activate in GLOBAL_ONLY mode`() {
        val d = detector()
        // Modo GLOBAL_ONLY con confianza baja -> no activa
        d.setActivationMode(SwipeActivationDetector.ActivationMode.GLOBAL_ONLY)
        d.setGlobalGesture(detected = true, confidence = 0.1f)
        assertThat(d.onSwipeUp(0L, requiredSwipes = 10)).isFalse()
    }
}