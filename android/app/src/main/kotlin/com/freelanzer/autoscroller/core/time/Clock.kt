package com.freelanzer.autoscroller.core.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Abstracción del reloj de pared (epoch millis). Permite inyectar un reloj falso en tests
 * para verificar lógica dependiente de tiempo sin depender de `System.currentTimeMillis()`.
 */
fun interface Clock {
    fun nowMillis(): Long
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
