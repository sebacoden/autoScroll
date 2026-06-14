package com.freelanzer.autoscroller.domain.gesture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests del [SwipeActivationDetector]. Clase pura → JVM puro, sin runner especial.
 * El umbral `requiredSwipes` se pasa por llamada (configurable en runtime).
 */
class SwipeActivationDetectorTest {

    @Test
    fun `three swipes within window trigger activation`() {
        val detector = SwipeActivationDetector(windowMs = 3_000L)
        assertThat(detector.onSwipeUp(0L, requiredSwipes = 3)).isFalse()
        assertThat(detector.onSwipeUp(1_000L, requiredSwipes = 3)).isFalse()
        assertThat(detector.onSwipeUp(2_000L, requiredSwipes = 3)).isTrue()
    }

    @Test
    fun `counter resets after triggering`() {
        val detector = SwipeActivationDetector(windowMs = 3_000L)
        detector.onSwipeUp(0L, 3)
        detector.onSwipeUp(1_000L, 3)
        assertThat(detector.onSwipeUp(2_000L, 3)).isTrue()

        assertThat(detector.onSwipeUp(2_500L, 3)).isFalse()
        assertThat(detector.onSwipeUp(3_000L, 3)).isFalse()
        assertThat(detector.onSwipeUp(3_500L, 3)).isTrue()
    }

    @Test
    fun `swipe outside window restarts the sequence`() {
        val detector = SwipeActivationDetector(windowMs = 3_000L)
        detector.onSwipeUp(0L, 3)
        detector.onSwipeUp(1_000L, 3)
        assertThat(detector.onSwipeUp(5_000L, 3)).isFalse()
        assertThat(detector.onSwipeUp(6_000L, 3)).isFalse()
        assertThat(detector.onSwipeUp(7_000L, 3)).isTrue()
    }

    @Test
    fun `configurable threshold - single swipe triggers when requiredSwipes is 1`() {
        val detector = SwipeActivationDetector(windowMs = 3_000L)
        assertThat(detector.onSwipeUp(0L, requiredSwipes = 1)).isTrue()
    }

    @Test
    fun `configurable threshold - five swipes`() {
        val detector = SwipeActivationDetector(windowMs = 10_000L)
        repeat(4) { i -> assertThat(detector.onSwipeUp(i * 1_000L, requiredSwipes = 5)).isFalse() }
        assertThat(detector.onSwipeUp(4_000L, requiredSwipes = 5)).isTrue()
    }

    @Test
    fun `swipe exactly at window boundary still counts`() {
        val detector = SwipeActivationDetector(windowMs = 3_000L)
        detector.onSwipeUp(0L, 2)
        assertThat(detector.onSwipeUp(3_000L, 2)).isTrue()
    }

    @Test
    fun `reset clears partial sequence`() {
        val detector = SwipeActivationDetector(windowMs = 3_000L)
        detector.onSwipeUp(0L, 3)
        detector.onSwipeUp(1_000L, 3)
        detector.reset()
        assertThat(detector.onSwipeUp(1_500L, 3)).isFalse()
        assertThat(detector.onSwipeUp(2_000L, 3)).isFalse()
        assertThat(detector.onSwipeUp(2_500L, 3)).isTrue()
    }
}
