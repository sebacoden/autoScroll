package com.freelanzer.autoscroller.ui.settings

import com.freelanzer.autoscroller.data.settings.SettingsRepository

/** Una app de la allowlist con su etiqueta legible para mostrar en la lista. */
data class ActivationApp(
    val packageName: String,
    val label: String,
)

/**
 * Snapshot inmutable de la pantalla principal (Ajustes). Refleja prefs persistidas +
 * estado del servicio de accesibilidad.
 */
data class SettingsUiState(
    val isServiceEnabled: Boolean,
    val intervalSeconds: Int,
    val timeLimitMinutes: Int,
    val alertsEnabled: Boolean,
    val threeFingerEnabled: Boolean,
    val swipeActivationEnabled: Boolean,
    val requiredSwipes: Int,
    val pauseSeconds: Int,
    val activationApps: List<ActivationApp>,
) {
    companion object {
        val Initial = SettingsUiState(
            isServiceEnabled = false,
            intervalSeconds = 4,
            timeLimitMinutes = 30,
            alertsEnabled = true,
            threeFingerEnabled = true,
            swipeActivationEnabled = true,
            requiredSwipes = SettingsRepository.DEFAULT_REQUIRED_SWIPES,
            pauseSeconds = SettingsRepository.DEFAULT_PAUSE_ON_TOUCH_SEC,
            activationApps = emptyList(),
        )
    }
}
