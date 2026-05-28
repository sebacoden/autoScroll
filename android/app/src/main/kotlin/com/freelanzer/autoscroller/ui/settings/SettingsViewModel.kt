package com.freelanzer.autoscroller.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.freelanzer.autoscroller.domain.controller.ScrollController
import com.freelanzer.autoscroller.domain.controller.ScrollState
import com.freelanzer.autoscroller.service.accessibility.AccessibilityServiceStatus
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
 * ViewModel de la pantalla principal (Ajustes). Combina:
 *  - Estado runtime: estado del servicio de accesibilidad, [ScrollController.state],
 *    [ScrollController.scrollCount].
 *  - Prefs persistidas: intervalo, límite de bienestar, alertas, trigger 3 dedos.
 *
 * Para evitar un `combine` con 7 flujos sobre `Array<Any?>` (sin type-safety), se separan
 * los flujos en dos grupos tipados y se combinan los dos resultados.
 *
 * El servicio de accesibilidad solo cambia desde Ajustes del sistema; [refreshServiceStatus]
 * lo invoca el composable cuando el ciclo de vida pasa por `ON_RESUME` (vuelta desde el
 * deep-link de accesibilidad).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val controller: ScrollController,
) : AndroidViewModel(application) {

    private val serviceEnabled = MutableStateFlow(
        AccessibilityServiceStatus.isEnabled(application),
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        runtimeStateFlow(),
        prefsStateFlow(),
    ) { runtime, prefs ->
        SettingsUiState(
            isServiceEnabled = runtime.isServiceEnabled,
            scrollState = runtime.scrollState,
            scrollCount = runtime.scrollCount,
            intervalSeconds = prefs.intervalSeconds,
            timeLimitMinutes = prefs.timeLimitMinutes,
            alertsEnabled = prefs.alertsEnabled,
            threeFingerEnabled = prefs.threeFingerEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState.Initial,
    )

    fun refreshServiceStatus() {
        serviceEnabled.value = AccessibilityServiceStatus.isEnabled(getApplication())
    }

    fun onToggleScroll() = controller.toggle()

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

    private fun runtimeStateFlow() = combine(
        serviceEnabled,
        controller.state,
        controller.scrollCount,
        ::RuntimeBundle,
    )

    private fun prefsStateFlow() = combine(
        settingsRepository.intervalMillisFlow,
        settingsRepository.timeLimitMinutesFlow,
        settingsRepository.alertsEnabledFlow,
        settingsRepository.threeFingerTriggerEnabledFlow,
    ) { intervalMs, limit, alerts, threeFinger ->
        PrefsBundle(
            intervalSeconds = (intervalMs / 1_000).toInt().coerceAtLeast(1),
            timeLimitMinutes = limit,
            alertsEnabled = alerts,
            threeFingerEnabled = threeFinger,
        )
    }

    private data class RuntimeBundle(
        val isServiceEnabled: Boolean,
        val scrollState: ScrollState,
        val scrollCount: Int,
    )

    private data class PrefsBundle(
        val intervalSeconds: Int,
        val timeLimitMinutes: Int,
        val alertsEnabled: Boolean,
        val threeFingerEnabled: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MS: Long = 5_000L
    }
}
