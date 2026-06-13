package com.freelanzer.autoscroller.ui.settings

/**
 * Snapshot inmutable de la pantalla principal (Ajustes).
 *
 * Tras la consolidación de la fase 5 y la remoción del panel de prueba, la UI no
 * controla el scroll directamente; solo refleja prefs persistidas + estado del
 * servicio de accesibilidad. La activación efectiva ocurre vía tap con 3 dedos.
 */
data class SettingsUiState(
    val isServiceEnabled: Boolean,
    val intervalSeconds: Int,
    val timeLimitMinutes: Int,
    val alertsEnabled: Boolean,
    val threeFingerEnabled: Boolean,
    val swipeActivationEnabled: Boolean,
) {
    companion object {
        val Initial = SettingsUiState(
            isServiceEnabled = false,
            intervalSeconds = 4,
            timeLimitMinutes = 30,
            alertsEnabled = true,
            threeFingerEnabled = true,
            swipeActivationEnabled = false,
        )
    }
}
