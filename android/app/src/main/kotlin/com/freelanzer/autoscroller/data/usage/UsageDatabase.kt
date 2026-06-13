package com.freelanzer.autoscroller.data.usage

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base de datos Room de uso (bienestar digital).
 *
 * `exportSchema = false` por ahora (version = 1). Cuando se introduzcan migraciones,
 * conviene exportar el esquema (`room.schemaLocation`) para versionarlo.
 */
@Database(
    entities = [ScrollSessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun scrollSessionDao(): ScrollSessionDao
}
