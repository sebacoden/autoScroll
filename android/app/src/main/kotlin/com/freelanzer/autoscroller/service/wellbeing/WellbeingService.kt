package com.freelanzer.autoscroller.service.wellbeing

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service del módulo de bienestar digital (paso 3 del roadmap).
 *
 * Vive durante la sesión de auto-scroll:
 *  - Mantiene una notificación *ongoing* con el tiempo transcurrido.
 *  - Tick interno cada [TICK_INTERVAL_MS] que actualiza la notificación y compara el
 *    elapsed contra el límite configurado.
 *  - Al superar el límite emite la notificación de alerta una única vez por sesión
 *    (si las alertas están habilitadas).
 *
 * Lo arranca y detiene el [com.freelanzer.autoscroller.service.accessibility.AutoScrollService]
 * según el estado del [com.freelanzer.autoscroller.domain.controller.ScrollController].
 *
 * `SystemClock.elapsedRealtime()` (monotónico, inmune a cambios de hora) se usa para el
 * tiempo transcurrido.
 */
@AndroidEntryPoint
class WellbeingService : LifecycleService() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notifier: WellbeingNotifier

    private var tickerJob: Job? = null
    private var sessionStartElapsed: Long = 0L
    private var alertPosted: Boolean = false

    @Volatile private var alertsEnabled: Boolean = SettingsRepository.DEFAULT_ALERTS_ENABLED
    /** Única fuente de verdad del límite; los millis se derivan on-the-fly cuando hacen falta. */
    @Volatile private var currentLimitMinutes: Int = SettingsRepository.DEFAULT_TIME_LIMIT_MIN

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()

        lifecycleScope.launch {
            settingsRepository.timeLimitMinutesFlow.collect { minutes ->
                currentLimitMinutes = minutes
            }
        }
        lifecycleScope.launch {
            settingsRepository.alertsEnabledFlow.collect { alertsEnabled = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (sessionStartElapsed == 0L) {
            sessionStartElapsed = SystemClock.elapsedRealtime()
            alertPosted = false
        }
        startInForeground()
        startTicker()
        // El ciclo de vida lo gobierna AutoScrollService (start en Scrolling, stop en Idle).
        // Es un FGS atado a una sesión efímera: si el sistema lo mata, NO debe recrearse solo
        // sin sesión activa (evita una notificación ongoing huérfana).
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val initial = notifier.buildOngoing(
            elapsedMs = SystemClock.elapsedRealtime() - sessionStartElapsed,
            limitMinutes = currentLimitMinutes,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                WellbeingNotifier.NOTIFICATION_ID_ONGOING,
                initial,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(WellbeingNotifier.NOTIFICATION_ID_ONGOING, initial)
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - sessionStartElapsed
                val limitMillis = TimeUnit.MINUTES.toMillis(currentLimitMinutes.toLong())
                notifier.refreshOngoing(elapsed, currentLimitMinutes)
                if (!alertPosted && elapsed >= limitMillis) {
                    alertPosted = true
                    if (alertsEnabled) notifier.postAlert()
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TICK_INTERVAL_MS: Long = 10_000L
    }
}
