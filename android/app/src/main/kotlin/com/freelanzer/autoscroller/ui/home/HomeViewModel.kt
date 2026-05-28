package com.freelanzer.autoscroller.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freelanzer.autoscroller.domain.controller.ScrollController
import com.freelanzer.autoscroller.service.accessibility.AccessibilityServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel de la Home. Expone un único [StateFlow] de [HomeUiState] y centraliza la
 * interacción con el [ScrollController]. La edición del intervalo se movió a
 * [com.freelanzer.autoscroller.ui.settings.SettingsViewModel] (separación de pantallas).
 *
 * El servicio de accesibilidad solo se puede habilitar desde Ajustes del sistema, por eso
 * exponemos [refreshServiceStatus] para invocarlo en `onResume`.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val controller: ScrollController,
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

    private companion object {
        const val STOP_TIMEOUT_MS: Long = 5_000L
    }
}
