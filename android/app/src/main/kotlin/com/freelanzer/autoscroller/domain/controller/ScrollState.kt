package com.freelanzer.autoscroller.domain.controller

/**
 * Estados del motor de auto-scroll.
 *
 * La pausa por interacción del usuario (toque / scroll) no es un estado acá: la maneja el
 * `ScrollEngine` suspendiendo los swipes temporalmente, sin salir de [Scrolling].
 */
enum class ScrollState {
    Idle,
    Scrolling,
}
