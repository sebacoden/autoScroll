package com.freelanzer.autoscroller.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.freelanzer.autoscroller.domain.controller.ScrollController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receptor de **debug** para alternar el `ScrollController` vía `adb`.
 *
 * Existe solo en el source set `debug/` (no se incluye en builds release). Sirve para
 * verificar el flujo end-to-end del toggle del auto-scroll sin tener que simular un
 * gesto de 3 dedos, que en el emulador es genuinamente difícil (no hay shortcut nativo;
 * solo `Ctrl+drag` para 2 dedos / pinch).
 *
 * Uso:
 * ```
 * adb shell am broadcast -a com.freelanzer.autoscroller.debug.TOGGLE
 * ```
 *
 * Atajos relacionados (también solo en debug):
 *  - START: fuerza `ScrollState.Scrolling`.
 *  - STOP : fuerza `ScrollState.Idle`.
 */
@AndroidEntryPoint
class DebugToggleReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: ScrollController

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_START -> controller.start()
            ACTION_STOP -> controller.stop()
            ACTION_TOGGLE -> controller.toggle()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.freelanzer.autoscroller.debug.TOGGLE"
        const val ACTION_START = "com.freelanzer.autoscroller.debug.START"
        const val ACTION_STOP = "com.freelanzer.autoscroller.debug.STOP"
    }
}
