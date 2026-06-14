package com.freelanzer.autoscroller.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freelanzer.autoscroller.core.apps.InstalledAppsProvider
import com.freelanzer.autoscroller.core.service.ServiceStatusProvider
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla principal (Ajustes).
 *
 * Combina los `Flow` persistidos del [SettingsRepository] con el estado del servicio de
 * accesibilidad ([ServiceStatusProvider]). Las etiquetas de las apps de la allowlist se
 * resuelven con [InstalledAppsProvider]. La validación de rangos vive en el repositorio.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serviceStatusProvider: ServiceStatusProvider,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    private val serviceEnabled = MutableStateFlow(
        serviceStatusProvider.isAutoScrollServiceEnabled(),
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        serviceEnabled,
        prefsStateFlow(),
        settingsRepository.activationAppPackagesFlow,
    ) { enabled, prefs, appPackages ->
        SettingsUiState(
            isServiceEnabled = enabled,
            intervalSeconds = prefs.intervalSeconds,
            timeLimitMinutes = prefs.timeLimitMinutes,
            alertsEnabled = prefs.alertsEnabled,
            threeFingerEnabled = prefs.threeFingerEnabled,
            swipeActivationEnabled = prefs.swipeActivationEnabled,
            requiredSwipes = prefs.requiredSwipes,
            pauseSeconds = prefs.pauseSeconds,
            activationApps = appPackages
                .map { ActivationApp(it, installedAppsProvider.labelFor(it)) }
                .sortedBy { it.label.lowercase() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState.Initial,
    )

    fun refreshServiceStatus() {
        serviceEnabled.value = serviceStatusProvider.isAutoScrollServiceEnabled()
    }

    fun onIntervalSecondsChanged(seconds: Float) {
        val millis = (seconds * 1_000f).roundToLong()
            .coerceIn(SettingsRepository.MIN_INTERVAL_MS, SettingsRepository.MAX_INTERVAL_MS)
        viewModelScope.launch { settingsRepository.setIntervalMillis(millis) }
    }

    fun onTimeLimitMinutesChanged(minutes: Float) {
        val clamped = minutes.toInt()
            .coerceIn(SettingsRepository.MIN_TIME_LIMIT_MIN, SettingsRepository.MAX_TIME_LIMIT_MIN)
        viewModelScope.launch { settingsRepository.setTimeLimitMinutes(clamped) }
    }

    fun onAlertsEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAlertsEnabled(enabled) }
    }

    fun onThreeFingerEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setThreeFingerTriggerEnabled(enabled) }
    }

    fun onSwipeActivationEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSwipeActivationEnabled(enabled) }
    }

    fun onRequiredSwipesChanged(count: Float) {
        val clamped = count.toInt()
            .coerceIn(SettingsRepository.MIN_REQUIRED_SWIPES, SettingsRepository.MAX_REQUIRED_SWIPES)
        viewModelScope.launch { settingsRepository.setRequiredSwipesToActivate(clamped) }
    }

    fun onPauseSecondsChanged(seconds: Float) {
        val clamped = seconds.toInt()
            .coerceIn(SettingsRepository.MIN_PAUSE_ON_TOUCH_SEC, SettingsRepository.MAX_PAUSE_ON_TOUCH_SEC)
        viewModelScope.launch { settingsRepository.setPauseOnTouchSeconds(clamped) }
    }

    fun onRemoveApp(packageName: String) {
        viewModelScope.launch { settingsRepository.removeActivationApp(packageName) }
    }

    private fun prefsStateFlow() = combine(
        settingsRepository.intervalMillisFlow,
        settingsRepository.timeLimitMinutesFlow,
        settingsRepository.alertsEnabledFlow,
        settingsRepository.threeFingerTriggerEnabledFlow,
        combine(
            settingsRepository.swipeActivationEnabledFlow,
            settingsRepository.requiredSwipesToActivateFlow,
            settingsRepository.pauseOnTouchSecondsFlow,
        ) { swipe, swipes, pause -> Triple(swipe, swipes, pause) },
    ) { intervalMs, limit, alerts, threeFinger, activation ->
        PrefsBundle(
            intervalSeconds = (intervalMs / 1_000).toInt().coerceAtLeast(1),
            timeLimitMinutes = limit,
            alertsEnabled = alerts,
            threeFingerEnabled = threeFinger,
            swipeActivationEnabled = activation.first,
            requiredSwipes = activation.second,
            pauseSeconds = activation.third,
        )
    }

    private data class PrefsBundle(
        val intervalSeconds: Int,
        val timeLimitMinutes: Int,
        val alertsEnabled: Boolean,
        val threeFingerEnabled: Boolean,
        val swipeActivationEnabled: Boolean,
        val requiredSwipes: Int,
        val pauseSeconds: Int,
    )

    private companion object {
        const val STOP_TIMEOUT_MS: Long = 5_000L
    }
}
