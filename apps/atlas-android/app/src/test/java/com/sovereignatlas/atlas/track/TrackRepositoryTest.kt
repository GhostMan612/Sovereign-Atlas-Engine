// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.db.Track
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class TrackRepositoryTest {
    private fun database(): AtlasDatabase {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AtlasDatabase.Schema.create(driver)
        return AtlasDatabase(driver)
    }

    private fun track(id: String, timestamp: Long): Track {
        return Track(
            id = id,
            name = "track-$id",
            timestamp = timestamp,
            distance_meters = 1234.5,
            geometry = """{"type":"LineString","coordinates":[[-93.1,44.9],[-93.0,45.0]]}""",
        )
    }

    @Test
    fun roundTripInsertAndDelete() {
        runBlocking {
            val repository = TrackRepository(database())
            val id = UUID.randomUUID().toString()
            repository.saveTrack(track(id, 1000L))
            val stored = withTimeout(10_000L) {
                repository.tracks.first { it.any { row -> row.id == id } }
            }
            assertEquals(1234.5, stored.first { it.id == id }.distance_meters, 0.0)
            repository.deleteTrack(id)
            val remaining = withTimeout(10_000L) {
                repository.tracks.first { it.none { row -> row.id == id } }
            }
            assertEquals(0, remaining.count { it.id == id })
        }
    }

    @Test
    fun selectOrdersByTimestampDescending() {
        runBlocking {
            val repository = TrackRepository(database())
            repository.saveTrack(track("older", 1000L))
            repository.saveTrack(track("newer", 2000L))
            val rows = withTimeout(10_000L) {
                repository.tracks.first { it.size >= 2 }
            }
            assertEquals("newer", rows.first().id)
        }
    }

    @Test
    fun tracksFlowEmitsAfterSave() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AtlasDatabase.Schema.create(driver)
        val testTrack = track(UUID.randomUUID().toString(), 1000L)
        val repo = TrackRepository(AtlasDatabase(driver), UnconfinedTestDispatcher())

        val emissions = mutableListOf<List<Track>>()
        val job = launch { repo.tracks.toList(emissions) }
        runCurrent()

        repo.saveTrack(testTrack)
        runCurrent()

        assertTrue(emissions.last().any { it.id == testTrack.id })
        job.cancel()
    }

    @Test
    fun bufferRoundTripPreservesSequenceOrder() {
        runBlocking {
            val repository = TrackRepository(database())
            repository.insertBufferedPoint("sess-1", 1L, 45.0, -93.0, null, 1000L)
            repository.insertBufferedPoint("sess-1", 0L, 44.0, -94.0, 250.0, 900L)
            repository.insertBufferedPoint("sess-2", 0L, 0.0, 0.0, null, 1000L)
            val points = repository.bufferedPoints("sess-1")
            assertEquals(2, points.size)
            assertEquals(0L, points[0].sequence)
            assertEquals(250.0, points[0].altitude!!, 0.0)
            assertEquals(1L, points[1].sequence)
        }
    }

    @Test
    fun bufferReplaceSurvivesSequenceRestart() {
        runBlocking {
            val repository = TrackRepository(database())
            repository.insertBufferedPoint("sess-1", 0L, 44.0, -94.0, null, 900L)
            repository.insertBufferedPoint("sess-1", 0L, 45.0, -93.0, null, 1000L)
            val points = repository.bufferedPoints("sess-1")
            assertEquals(1, points.size)
            assertEquals(45.0, points[0].latitude, 0.0)
        }
    }

    @Test
    fun bufferClearRemovesSession() {
        runBlocking {
            val repository = TrackRepository(database())
            repository.insertBufferedPoint("sess-1", 0L, 45.0, -93.0, null, 1000L)
            repository.clearBufferedPoints("sess-1")
            assertTrue(repository.bufferedPoints("sess-1").isEmpty())
        }
    }
}
