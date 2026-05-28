package com.freelanzer.autoscroller.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.freelanzer.autoscroller.domain.controller.ScrollController
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
 * ViewModel de la Home. Expone un único [StateFlow] de [HomeUiState] derivado de:
 *  - El estado del servicio de accesibilidad (consultado al sistema).
 *  - El estado, intervalo y contador del [ScrollController].
 *
 * Las acciones de UI (toggle, cambio de intervalo) se delegan al controller o al
 * [SettingsRepository] según corresponda (separación runtime / persistencia).
 *
 * El servicio de accesibilidad sólo se puede habilitar desde Ajustes del sistema, por lo
 * que [refreshServiceStatus] debe invocarse en `onResume` de la Activity.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val controller: ScrollController,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application) {

    private val serviceEnabled = MutableStateFlow(
        AccessibilityServiceStatus.isEnabled(application),
    )

    val uiState: StateFlow<HomeUiState> = combine(
        serviceEnabled,
        controller.state,
        controller.intervalMillis,
        controller.scrollCount,
    ) { enabled, state, intervalMs, count ->
        HomeUiState(
            isServiceEnabled = enabled,
            scrollState = state,
            intervalSeconds = (intervalMs / 1_000).toInt().coerceAtLeast(1),
            scrollCount = count,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState.Initial,
    )

    fun refreshServiceStatus() {
        serviceEnabled.value = AccessibilityServiceStatus.isEnabled(getApplication())
    }

    fun onToggleScroll() = controller.toggle()

    /**
     * Actualiza el intervalo persistido en DataStore. El controller refleja el cambio
     * automáticamente a través de [ScrollController.intervalMillis].
     */
    fun onIntervalSecondsChanged(seconds: Float) {
        val millis = (seconds * 1_000f).roundToLong()
            .coerceIn(SettingsRepository.MIN_INTERVAL_MS, SettingsRepository.MAX_INTERVAL_MS)
        viewModelScope.launch {
            settingsRepository.setIntervalMillis(millis)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS: Long = 5_000L
    }
}
