package com.freelanzer.autoscroller.core.di

import javax.inject.Qualifier

/**
 * Qualifier para distinguir el [kotlinx.coroutines.CoroutineScope] de proceso completo
 * (vive lo que vive la `Application`) de cualquier otro scope (ej. de ViewModel) cuando
 * se inyecte vía Hilt.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
