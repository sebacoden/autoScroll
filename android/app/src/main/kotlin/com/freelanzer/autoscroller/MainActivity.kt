package com.freelanzer.autoscroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.freelanzer.autoscroller.core.ui.theme.AutoScrollerTheme
import com.freelanzer.autoscroller.data.settings.SettingsRepository
import com.freelanzer.autoscroller.ui.navigation.AppNavGraph
import com.freelanzer.autoscroller.ui.navigation.AppRoutes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Punto de entrada de UI. Decide la pantalla inicial según el flag EULA persistido.
 *
 * La lectura del flag se hace **fuera del hilo principal** (DataStore es IO): mientras se
 * resuelve, la SplashScreen del sistema se mantiene visible (sin parpadeo de la EULA cuando
 * ya fue aceptada y sin bloquear el arranque).
 *
 * El refresh del estado del servicio de accesibilidad al volver del system settings lo
 * maneja `SettingsScreen` vía `LifecycleResumeEffect`; no es necesario un override de
 * `onResume` acá.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // `null` = todavía resolviendo el destino; la splash se mantiene hasta tenerlo.
        var startDestination by mutableStateOf<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDestination == null }

        lifecycleScope.launch {
            startDestination =
                if (settingsRepository.eulaAcceptedFlow.first()) AppRoutes.SETTINGS else AppRoutes.EULA
        }

        setContent {
            AutoScrollerTheme {
                startDestination?.let { AppNavGraph(startDestination = it) }
            }
        }
    }
}
