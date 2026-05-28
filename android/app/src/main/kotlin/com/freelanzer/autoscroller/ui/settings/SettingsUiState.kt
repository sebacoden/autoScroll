package com.freelanzer.autoscroller.ui.settings

/**
 * Snapshot inmutable de los ajustes visibles en pantalla. Derivado de los `Flow` del
 * `SettingsRepository` por el [SettingsViewModel].
 */
data class SettingsUiState(
    val intervalSeconds: Int,
    val timeLimitMinutes: Int,
    val alertsEnabled: Boolean,
    val threeFingerEnabled: Boolean,
) {
    companion object {
        val Initial = SettingsUiState(
            intervalSeconds = 4,
            timeLimitMinutes = 30,
            alertsEnabled = true,
            threeFingerEnabled = false,
        )
    }
}
