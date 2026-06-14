package com.freelanzer.autoscroller.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una app instalada relevante para el selector (con launcher). */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

/**
 * Lista las apps lanzables del dispositivo y resuelve etiquetas legibles. Abstracción para
 * desacoplar la UI de `PackageManager` (y poder fakear en tests).
 */
interface InstalledAppsProvider {
    suspend fun launchableApps(): List<InstalledApp>

    /** Etiqueta legible de un paquete; si no está instalado, devuelve el propio package. */
    fun labelFor(packageName: String): String
}

@Singleton
class RealInstalledAppsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstalledAppsProvider {

    private val packageManager: PackageManager get() = context.packageManager

    override suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0)
            .map { resolveInfo ->
                InstalledApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    override fun labelFor(packageName: String): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InstalledAppsModule {
    @Binds
    abstract fun bindInstalledAppsProvider(impl: RealInstalledAppsProvider): InstalledAppsProvider
}
