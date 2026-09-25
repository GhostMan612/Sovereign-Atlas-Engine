// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.db.Waypoint
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

final class WaypointRepositoryTest {
    private fun database(): AtlasDatabase {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AtlasDatabase.Schema.create(driver)
        return AtlasDatabase(driver)
    }

    private fun waypoint(id: String, timestamp: Long): Waypoint {
        return Waypoint(
            id = id,
            name = "wp-$id",
            latitude = 44.9,
            longitude = -93.1,
            timestamp = timestamp,
            notes = null,
        )
    }

    @Test
    fun roundTripInsertAndDelete() {
        runBlocking {
            val repository = WaypointRepository(database())
            val id = UUID.randomUUID().toString()
            repository.saveWaypoint(waypoint(id, 1000L))
            val stored = withTimeout(10_000L) {
                repository.waypoints.first { it.any { row -> row.id == id } }
            }
            assertEquals("wp-$id", stored.first { it.id == id }.name)
            repository.deleteWaypoint(id)
            val remaining = withTimeout(10_000L) {
                repository.waypoints.first { it.none { row -> row.id == id } }
            }
            assertEquals(0, remaining.count { it.id == id })
        }
    }

    @Test
    fun selectOrdersByTimestampDescending() {
        runBlocking {
            val repository = WaypointRepository(database())
            repository.saveWaypoint(waypoint("older", 1000L))
            repository.saveWaypoint(waypoint("newer", 2000L))
            val rows = withTimeout(10_000L) {
                repository.waypoints.first { it.size >= 2 }
            }
            val order = rows.map { it.id }
            assertEquals("newer", order.first())
        }
    }
}
