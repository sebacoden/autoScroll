package com.freelanzer.autoscroller.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freelanzer.autoscroller.ui.apps.AppPickerScreen
import com.freelanzer.autoscroller.ui.eula.EulaScreen
import com.freelanzer.autoscroller.ui.settings.SettingsScreen

/**
 * Grafo único de la app: EULA → SETTINGS (principal) → APP_PICKER (selector de apps).
 * `startDestination` se decide en `MainActivity` según el flag `eulaAccepted` persistido.
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
            SettingsScreen(
                onNavigateToAppPicker = { navController.navigate(AppRoutes.APP_PICKER) },
            )
        }
        composable(AppRoutes.APP_PICKER) {
            AppPickerScreen(onBack = { navController.popBackStack() })
        }
    }
}
