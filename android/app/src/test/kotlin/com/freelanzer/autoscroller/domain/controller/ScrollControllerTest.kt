package com.freelanzer.autoscroller.domain.controller

import app.cash.turbine.test
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests del state machine + flujos del [ScrollController].
 *
 * `ScrollController` es 100% Kotlin puro (no toca Android) — el timestamp del swipe lo
 * provee el caller, así que podemos verificar `lastSwipeAtMs` sin shim de `SystemClock`.
 */
class ScrollControllerTest {

    private val intervalFlow = MutableStateFlow(SettingsRepository.DEFAULT_INTERVAL_MS)
    private val settingsRepository = mockk<SettingsRepository>().apply {
        every { intervalMillisFlow } returns intervalFlow
    }
    private val appScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())
    private val controller = ScrollController(settingsRepository, appScope)

    @Test
    fun `initial state is Idle and counters reset`() {
        assertThat(controller.state.value).isEqualTo(ScrollState.Idle)
        assertThat(controller.scrollCount.value).isEqualTo(0)
        assertThat(controller.lastSwipeAtMs).isEqualTo(0L)
    }

    @Test
    fun `start transitions to Scrolling`() {
        controller.start()
        assertThat(controller.state.value).isEqualTo(ScrollState.Scrolling)
    }

    @Test
    fun `start resets scrollCount and lastSwipeAtMs`() {
        controller.onScrollPerformed(elapsedRealtimeMs = 1_000L)
        controller.onScrollPerformed(elapsedRealtimeMs = 2_000L)
        assertThat(controller.scrollCount.value).isEqualTo(2)
        assertThat(controller.lastSwipeAtMs).isEqualTo(2_000L)

        controller.start()

        assertThat(controller.scrollCount.value).isEqualTo(0)
        assertThat(controller.lastSwipeAtMs).isEqualTo(0L)
    }

    @Test
    fun `stop transitions to Idle from any state`() {
        controller.start()
        controller.stop()
        assertThat(controller.state.value).isEqualTo(ScrollState.Idle)

        controller.start()
        controller.pause()
        controller.stop()
        assertThat(controller.state.value).isEqualTo(ScrollState.Idle)
    }

    @Test
    fun `pause only takes effect while Scrolling`() {
        controller.pause()
        assertThat(controller.state.value).isEqualTo(ScrollState.Idle)

        controller.start()
        controller.pause()
        assertThat(controller.state.value).isEqualTo(ScrollState.Paused)

        // Una segunda pause desde Paused no debe cambiar nada.
        controller.pause()
        assertThat(controller.state.value).isEqualTo(ScrollState.Paused)
    }

    @Test
    fun `toggle cycles Idle to Scrolling to Idle`() {
        assertThat(controller.state.value).isEqualTo(ScrollState.Idle)
        controller.toggle()
        assertThat(controller.state.value).isEqualTo(ScrollState.Scrolling)
        controller.toggle()
        assertThat(controller.state.value).isEqualTo(ScrollState.Idle)
    }

    @Test
    fun `toggle from Paused resumes to Scrolling`() {
        controller.start()
        controller.pause()
        assertThat(controller.state.value).isEqualTo(ScrollState.Paused)

        controller.toggle()
        assertThat(controller.state.value).isEqualTo(ScrollState.Scrolling)
    }

    @Test
    fun `onScrollPerformed increments count and records timestamp`() {
        controller.onScrollPerformed(elapsedRealtimeMs = 1_500L)
        assertThat(controller.scrollCount.value).isEqualTo(1)
        assertThat(controller.lastSwipeAtMs).isEqualTo(1_500L)

        controller.onScrollPerformed(elapsedRealtimeMs = 5_000L)
        assertThat(controller.scrollCount.value).isEqualTo(2)
        assertThat(controller.lastSwipeAtMs).isEqualTo(5_000L)
    }

    @Test
    fun `intervalMillis mirrors repository flow`() = runTest {
        controller.intervalMillis.test {
            assertThat(awaitItem()).isEqualTo(SettingsRepository.DEFAULT_INTERVAL_MS)
            intervalFlow.value = 8_000L
            assertThat(awaitItem()).isEqualTo(8_000L)
            cancelAndConsumeRemainingEvents()
        }
    }
}
