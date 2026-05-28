package com.freelanzer.autoscroller.domain.controller

/**
 * Estados posibles del motor de auto-scroll.
 *
 * Modelado como máquina de estados explícita (ver especificación §3.B).
 * `Paused` queda reservado para la lógica de pausa inteligente (doble-toque, scroll manual)
 * que se implementará en fases posteriores del roadmap.
 */
enum class ScrollState {
    Idle,
    Scrolling,
    Paused,
}
