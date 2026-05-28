package com.freelanzer.autoscroller.ui.navigation

/**
 * Rutas del grafo de navegación. Centralizadas como constantes para evitar typos
 * repartidos por la base de código.
 *
 * Tras la consolidación de la fase 5, [SETTINGS] funciona como la pantalla principal
 * (post-onboarding); contiene el estado del servicio, prefs persistidas y el panel de
 * prueba en un único Scaffold. La activación primaria es el tap con 3 dedos del
 * AccessibilityService.
 */
object AppRoutes {
    const val EULA = "eula"
    const val SETTINGS = "settings"
}
