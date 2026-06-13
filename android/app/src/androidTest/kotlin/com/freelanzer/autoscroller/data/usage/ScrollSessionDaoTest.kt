package com.freelanzer.autoscroller.data.usage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test instrumentado del [ScrollSessionDao] contra una base Room **in-memory** (SQLite
 * real en el device/emulador). Verifica inserción y las queries de agregación —
 * determinista, no depende de YouTube ni de UI.
 *
 * Correr con: `./gradlew :app:connectedDebugAndroidTest` (requiere device/emulador).
 */
@RunWith(AndroidJUnit4::class)
class ScrollSessionDaoTest {

    private lateinit var database: UsageDatabase
    private lateinit var dao: ScrollSessionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UsageDatabase::class.java,
        ).build()
        dao = database.scrollSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndObserveAll() = runTest {
        dao.insert(session(start = 0, end = 1_000, swipes = 3, app = "com.tiktok"))
        dao.insert(session(start = 2_000, end = 5_000, swipes = 7, app = "com.instagram"))

        val all = dao.observeAll().first()
        assertThat(all).hasSize(2)
        // Orden por startTime DESC: la más reciente primero.
        assertThat(all.first().appPackage).isEqualTo("com.instagram")
    }

    @Test
    fun observeTotalSwipes_sumsAllSessions() = runTest {
        dao.insert(session(swipes = 3))
        dao.insert(session(swipes = 7))
        dao.insert(session(swipes = 5))

        assertThat(dao.observeTotalSwipes().first()).isEqualTo(15)
    }

    @Test
    fun observeTotalSwipes_emptyIsZero() = runTest {
        assertThat(dao.observeTotalSwipes().first()).isEqualTo(0)
    }

    @Test
    fun observeUsageByApp_groupsAndOrdersByDuration() = runTest {
        // tiktok: 1000 + 4000 = 5000 ms en 2 sesiones
        dao.insert(session(start = 0, end = 1_000, app = "com.tiktok"))
        dao.insert(session(start = 10_000, end = 14_000, app = "com.tiktok"))
        // instagram: 2000 ms en 1 sesión
        dao.insert(session(start = 0, end = 2_000, app = "com.instagram"))

        val usage = dao.observeUsageByApp().first()
        assertThat(usage).hasSize(2)
        // Ordenado por duración total DESC → tiktok primero.
        assertThat(usage[0].appPackage).isEqualTo("com.tiktok")
        assertThat(usage[0].totalDurationMs).isEqualTo(5_000)
        assertThat(usage[0].sessionCount).isEqualTo(2)
        assertThat(usage[1].appPackage).isEqualTo("com.instagram")
        assertThat(usage[1].totalDurationMs).isEqualTo(2_000)
        assertThat(usage[1].sessionCount).isEqualTo(1)
    }

    @Test
    fun clear_removesAllSessions() = runTest {
        dao.insert(session())
        dao.insert(session())
        dao.clear()
        assertThat(dao.observeAll().first()).isEmpty()
    }

    private fun session(
        start: Long = 0,
        end: Long = 1_000,
        swipes: Int = 1,
        app: String = "com.example",
    ) = ScrollSessionEntity(
        startTime = start,
        endTime = end,
        swipeCount = swipes,
        appPackage = app,
    )
}
