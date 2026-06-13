package com.freelanzer.autoscroller.data.usage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una sesión de auto-scroll registrada localmente (bienestar digital).
 *
 * `startTime`/`endTime` son epoch millis (reloj de pared) para poder agrupar por día.
 * La duración se deriva como `endTime - startTime`.
 */
@Entity(tableName = "scroll_sessions")
data class ScrollSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val swipeCount: Int,
    val appPackage: String,
)
