package com.freelanzer.autoscroller.service.wellbeing

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
     * Silencioso ante falta del permiso `POST_NOTIFICATIONS` (Android 13+).
     *
     * Defensa en profundidad: chequeo runtime explícito + `runCatching` para sobrevivir
     * a `SecurityException` ante revocación del permiso entre el check y la notificación.
     * El `@SuppressLint` se justifica porque ambas protecciones están presentes; lint no
     * puede inferirlas porque están detrás de un helper privado.
     */
    @SuppressLint("MissingPermission")
    fun refreshOngoing(elapsedMs: Long, limitMinutes: Int) {
        if (!hasNotificationPermission()) return
        runCatching {
            compatManager.notify(
                NOTIFICATION_ID_ONGOING,
                buildOngoing(elapsedMs, limitMinutes),
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun postAlert() {
        if (!hasNotificationPermission()) return
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

    /**
     * En Android 13+ `POST_NOTIFICATIONS` es runtime; antes era automática. El chequeo
     * explícito permite a Lint validar que las llamadas a `notify()` están protegidas.
     */
    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
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
