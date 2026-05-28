package com.freelanzer.autoscroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.freelanzer.autoscroller.core.ui.theme.AutoScrollerTheme
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.freelanzer.autoscroller.ui.navigation.AppNavGraph
import com.freelanzer.autoscroller.ui.navigation.AppRoutes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Punto de entrada de UI. Decide la pantalla inicial leyendo el flag EULA en forma
 * sincrónica al `onCreate` (DataStore lookup pequeño, evita el parpadeo de la EULA
 * cuando ya fue aceptada).
 *
 * El refresh del estado del servicio de accesibilidad al volver del system settings lo
 * maneja `SettingsScreen` vía `LifecycleResumeEffect`; no es necesario un override de
 * `onResume` acá.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = runBlocking {
            if (settingsRepository.eulaAcceptedFlow.first()) AppRoutes.SETTINGS else AppRoutes.EULA
        }

        setContent {
            AutoScrollerTheme {
                AppNavGraph(startDestination = startDestination)
            }
        }
    }
}
