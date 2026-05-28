package com.freelanzer.autoscroller.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Persistencia de preferencias del usuario.
 *
 * Fuente única de verdad para los valores **persistidos**; los consumidores observan los
 * `Flow` y propagan los cambios a sus modelos de estado runtime correspondientes.
 *
 *  - [intervalMillisFlow]: tiempo entre swipes (paso 2 del roadmap).
 *  - [timeLimitMinutesFlow]: límite de bienestar digital (paso 3).
 *  - [alertsEnabledFlow]: si emitir la alerta al alcanzar el límite.
 *  - [eulaAcceptedFlow]: si el usuario aceptó los términos al primer inicio.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val intervalMillisFlow: Flow<Long> = dataStore.read { it[KEY_INTERVAL_MS] ?: DEFAULT_INTERVAL_MS }

    val timeLimitMinutesFlow: Flow<Int> = dataStore.read {
        it[KEY_TIME_LIMIT_MIN] ?: DEFAULT_TIME_LIMIT_MIN
    }

    val alertsEnabledFlow: Flow<Boolean> = dataStore.read {
        it[KEY_ALERTS_ENABLED] ?: DEFAULT_ALERTS_ENABLED
    }

    val eulaAcceptedFlow: Flow<Boolean> = dataStore.read {
        it[KEY_EULA_ACCEPTED] ?: false
    }

    val threeFingerTriggerEnabledFlow: Flow<Boolean> = dataStore.read {
        it[KEY_THREE_FINGER_ENABLED] ?: DEFAULT_THREE_FINGER_ENABLED
    }

    suspend fun setIntervalMillis(millis: Long) {
        require(millis in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
            "Intervalo fuera de rango ($MIN_INTERVAL_MS..$MAX_INTERVAL_MS ms): $millis"
        }
        dataStore.edit { it[KEY_INTERVAL_MS] = millis }
    }

    suspend fun setTimeLimitMinutes(minutes: Int) {
        require(minutes in MIN_TIME_LIMIT_MIN..MAX_TIME_LIMIT_MIN) {
            "Límite fuera de rango ($MIN_TIME_LIMIT_MIN..$MAX_TIME_LIMIT_MIN min): $minutes"
        }
        dataStore.edit { it[KEY_TIME_LIMIT_MIN] = minutes }
    }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ALERTS_ENABLED] = enabled }
    }

    suspend fun setEulaAccepted(accepted: Boolean) {
        dataStore.edit { it[KEY_EULA_ACCEPTED] = accepted }
    }

    suspend fun setThreeFingerTriggerEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_THREE_FINGER_ENABLED] = enabled }
    }

    /**
     * Atajo interno: aplica recuperación ante `IOException` (archivo corrupto o ausente)
     * y proyecta a un tipo concreto en una sola línea.
     */
    private fun <T> DataStore<Preferences>.read(transform: (Preferences) -> T): Flow<T> =
        data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map(transform)

    companion object {
        // Intervalo entre swipes
        const val DEFAULT_INTERVAL_MS: Long = 4_000L
        const val MIN_INTERVAL_MS: Long = 1_000L
        const val MAX_INTERVAL_MS: Long = 30_000L

        // Bienestar digital
        const val DEFAULT_TIME_LIMIT_MIN: Int = 30
        const val MIN_TIME_LIMIT_MIN: Int = 5
        const val MAX_TIME_LIMIT_MIN: Int = 180
        const val DEFAULT_ALERTS_ENABLED: Boolean = true

        // Trigger 3 dedos (gesto del AccessibilityService)
        const val DEFAULT_THREE_FINGER_ENABLED: Boolean = false

        private val KEY_INTERVAL_MS = longPreferencesKey("interval_ms")
        private val KEY_TIME_LIMIT_MIN = intPreferencesKey("time_limit_min")
        private val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        private val KEY_EULA_ACCEPTED = booleanPreferencesKey("eula_accepted")
        private val KEY_THREE_FINGER_ENABLED = booleanPreferencesKey("three_finger_enabled")
    }
}
