package com.freelanzer.autoscroller.domain.usage

import com.freelanzer.autoscroller.core.time.Clock
import com.freelanzer.autoscroller.data.usage.AppUsage
import com.freelanzer.autoscroller.data.usage.ScrollSessionEntity
import com.freelanzer.autoscroller.data.usage.UsageRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

/**
 * Tests de orquestación del [SessionRecorder] con un [UsageRepository] fake y un [Clock]
 * controlable. Pura JVM: no levanta Room.
 */
class SessionRecorderTest {

    private val recorded = mutableListOf<RecordedSession>()
    private val fakeRepo = object : UsageRepository {
        override suspend fun recordSession(
            startTime: Long,
            endTime: Long,
            swipeCount: Int,
            appPackage: String,
        ) {
            recorded += RecordedSession(startTime, endTime, swipeCount, appPackage)
        }

        override fun observeSessions(): Flow<List<ScrollSessionEntity>> = emptyFlow()
        override fun observeUsageByApp(): Flow<List<AppUsage>> = emptyFlow()
        override fun observeTotalSwipes(): Flow<Int> = emptyFlow()
        override suspend fun clear() { recorded.clear() }
    }

    private var fakeNow = 0L
    private val clock = Clock { fakeNow }
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private fun recorder() = SessionRecorder(fakeRepo, clock, scope)

    @Test
    fun `start then end records one session with correct fields`() {
        val r = recorder()
        fakeNow = 1_000L
        r.onSessionStarted("com.zhiliaoapp.musically")
        fakeNow = 6_000L
        r.onSessionEnded(swipeCount = 5)

        assertThat(recorded).hasSize(1)
        val s = recorded.single()
        assertThat(s.startTime).isEqualTo(1_000L)
        assertThat(s.endTime).isEqualTo(6_000L)
        assertThat(s.swipeCount).isEqualTo(5)
        assertThat(s.appPackage).isEqualTo("com.zhiliaoapp.musically")
    }

    @Test
    fun `end without start is a no-op`() {
        val r = recorder()
        r.onSessionEnded(swipeCount = 3)
        assertThat(recorded).isEmpty()
    }

    @Test
    fun `session with zero swipes is discarded`() {
        val r = recorder()
        r.onSessionStarted("com.app")
        r.onSessionEnded(swipeCount = 0)
        assertThat(recorded).isEmpty()
    }

    @Test
    fun `double end records only once`() {
        val r = recorder()
        r.onSessionStarted("com.app")
        r.onSessionEnded(swipeCount = 4)
        r.onSessionEnded(swipeCount = 4)
        assertThat(recorded).hasSize(1)
    }

    @Test
    fun `blank package falls back to unknown`() {
        val r = recorder()
        r.onSessionStarted("")
        r.onSessionEnded(swipeCount = 2)
        assertThat(recorded.single().appPackage).isEqualTo("unknown")
    }

    private data class RecordedSession(
        val startTime: Long,
        val endTime: Long,
        val swipeCount: Int,
        val appPackage: String,
    )
}
