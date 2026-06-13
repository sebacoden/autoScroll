package com.freelanzer.autoscroller.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provee el `DataStore<Preferences>` único de la app a Hilt.
 *
 * El delegate `preferencesDataStore` garantiza una única instancia por proceso, lo cual
 * exige aplicarlo a un objeto/contexto estable (extensión top-level sobre `Context`).
 */
private const val DATASTORE_NAME = "autoscroller_settings"

private val Context.autoScrollerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.autoScrollerDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsRepositoryModule {

    @Binds
    abstract fun bindSettingsRepository(
        impl: DataStoreSettingsRepository,
    ): SettingsRepository
}
