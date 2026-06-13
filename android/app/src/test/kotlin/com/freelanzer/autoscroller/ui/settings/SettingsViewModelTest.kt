package com.freelanzer.autoscroller.ui.settings

import app.cash.turbine.test
import com.freelanzer.autoscroller.core.service.ServiceStatusProvider
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.freelanzer.autoscroller.testsupport.FakeSettingsRepository
import com.freelanzer.autoscroller.testsupport.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests del [SettingsViewModel] contra un [FakeSettingsRepository] en memoria
 * (determinista, sin DataStore/IO). La persistencia real se prueba en
 * `SettingsRepositoryTest`.
 *
 * Cada test corre bajo `runTest(scheduler)` compartiendo el reloj del dispatcher de Main
 * (ver [MainDispatcherRule]); así los `launch` y el `stateIn(WhileSubscribed)` del
 * ViewModel se resuelven de forma determinista.
 */
class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()
    private val serviceStatusProvider = FakeServiceStatusProvider(initial = false)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        viewModel = SettingsViewModel(repository, serviceStatusProvider)
    }

    @Test
    fun `uiState derives from repository values`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Valores NO-default para probar la derivación real (no solo el placeholder
            // Initial, que casualmente coincide con los defaults).
            repository.intervalMillisFlow.value = 8_000L
            repository.timeLimitMinutesFlow.value = 60
            repository.alertsEnabledFlow.value = false
            repository.threeFingerTriggerEnabledFlow.value = false
            repository.swipeActivationEnabledFlow.value = true

            viewModel.uiState.test {
                var s = awaitItem()
                while (s.intervalSeconds != 8) s = awaitItem()

                assertThat(s.intervalSeconds).isEqualTo(8)
                assertThat(s.timeLimitMinutes).isEqualTo(60)
                assertThat(s.alertsEnabled).isFalse()
                assertThat(s.threeFingerEnabled).isFalse()
                assertThat(s.swipeActivationEnabled).isTrue()
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState defaults match Initial`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Con el repo en defaults, el primer estado expuesto es Initial (mismos valores).
            viewModel.uiState.test {
                val first = awaitItem()
                assertThat(first.intervalSeconds).isEqualTo(
                    (SettingsRepository.DEFAULT_INTERVAL_MS / 1_000).toInt(),
                )
                assertThat(first.timeLimitMinutes).isEqualTo(SettingsRepository.DEFAULT_TIME_LIMIT_MIN)
                assertThat(first.threeFingerEnabled).isEqualTo(SettingsRepository.DEFAULT_THREE_FINGER_ENABLED)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `onIntervalSecondsChanged persists interval rounded`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onIntervalSecondsChanged(seconds = 10f)
            assertThat(repository.intervalMillisFlow.value).isEqualTo(10_000L)
        }

    @Test
    fun `onIntervalSecondsChanged clamps below MIN to MIN`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onIntervalSecondsChanged(seconds = 0f)
            assertThat(repository.intervalMillisFlow.value).isEqualTo(SettingsRepository.MIN_INTERVAL_MS)
        }

    @Test
    fun `onIntervalSecondsChanged clamps above MAX to MAX`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onIntervalSecondsChanged(seconds = 999f)
            assertThat(repository.intervalMillisFlow.value).isEqualTo(SettingsRepository.MAX_INTERVAL_MS)
        }

    @Test
    fun `onTimeLimitMinutesChanged persists clamped int`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onTimeLimitMinutesChanged(minutes = 999f)
            assertThat(repository.timeLimitMinutesFlow.value).isEqualTo(SettingsRepository.MAX_TIME_LIMIT_MIN)
        }

    @Test
    fun `onAlertsEnabledChanged persists toggle`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onAlertsEnabledChanged(false)
            assertThat(repository.alertsEnabledFlow.value).isFalse()
        }

    @Test
    fun `onThreeFingerEnabledChanged persists toggle`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onThreeFingerEnabledChanged(false)
            assertThat(repository.threeFingerTriggerEnabledFlow.value).isFalse()
        }

    @Test
    fun `onSwipeActivationEnabledChanged persists toggle`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onSwipeActivationEnabledChanged(true)
            assertThat(repository.swipeActivationEnabledFlow.value).isTrue()
        }

    @Test
    fun `refreshServiceStatus re-queries the provider and updates state`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            serviceStatusProvider.enabled = true
            viewModel.refreshServiceStatus()

            viewModel.uiState.test {
                var item = awaitItem()
                while (!item.isServiceEnabled) item = awaitItem()
                assertThat(item.isServiceEnabled).isTrue()
                cancelAndConsumeRemainingEvents()
            }
        }

    private class FakeServiceStatusProvider(initial: Boolean) : ServiceStatusProvider {
        var enabled: Boolean = initial
        override fun isAutoScrollServiceEnabled(): Boolean = enabled
    }
}
