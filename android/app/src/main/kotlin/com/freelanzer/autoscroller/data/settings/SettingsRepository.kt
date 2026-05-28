package com.freelanzer.autoscroller.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Persistencia de preferencias del usuario (paso 2 del roadmap: configuración dinámica).
 *
 * Fuente única de verdad para los valores **persistidos**; los consumidores observan los
 * `Flow` y propagan los cambios a sus respectivos modelos de estado runtime.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Intervalo entre swipes en milisegundos. Default [DEFAULT_INTERVAL_MS]. */
    val intervalMillisFlow: Flow<Long> = dataStore.data
        .catch { exception ->
            // DataStore lanza IOException ante archivos corruptos/no encontrados;
            // resto se propaga porque indicaría un bug.
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> prefs[KEY_INTERVAL_MS] ?: DEFAULT_INTERVAL_MS }

    /**
     * Guarda el intervalo. Valida en el borde público: el rango es contrato del repositorio,
     * no se delega a los consumidores.
     */
    suspend fun setIntervalMillis(millis: Long) {
        require(millis in MIN_INTERVAL_MS..MAX_INTERVAL_MS) {
            "Intervalo fuera de rango ($MIN_INTERVAL_MS..$MAX_INTERVAL_MS ms): $millis"
        }
        dataStore.edit { it[KEY_INTERVAL_MS] = millis }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 4_000L
        const val MIN_INTERVAL_MS: Long = 1_000L
        const val MAX_INTERVAL_MS: Long = 30_000L

        private val KEY_INTERVAL_MS = longPreferencesKey("interval_ms")
    }
}
