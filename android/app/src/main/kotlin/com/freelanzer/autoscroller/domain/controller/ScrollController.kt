package com.freelanzer.autoscroller.domain.controller

import com.freelanzer.autoscroller.core.di.ApplicationScope
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Fuente única de verdad del estado **runtime** del auto-scroll.
 *
 *  - `state`: máquina de estados (Idle / Scrolling / Paused). Ver especificación §3.B.
 *  - `intervalMillis`: espejo de [SettingsRepository.intervalMillisFlow] convertido a
 *    `StateFlow` mediante `stateIn` para que UI y servicio puedan leer `.value` sin
 *    suspender.
 *  - `scrollCount`: cantidad de swipes ejecutados en la sesión actual (se resetea
 *    cada vez que se llama a [start]). Útil para feedback en el panel de prueba.
 *
 * El controller **no** persiste el intervalo; eso es responsabilidad del repositorio
 * (separación de responsabilidades persistencia / runtime).
 */
@Singleton
class ScrollController @Inject constructor(
    settingsRepository: SettingsRepository,
    @ApplicationScope appScope: CoroutineScope,
) {

    private val _state = MutableStateFlow(ScrollState.Idle)
    val state: StateFlow<ScrollState> = _state.asStateFlow()

    val intervalMillis: StateFlow<Long> = settingsRepository.intervalMillisFlow.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsRepository.DEFAULT_INTERVAL_MS,
    )

    private val _scrollCount = MutableStateFlow(0)
    val scrollCount: StateFlow<Int> = _scrollCount.asStateFlow()

    /** Inicia el auto-scroll y resetea el contador de sesión. */
    fun start() {
        _scrollCount.value = 0
        _state.value = ScrollState.Scrolling
    }

    fun stop() {
        _state.value = ScrollState.Idle
    }

    /** Pausa reservada para detección de doble-toque / scroll manual (roadmap fase 3). */
    fun pause() = _state.update { current ->
        if (current == ScrollState.Scrolling) ScrollState.Paused else current
    }

    fun toggle() {
        when (_state.value) {
            ScrollState.Idle, ScrollState.Paused -> start()
            ScrollState.Scrolling -> stop()
        }
    }

    /** Llamado por [com.freelanzer.autoscroller.service.accessibility.ScrollEngine] tras cada swipe. */
    fun onScrollPerformed() {
        _scrollCount.update { it + 1 }
    }
}
