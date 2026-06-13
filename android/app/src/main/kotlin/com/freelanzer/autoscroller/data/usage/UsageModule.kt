package com.freelanzer.autoscroller.data.usage

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provee la base de datos de uso, su DAO y el binding del repositorio.
 *
 * Se parte en dos: un `object` para los `@Provides` (DB, DAO) y una clase `abstract`
 * para el `@Binds` del repositorio — Hilt requiere esa separación.
 */
@Module
@InstallIn(SingletonComponent::class)
object UsageProvidesModule {

    @Provides
    @Singleton
    fun provideUsageDatabase(@ApplicationContext context: Context): UsageDatabase =
        Room.databaseBuilder(context, UsageDatabase::class.java, "autoscroller_usage.db")
            .build()

    @Provides
    fun provideScrollSessionDao(database: UsageDatabase): ScrollSessionDao =
        database.scrollSessionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UsageBindsModule {

    @dagger.Binds
    abstract fun bindUsageRepository(impl: RoomUsageRepository): UsageRepository
}
