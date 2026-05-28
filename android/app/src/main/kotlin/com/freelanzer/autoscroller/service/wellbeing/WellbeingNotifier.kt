package com.freelanzer.autoscroller.service.wellbeing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.freelanzer.autoscroller.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centraliza canales y notificaciones del módulo de bienestar.
 *
 * Expuesto como `@Singleton` para que el [WellbeingService] y la creación de canales
 * compartan las mismas constantes y formato del tiempo.
 */
@Singleton
class WellbeingNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val systemManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private val compatManager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            context.getString(R.string.notif_channel_ongoing_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_ongoing_desc)
            setShowBadge(false)
        }
        val alert = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.notif_channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_alert_desc)
        }
        systemManager.createNotificationChannels(listOf(ongoing, alert))
    }

    fun buildOngoing(elapsedMs: Long, limitMinutes: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setContentTitle(context.getString(R.string.notif_ongoing_title))
            .setContentText(
                context.getString(
                    R.string.notif_ongoing_text,
                    formatElapsed(elapsedMs),
                    limitMinutes,
                ),
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    /**
     * Refresca la notificación ongoing in-place (mismo `NOTIFICATION_ID_ONGOING`).
     * Silencioso ante falta de permiso `POST_NOTIFICATIONS` (Android 13+).
     */
    fun refreshOngoing(elapsedMs: Long, limitMinutes: Int) {
        runCatching {
            compatManager.notify(
                NOTIFICATION_ID_ONGOING,
                buildOngoing(elapsedMs, limitMinutes),
            )
        }
    }

    fun postAlert() {
        val alert = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle(context.getString(R.string.notif_alert_title))
            .setContentText(context.getString(R.string.notif_alert_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { compatManager.notify(NOTIFICATION_ID_ALERT, alert) }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs)
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    companion object {
        const val CHANNEL_ONGOING = "autoscroller_wellbeing_ongoing"
        const val CHANNEL_ALERT = "autoscroller_wellbeing_alert"
        const val NOTIFICATION_ID_ONGOING = 1001
        const val NOTIFICATION_ID_ALERT = 1002
    }
}
