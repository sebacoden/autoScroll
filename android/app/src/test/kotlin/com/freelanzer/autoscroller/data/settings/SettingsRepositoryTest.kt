package com.freelanzer.autoscroller.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.freelanzer.autoscroller.testsupport.createTestPreferencesDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout

/**
 * Tests del [SettingsRepository] contra un `DataStore<Preferences>` real respaldado por
 * un archivo temporal (no requiere `Context`). Verifica defaults, persistencia y la
 * validación de rangos en los setters.
 */
class SettingsRepositoryTest {

    @get:Rule val tempFolder = TemporaryFolder()

    /** Red de seguridad: un test colgado falla a los 15 s con stack trace en vez de bloquear el build. */
    @get:Rule val timeout: Timeout = Timeout.seconds(15)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        dataStore = createTestPreferencesDataStore(tempFolder)
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        // El TemporaryFolder se limpia solo al terminar el test.
    }

    @Test
    fun `intervalMillis defaults to DEFAULT_INTERVAL_MS when not set`() = runBlocking {
        repository.intervalMillisFlow.test {
            assertThat(awaitItem()).isEqualTo(SettingsRepository.DEFAULT_INTERVAL_MS)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setIntervalMillis persists and emits new value`() = runBlocking {
        repository.setIntervalMillis(8_000L)
        repository.intervalMillisFlow.test {
            assertThat(awaitItem()).isEqualTo(8_000L)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setIntervalMillis rejects value below minimum`() = runBlocking {
        repository.setIntervalMillis(SettingsRepository.MIN_INTERVAL_MS - 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setIntervalMillis rejects value above maximum`() = runBlocking {
        repository.setIntervalMillis(SettingsRepository.MAX_INTERVAL_MS + 1)
    }

    @Test
    fun `timeLimitMinutes defaults to 30 minutes`() = runBlocking {
        repository.timeLimitMinutesFlow.test {
            assertThat(awaitItem()).isEqualTo(SettingsRepository.DEFAULT_TIME_LIMIT_MIN)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setTimeLimitMinutes persists and emits new value`() = runBlocking {
        repository.setTimeLimitMinutes(60)
        repository.timeLimitMinutesFlow.test {
            assertThat(awaitItem()).isEqualTo(60)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setTimeLimitMinutes rejects out-of-range values`() = runBlocking {
        repository.setTimeLimitMinutes(SettingsRepository.MAX_TIME_LIMIT_MIN + 1)
    }

    @Test
    fun `alertsEnabled defaults to true`() = runBlocking {
        repository.alertsEnabledFlow.test {
            assertThat(awaitItem()).isTrue()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setAlertsEnabled persists toggle`() = runBlocking {
        repository.setAlertsEnabled(false)
        repository.alertsEnabledFlow.test {
            assertThat(awaitItem()).isFalse()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `threeFingerTrigger defaults to true (activación primaria)`() = runBlocking {
        // Verificamos primero el invariante del producto: tap con 3 dedos es la
        // activación por defecto post-onboarding.
        assertThat(SettingsRepository.DEFAULT_THREE_FINGER_ENABLED).isTrue()

        repository.threeFingerTriggerEnabledFlow.test {
            assertThat(awaitItem()).isEqualTo(SettingsRepository.DEFAULT_THREE_FINGER_ENABLED)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setThreeFingerTriggerEnabled persists toggle`() = runBlocking {
        repository.setThreeFingerTriggerEnabled(false)
        repository.threeFingerTriggerEnabledFlow.test {
            assertThat(awaitItem()).isFalse()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setSwipeActivationEnabled persists toggle`() = runBlocking {
        repository.setSwipeActivationEnabled(true)
        repository.swipeActivationEnabledFlow.test {
            assertThat(awaitItem()).isTrue()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `requiredSwipes defaults to 3 and persists`() = runBlocking {
        assertThat(SettingsRepository.DEFAULT_REQUIRED_SWIPES).isEqualTo(3)
        repository.setRequiredSwipesToActivate(5)
        repository.requiredSwipesToActivateFlow.test {
            assertThat(awaitItem()).isEqualTo(5)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setRequiredSwipes rejects out-of-range`() = runBlocking {
        repository.setRequiredSwipesToActivate(SettingsRepository.MAX_REQUIRED_SWIPES + 1)
    }

    @Test
    fun `pauseOnTouch defaults to 3s and persists`() = runBlocking {
        assertThat(SettingsRepository.DEFAULT_PAUSE_ON_TOUCH_SEC).isEqualTo(3)
        repository.setPauseOnTouchSeconds(10)
        repository.pauseOnTouchSecondsFlow.test {
            assertThat(awaitItem()).isEqualTo(10)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `activationApps defaults to the predefined set`() = runBlocking {
        repository.activationAppPackagesFlow.test {
            assertThat(awaitItem()).isEqualTo(DefaultActivationApps.PACKAGES)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `addActivationApp adds to the set`() = runBlocking {
        repository.addActivationApp("com.example.app")
        repository.activationAppPackagesFlow.test {
            assertThat(awaitItem()).contains("com.example.app")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `removeActivationApp removes from the set`() = runBlocking {
        repository.removeActivationApp(DefaultActivationApps.YOUTUBE)
        repository.activationAppPackagesFlow.test {
            assertThat(awaitItem()).doesNotContain(DefaultActivationApps.YOUTUBE)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `swipeActivation defaults to true`() = runBlocking {
        assertThat(SettingsRepository.DEFAULT_SWIPE_ACTIVATION_ENABLED).isTrue()
        repository.swipeActivationEnabledFlow.test {
            assertThat(awaitItem()).isTrue()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `eulaAccepted defaults to false`() = runBlocking {
        repository.eulaAcceptedFlow.test {
            assertThat(awaitItem()).isFalse()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setEulaAccepted persists`() = runBlocking {
        repository.setEulaAccepted(true)
        repository.eulaAcceptedFlow.test {
            assertThat(awaitItem()).isTrue()
            cancelAndConsumeRemainingEvents()
        }
    }
}
