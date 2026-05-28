package com.freelanzer.autoscroller.ui.settings

import com.freelanzer.autoscroller.domain.controller.ScrollState

/**
 * Snapshot inmutable de la pantalla principal (que ahora es Ajustes).
 *
 * Combina prefs persistidas (intervalo, límite, alertas, trigger 3 dedos) con estado
 * runtime (servicio habilitado, scroll state, contador de swipes) para que la UI tenga
 * una única fuente de verdad recompuesta.
 */
data class SettingsUiState(
    val isServiceEnabled: Boolean,
    val scrollState: ScrollState,
    val scrollCount: Int,
    val intervalSeconds: Int,
    val timeLimitMinutes: Int,
    val alertsEnabled: Boolean,
    val threeFingerEnabled: Boolean,
) {
    val isScrolling: Boolean get() = scrollState == ScrollState.Scrolling

    companion object {
        val Initial = SettingsUiState(
            isServiceEnabled = false,
            scrollState = ScrollState.Idle,
            scrollCount = 0,
            intervalSeconds = 4,
            timeLimitMinutes = 30,
            alertsEnabled = true,
            threeFingerEnabled = true,
        )
    }
}
