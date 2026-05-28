package com.freelanzer.autoscroller.ui.home

import com.freelanzer.autoscroller.domain.controller.ScrollState

/**
 * Snapshot inmutable del estado de la Home. El [HomeViewModel] deriva una nueva instancia
 * ante cualquier cambio en sus fuentes.
 */
data class HomeUiState(
    val isServiceEnabled: Boolean,
    val scrollState: ScrollState,
    val intervalSeconds: Int,
    val scrollCount: Int,
) {
    val isScrolling: Boolean get() = scrollState == ScrollState.Scrolling

    companion object {
        val Initial = HomeUiState(
            isServiceEnabled = false,
            scrollState = ScrollState.Idle,
            intervalSeconds = 4,
            scrollCount = 0,
        )
    }
}
