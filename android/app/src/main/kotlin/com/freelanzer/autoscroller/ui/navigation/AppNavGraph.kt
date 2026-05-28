package com.freelanzer.autoscroller.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freelanzer.autoscroller.ui.eula.EulaScreen
import com.freelanzer.autoscroller.ui.home.HomeScreen
import com.freelanzer.autoscroller.ui.settings.SettingsScreen

/**
 * Grafo único de la app. La [startDestination] se decide en `MainActivity` según el flag
 * `eulaAccepted` persistido, leído sincrónicamente al `onCreate` para evitar parpadeos.
 *
 * Al aceptar EULA, navegamos a HOME con `popUpTo(EULA){ inclusive = true }` para que
 * el back gesture no vuelva a mostrar el EULA.
 */
@Composable
fun AppNavGraph(startDestination: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppRoutes.EULA) {
            EulaScreen(
                onAccepted = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.EULA) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoutes.HOME) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(AppRoutes.SETTINGS) },
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
