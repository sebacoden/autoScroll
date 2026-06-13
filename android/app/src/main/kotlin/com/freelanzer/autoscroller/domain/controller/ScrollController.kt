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
 *    cada vez que se llama a [start]).
 *  - `lastSwipeAtMs`: timestamp monotónico del último swipe; lo provee el caller
 *    (ScrollEngine), no lo lee el controller. Eso mantiene este clase 100% Kotlin
 *    puro y testeable en JVM sin shadowing de `SystemClock`.
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

    @Volatile var lastSwipeAtMs: Long = 0L
        private set

    /** Inicia el auto-scroll y resetea el contador y la marca temporal de sesión. */
    fun start() {
        _scrollCount.value = 0
        lastSwipeAtMs = 0L
        _state.value = ScrollState.Scrolling
    }

    fun stop() {
        _state.value = ScrollState.Idle
    }

    /** Pausa reservada para la detección de doble-toque / scroll manual (fase 4). */
    fun pause() = _state.update { current ->
        if (current == ScrollState.Scrolling) ScrollState.Paused else current
    }

    fun toggle() {
        when (_state.value) {
            ScrollState.Idle, ScrollState.Paused -> start()
            ScrollState.Scrolling -> stop()
        }
    }

    /**
     * Notifica al controller que se ejecutó un swipe.
     * El caller (ScrollEngine) provee el `elapsedRealtimeMs` para que esta clase no
     * dependa de `android.os.SystemClock` y pueda testearse en JVM sin shims.
     */
    fun onScrollPerformed(elapsedRealtimeMs: Long) {
        lastSwipeAtMs = elapsedRealtimeMs
        _scrollCount.update { it + 1 }
    }
}
