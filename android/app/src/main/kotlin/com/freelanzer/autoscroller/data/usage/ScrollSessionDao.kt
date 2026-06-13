package com.freelanzer.autoscroller.data.usage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a datos de las sesiones de scroll. Room valida estas queries en tiempo de
 * compilación (un error de columna rompe el build, no al usuario).
 */
@Dao
interface ScrollSessionDao {

    @Insert
    suspend fun insert(session: ScrollSessionEntity): Long

    @Query("SELECT * FROM scroll_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<ScrollSessionEntity>>

    @Query("SELECT COALESCE(SUM(swipeCount), 0) FROM scroll_sessions")
    fun observeTotalSwipes(): Flow<Int>

    /** Uso agregado por app, de mayor a menor duración total. */
    @Query(
        """
        SELECT appPackage AS appPackage,
               SUM(endTime - startTime) AS totalDurationMs,
               COUNT(*) AS sessionCount
        FROM scroll_sessions
        GROUP BY appPackage
        ORDER BY totalDurationMs DESC
        """,
    )
    fun observeUsageByApp(): Flow<List<AppUsage>>

    @Query("DELETE FROM scroll_sessions")
    suspend fun clear()
}
