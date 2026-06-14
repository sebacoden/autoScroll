package com.freelanzer.autoscroller.testsupport

import com.freelanzer.autoscroller.data.settings.DefaultActivationApps
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Implementación en memoria de [SettingsRepository] para tests de ViewModels.
 *
 * Respaldada por `MutableStateFlow` (hot, siempre con valor): los `setX` actualizan el
 * estado de forma síncrona y los flujos emiten al instante — sin DataStore, sin IO. La
 * persistencia real se prueba aparte en `SettingsRepositoryTest`.
 */
class FakeSettingsRepository : SettingsRepository {

    override val intervalMillisFlow = MutableStateFlow(SettingsRepository.DEFAULT_INTERVAL_MS)
    override val timeLimitMinutesFlow = MutableStateFlow(SettingsRepository.DEFAULT_TIME_LIMIT_MIN)
    override val alertsEnabledFlow = MutableStateFlow(SettingsRepository.DEFAULT_ALERTS_ENABLED)
    override val threeFingerTriggerEnabledFlow =
        MutableStateFlow(SettingsRepository.DEFAULT_THREE_FINGER_ENABLED)
    override val swipeActivationEnabledFlow =
        MutableStateFlow(SettingsRepository.DEFAULT_SWIPE_ACTIVATION_ENABLED)
    override val requiredSwipesToActivateFlow =
        MutableStateFlow(SettingsRepository.DEFAULT_REQUIRED_SWIPES)
    override val pauseOnTouchSecondsFlow =
        MutableStateFlow(SettingsRepository.DEFAULT_PAUSE_ON_TOUCH_SEC)
    override val activationAppPackagesFlow =
        MutableStateFlow(DefaultActivationApps.PACKAGES)
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

    override suspend fun setRequiredSwipesToActivate(count: Int) {
        require(count in SettingsRepository.MIN_REQUIRED_SWIPES..SettingsRepository.MAX_REQUIRED_SWIPES)
        requiredSwipesToActivateFlow.value = count
    }

    override suspend fun setPauseOnTouchSeconds(seconds: Int) {
        require(seconds in SettingsRepository.MIN_PAUSE_ON_TOUCH_SEC..SettingsRepository.MAX_PAUSE_ON_TOUCH_SEC)
        pauseOnTouchSecondsFlow.value = seconds
    }

    override suspend fun addActivationApp(packageName: String) {
        if (packageName.isBlank()) return
        activationAppPackagesFlow.value = activationAppPackagesFlow.value + packageName
    }

    override suspend fun removeActivationApp(packageName: String) {
        activationAppPackagesFlow.value = activationAppPackagesFlow.value - packageName
    }

    override suspend fun setEulaAccepted(accepted: Boolean) {
        eulaAcceptedFlow.value = accepted
    }
}
