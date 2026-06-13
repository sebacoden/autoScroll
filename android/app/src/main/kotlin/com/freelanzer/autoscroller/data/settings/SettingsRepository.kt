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
 * Preferencias del usuario persistidas.
 *
 * Es una **interfaz** para que los consumidores (ViewModels) se testeen con un fake en
 * memoria sin DataStore. La implementación real ([DataStoreSettingsRepository]) se prueba
 * aparte contra un DataStore de verdad.
 */
interface SettingsRepository {
    val intervalMillisFlow: Flow<Long>
    val timeLimitMinutesFlow: Flow<Int>
    val alertsEnabledFlow: Flow<Boolean>
    val threeFingerTriggerEnabledFlow: Flow<Boolean>
    val swipeActivationEnabledFlow: Flow<Boolean>
    val eulaAcceptedFlow: Flow<Boolean>

    suspend fun setIntervalMillis(millis: Long)
    suspend fun setTimeLimitMinutes(minutes: Int)
    suspend fun setAlertsEnabled(enabled: Boolean)
    suspend fun setThreeFingerTriggerEnabled(enabled: Boolean)
    suspend fun setSwipeActivationEnabled(enabled: Boolean)
    suspend fun setEulaAccepted(accepted: Boolean)

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

        // Activación
        const val DEFAULT_THREE_FINGER_ENABLED: Boolean = true
        const val DEFAULT_SWIPE_ACTIVATION_ENABLED: Boolean = false
    }
}

/**
 * Implementación sobre Preferences DataStore. Fuente única de verdad de la persistencia.
 * La validación de rangos vive acá (contrato del repositorio, no del consumidor).
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val intervalMillisFlow: Flow<Long> =
        dataStore.read { it[KEY_INTERVAL_MS] ?: SettingsRepository.DEFAULT_INTERVAL_MS }

    override val timeLimitMinutesFlow: Flow<Int> =
        dataStore.read { it[KEY_TIME_LIMIT_MIN] ?: SettingsRepository.DEFAULT_TIME_LIMIT_MIN }

    override val alertsEnabledFlow: Flow<Boolean> =
        dataStore.read { it[KEY_ALERTS_ENABLED] ?: SettingsRepository.DEFAULT_ALERTS_ENABLED }

    override val threeFingerTriggerEnabledFlow: Flow<Boolean> =
        dataStore.read { it[KEY_THREE_FINGER_ENABLED] ?: SettingsRepository.DEFAULT_THREE_FINGER_ENABLED }

    override val swipeActivationEnabledFlow: Flow<Boolean> =
        dataStore.read { it[KEY_SWIPE_ACTIVATION_ENABLED] ?: SettingsRepository.DEFAULT_SWIPE_ACTIVATION_ENABLED }

    override val eulaAcceptedFlow: Flow<Boolean> =
        dataStore.read { it[KEY_EULA_ACCEPTED] ?: false }

    override suspend fun setIntervalMillis(millis: Long) {
        require(millis in SettingsRepository.MIN_INTERVAL_MS..SettingsRepository.MAX_INTERVAL_MS) {
            "Intervalo fuera de rango: $millis"
        }
        dataStore.edit { it[KEY_INTERVAL_MS] = millis }
    }

    override suspend fun setTimeLimitMinutes(minutes: Int) {
        require(minutes in SettingsRepository.MIN_TIME_LIMIT_MIN..SettingsRepository.MAX_TIME_LIMIT_MIN) {
            "Límite fuera de rango: $minutes"
        }
        dataStore.edit { it[KEY_TIME_LIMIT_MIN] = minutes }
    }

    override suspend fun setAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ALERTS_ENABLED] = enabled }
    }

    override suspend fun setThreeFingerTriggerEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_THREE_FINGER_ENABLED] = enabled }
    }

    override suspend fun setSwipeActivationEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SWIPE_ACTIVATION_ENABLED] = enabled }
    }

    override suspend fun setEulaAccepted(accepted: Boolean) {
        dataStore.edit { it[KEY_EULA_ACCEPTED] = accepted }
    }

    private fun <T> DataStore<Preferences>.read(transform: (Preferences) -> T): Flow<T> =
        data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map(transform)

    private companion object {
        val KEY_INTERVAL_MS = longPreferencesKey("interval_ms")
        val KEY_TIME_LIMIT_MIN = intPreferencesKey("time_limit_min")
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val KEY_EULA_ACCEPTED = booleanPreferencesKey("eula_accepted")
        val KEY_THREE_FINGER_ENABLED = booleanPreferencesKey("three_finger_enabled")
        val KEY_SWIPE_ACTIVATION_ENABLED = booleanPreferencesKey("swipe_activation_enabled")
    }
}
