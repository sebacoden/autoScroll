package com.freelanzer.autoscroller.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freelanzer.autoscroller.ui.eula.EulaScreen
import com.freelanzer.autoscroller.ui.settings.SettingsScreen

/**
 * Grafo único de la app: dos rutas (EULA + SETTINGS).
 * `startDestination` se decide en `MainActivity` según el flag `eulaAccepted` persistido.
 * Al aceptar la EULA navegamos a SETTINGS con `popUpTo(EULA){ inclusive = true }` para
 * que el back gesture no regrese a la EULA.
 */
@Composable
fun AppNavGraph(startDestination: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppRoutes.EULA) {
            EulaScreen(
                onAccepted = {
                    navController.navigate(AppRoutes.SETTINGS) {
                        popUpTo(AppRoutes.EULA) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen()
        }
    }
}
