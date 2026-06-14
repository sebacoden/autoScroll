package com.freelanzer.autoscroller.ui.settings

import app.cash.turbine.test
import com.freelanzer.autoscroller.core.apps.InstalledApp
import com.freelanzer.autoscroller.core.apps.InstalledAppsProvider
import com.freelanzer.autoscroller.core.service.ServiceStatusProvider
import com.freelanzer.autoscroller.data.settings.DefaultActivationApps
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.freelanzer.autoscroller.testsupport.FakeSettingsRepository
import com.freelanzer.autoscroller.testsupport.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests del [SettingsViewModel] contra fakes en memoria (determinista, sin DataStore/IO).
 * La persistencia real se prueba en `SettingsRepositoryTest`.
 */
class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()
    private val serviceStatusProvider = FakeServiceStatusProvider(initial = false)
    private val installedAppsProvider = FakeInstalledAppsProvider()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        viewModel = SettingsViewModel(repository, serviceStatusProvider, installedAppsProvider)
    }

    @Test
    fun `uiState derives from repository values`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            repository.intervalMillisFlow.value = 8_000L
            repository.timeLimitMinutesFlow.value = 60
            repository.requiredSwipesToActivateFlow.value = 5
            repository.pauseOnTouchSecondsFlow.value = 10

            viewModel.uiState.test {
                var s = awaitItem()
                while (s.intervalSeconds != 8) s = awaitItem()

                assertThat(s.intervalSeconds).isEqualTo(8)
                assertThat(s.timeLimitMinutes).isEqualTo(60)
                assertThat(s.requiredSwipes).isEqualTo(5)
                assertThat(s.pauseSeconds).isEqualTo(10)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState lists activation apps with resolved labels`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.uiState.test {
                var s = awaitItem()
                while (s.activationApps.isEmpty()) s = awaitItem()
                // Los defaults incluyen YouTube; la etiqueta la resuelve el provider fake.
                assertThat(s.activationApps.map { it.packageName })
                    .containsExactlyElementsIn(DefaultActivationApps.PACKAGES)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `onRequiredSwipesChanged persists clamped`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onRequiredSwipesChanged(99f)
            assertThat(repository.requiredSwipesToActivateFlow.value)
                .isEqualTo(SettingsRepository.MAX_REQUIRED_SWIPES)
        }

    @Test
    fun `onPauseSecondsChanged persists clamped`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onPauseSecondsChanged(0f)
            assertThat(repository.pauseOnTouchSecondsFlow.value)
                .isEqualTo(SettingsRepository.MIN_PAUSE_ON_TOUCH_SEC)
        }

    @Test
    fun `onRemoveApp removes from allowlist`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onRemoveApp(DefaultActivationApps.YOUTUBE)
            assertThat(repository.activationAppPackagesFlow.value)
                .doesNotContain(DefaultActivationApps.YOUTUBE)
        }

    @Test
    fun `onSwipeActivationEnabledChanged persists toggle`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onSwipeActivationEnabledChanged(false)
            assertThat(repository.swipeActivationEnabledFlow.value).isFalse()
        }

    @Test
    fun `onIntervalSecondsChanged clamps above MAX`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.onIntervalSecondsChanged(999f)
            assertThat(repository.intervalMillisFlow.value).isEqualTo(SettingsRepository.MAX_INTERVAL_MS)
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

    private class FakeInstalledAppsProvider : InstalledAppsProvider {
        override suspend fun launchableApps(): List<InstalledApp> = emptyList()
        override fun labelFor(packageName: String): String = "label:$packageName"
    }
}
