package com.freelanzer.autoscroller.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de ajustes. Combina los `Flow` persistidos del repositorio
 * en un único [SettingsUiState] y expone setters que delegan en el repositorio
 * (la validación de rangos vive ahí, no acá).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.intervalMillisFlow,
        settingsRepository.timeLimitMinutesFlow,
        settingsRepository.alertsEnabledFlow,
        settingsRepository.threeFingerTriggerEnabledFlow,
    ) { intervalMs, limitMinutes, alertsEnabled, threeFingerEnabled ->
        SettingsUiState(
            intervalSeconds = (intervalMs / 1_000).toInt().coerceAtLeast(1),
            timeLimitMinutes = limitMinutes,
            alertsEnabled = alertsEnabled,
            threeFingerEnabled = threeFingerEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState.Initial,
    )

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

    private companion object {
        const val STOP_TIMEOUT_MS: Long = 5_000L
    }
}
