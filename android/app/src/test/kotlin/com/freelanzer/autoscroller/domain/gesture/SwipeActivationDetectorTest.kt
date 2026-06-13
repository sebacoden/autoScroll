package com.freelanzer.autoscroller.domain.gesture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests del [SwipeActivationDetector]. Clase pura → JVM puro, sin runner especial.
 */
class SwipeActivationDetectorTest {

    @Test
    fun `three swipes within window trigger activation`() {
        val detector = SwipeActivationDetector(requiredSwipes = 3, windowMs = 3_000L)
        assertThat(detector.onSwipeUp(0L)).isFalse()
        assertThat(detector.onSwipeUp(1_000L)).isFalse()
        assertThat(detector.onSwipeUp(2_000L)).isTrue()
    }

    @Test
    fun `counter resets after triggering`() {
        val detector = SwipeActivationDetector(requiredSwipes = 3, windowMs = 3_000L)
        detector.onSwipeUp(0L)
        detector.onSwipeUp(1_000L)
        assertThat(detector.onSwipeUp(2_000L)).isTrue()

        // Tras disparar, hace falta una nueva tanda completa.
        assertThat(detector.onSwipeUp(2_500L)).isFalse()
        assertThat(detector.onSwipeUp(3_000L)).isFalse()
        assertThat(detector.onSwipeUp(3_500L)).isTrue()
    }

    @Test
    fun `swipe outside window restarts the sequence`() {
        val detector = SwipeActivationDetector(requiredSwipes = 3, windowMs = 3_000L)
        detector.onSwipeUp(0L)
        detector.onSwipeUp(1_000L)
        // Gap mayor a la ventana: este swipe es el 1 de una nueva tanda.
        assertThat(detector.onSwipeUp(5_000L)).isFalse()
        assertThat(detector.onSwipeUp(6_000L)).isFalse()
        assertThat(detector.onSwipeUp(7_000L)).isTrue()
    }

    @Test
    fun `swipe exactly at window boundary still counts`() {
        val detector = SwipeActivationDetector(requiredSwipes = 2, windowMs = 3_000L)
        detector.onSwipeUp(0L)
        // Exactamente en el límite (<=) cuenta como dentro de ventana.
        assertThat(detector.onSwipeUp(3_000L)).isTrue()
    }

    @Test
    fun `reset clears partial sequence`() {
        val detector = SwipeActivationDetector(requiredSwipes = 3, windowMs = 3_000L)
        detector.onSwipeUp(0L)
        detector.onSwipeUp(1_000L)
        detector.reset()
        // Tras reset, el siguiente swipe es el 1 de una nueva tanda.
        assertThat(detector.onSwipeUp(1_500L)).isFalse()
        assertThat(detector.onSwipeUp(2_000L)).isFalse()
        assertThat(detector.onSwipeUp(2_500L)).isTrue()
    }

    @Test
    fun `single required swipe triggers immediately`() {
        val detector = SwipeActivationDetector(requiredSwipes = 1, windowMs = 3_000L)
        assertThat(detector.onSwipeUp(0L)).isTrue()
    }
}
