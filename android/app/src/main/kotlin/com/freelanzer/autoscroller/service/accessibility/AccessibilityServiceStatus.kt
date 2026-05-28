package com.freelanzer.autoscroller.service.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Helpers para consultar si el servicio de accesibilidad está habilitado en los ajustes del
 * sistema. Se basa en `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, el cual contiene la
 * lista de servicios activados separada por `:`.
 */
object AccessibilityServiceStatus {

    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AutoScrollService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        for (component in splitter) {
            if (component.equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
