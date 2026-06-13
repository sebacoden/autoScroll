package com.freelanzer.autoscroller.core.service

import android.content.Context
import com.freelanzer.autoscroller.service.accessibility.AccessibilityServiceStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstracción del chequeo de estado del [com.freelanzer.autoscroller.service.accessibility.AutoScrollService].
 * Existe para desacoplar a los `ViewModel` de `android.content.Context` y permitir que
 * sus tests unitarios corran en JVM puro con un fake.
 */
interface ServiceStatusProvider {
    fun isAutoScrollServiceEnabled(): Boolean
}

@Singleton
class RealServiceStatusProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ServiceStatusProvider {
    override fun isAutoScrollServiceEnabled(): Boolean =
        AccessibilityServiceStatus.isEnabled(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceStatusModule {
    @Binds
    abstract fun bindServiceStatusProvider(
        impl: RealServiceStatusProvider,
    ): ServiceStatusProvider
}
