package com.freelanzer.autoscroller.testsupport

import com.freelanzer.autoscroller.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Implementación en memoria de [SettingsRepository] para tests de ViewModels.
 *
 * Respaldada por `MutableStateFlow` (hot, siempre con valor): los `setX` actualizan el
 * estado de forma síncrona y los flujos emiten al instante — sin DataStore, sin IO, sin
 * dispatchers de test. Determinista y rápido. La persistencia real se prueba aparte en
 * `DataStoreSettingsRepository` / `SettingsRepositoryTest`.
 */
class FakeSettingsRepository : SettingsRepository {

    override val intervalMillisFlow = MutableStateFlow(SettingsRepository.DEFAULT_INTERVAL_MS)
    override val timeLimitMinutesFlow = MutableStateFlow(SettingsRepository.DEFAULT_TIME_LIMIT_MIN)
    override val alertsEnabledFlow = MutableStateFlow(SettingsRepository.DEFAULT_ALERTS_ENABLED)
    override val threeFingerTriggerEnabledFlow =
        MutableStateFlow(SettingsRepository.DEFAULT_THREE_FINGER_ENABLED)
    override val swipeActivationEnabledFlow =
        MutableStateFlow(SettingsRepository.DEFAULT_SWIPE_ACTIVATION_ENABLED)
    override val eulaAcceptedFlow = MutableStateFlow(false)

    override suspend fun setIntervalMillis(millis: Long) {
        require(millis in SettingsRepository.MIN_INTERVAL_MS..SettingsRepository.MAX_INTERVAL_MS)
        intervalMillisFlow.value = millis
    }

    override suspend fun setTimeLimitMinutes(minutes: Int) {
        require(minutes in SettingsRepository.MIN_TIME_LIMIT_MIN..SettingsRepository.MAX_TIME_LIMIT_MIN)
        timeLimitMinutesFlow.value = minutes
    }

    override suspend fun setAlertsEnabled(enabled: Boolean) {
        alertsEnabledFlow.value = enabled
    }

    override suspend fun setThreeFingerTriggerEnabled(enabled: Boolean) {
        threeFingerTriggerEnabledFlow.value = enabled
    }

    override suspend fun setSwipeActivationEnabled(enabled: Boolean) {
        swipeActivationEnabledFlow.value = enabled
    }

    override suspend fun setEulaAccepted(accepted: Boolean) {
        eulaAcceptedFlow.value = accepted
    }
}
