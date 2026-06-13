package com.freelanzer.autoscroller.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Combina los `Flow` persistidos del [SettingsRepository] con un flag de
 * "servicio de accesibilidad habilitado" (resuelto vía [ServiceStatusProvider] para no
 * acoplar a `Context`). Expone setters que delegan en el repositorio — la validación
 * de rangos vive ahí.
 *
 * `refreshServiceStatus()` lo invoca la UI desde `LifecycleResumeEffect` cuando el
 * usuario vuelve de Ajustes del sistema.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val serviceStatusProvider: ServiceStatusProvider,
) : ViewModel() {

    private val serviceEnabled = MutableStateFlow(
        serviceStatusProvider.isAutoScrollServiceEnabled(),
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        serviceEnabled,
        prefsStateFlow(),
    ) { enabled, prefs ->
        SettingsUiState(
            isServiceEnabled = enabled,
            intervalSeconds = prefs.intervalSeconds,
            timeLimitMinutes = prefs.timeLimitMinutes,
            alertsEnabled = prefs.alertsEnabled,
            threeFingerEnabled = prefs.threeFingerEnabled,
            swipeActivationEnabled = prefs.swipeActivationEnabled,
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

    private fun prefsStateFlow() = combine(
        settingsRepository.intervalMillisFlow,
        settingsRepository.timeLimitMinutesFlow,
        settingsRepository.alertsEnabledFlow,
        settingsRepository.threeFingerTriggerEnabledFlow,
        settingsRepository.swipeActivationEnabledFlow,
    ) { intervalMs, limit, alerts, threeFinger, swipeActivation ->
        PrefsBundle(
            intervalSeconds = (intervalMs / 1_000).toInt().coerceAtLeast(1),
            timeLimitMinutes = limit,
            alertsEnabled = alerts,
            threeFingerEnabled = threeFinger,
            swipeActivationEnabled = swipeActivation,
        )
    }

    private data class PrefsBundle(
        val intervalSeconds: Int,
        val timeLimitMinutes: Int,
        val alertsEnabled: Boolean,
        val threeFingerEnabled: Boolean,
        val swipeActivationEnabled: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MS: Long = 5_000L
    }
}
