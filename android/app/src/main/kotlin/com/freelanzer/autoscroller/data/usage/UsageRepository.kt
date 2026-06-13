package com.freelanzer.autoscroller.data.usage

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Fuente de verdad del historial de uso (bienestar digital).
 *
 * Es una interfaz para que los consumidores (p. ej. [com.freelanzer.autoscroller.domain.usage.SessionRecorder])
 * se testeen con un fake sin levantar Room.
 */
interface UsageRepository {
    suspend fun recordSession(startTime: Long, endTime: Long, swipeCount: Int, appPackage: String)
    fun observeSessions(): Flow<List<ScrollSessionEntity>>
    fun observeUsageByApp(): Flow<List<AppUsage>>
    fun observeTotalSwipes(): Flow<Int>
}

@Singleton
class RoomUsageRepository @Inject constructor(
    private val dao: ScrollSessionDao,
) : UsageRepository {

    override suspend fun recordSession(
        startTime: Long,
        endTime: Long,
        swipeCount: Int,
        appPackage: String,
    ) {
        require(endTime >= startTime) { "endTime ($endTime) < startTime ($startTime)" }
        require(swipeCount >= 0) { "swipeCount negativo: $swipeCount" }
        dao.insert(
            ScrollSessionEntity(
                startTime = startTime,
                endTime = endTime,
                swipeCount = swipeCount,
                appPackage = appPackage,
            ),
        )
    }

    override fun observeSessions(): Flow<List<ScrollSessionEntity>> = dao.observeAll()
    override fun observeUsageByApp(): Flow<List<AppUsage>> = dao.observeUsageByApp()
    override fun observeTotalSwipes(): Flow<Int> = dao.observeTotalSwipes()
}
