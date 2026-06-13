package com.freelanzer.autoscroller.data.usage

/**
 * Proyección de la agregación de uso por aplicación (resultado de un `GROUP BY appPackage`).
 */
data class AppUsage(
    val appPackage: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
)
