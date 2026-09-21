// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun record(): OfflinePackRecord {
    return OfflinePackRecord(
        packId = "pack-000001",
        providerId = "osm-standard",
        providerTitle = "OSM Standard",
        zMin = 10,
        zMax = 10,
        xMin = 1,
        xMax = 1,
        yMin = 2,
        yMax = 2,
        bytesPerTile = 100,
        approvedBulk = false,
        isPrefetch = false,
        createdAtEpoch = 1_000L,
    )
}

final class OfflinePacksTest {
    @Test
    fun formatAgeShowsRawAgeNeverStale() {
        assertEquals("5s", formatAge(5L))
        assertEquals("1m", formatAge(90L))
        assertEquals("2h", formatAge(7200L))
        assertEquals("1d", formatAge(90000L))
        assertEquals("clock-skew", formatAge(-3L))
    }

    @Test
    fun formatBytesUsesBinaryUnits() {
        assertEquals("512 B", formatBytes(512L))
        assertEquals("2.0 KiB", formatBytes(2048L))
        assertEquals("2.00 MiB", formatBytes(2097152L))
    }

    @Test
    fun countTilesSaturatesWithoutEnumerating() {
        assertEquals(
            1,
            countTiles(zMin = 0, zMax = 0, xMin = 0, xMax = 0, yMin = 0, yMax = 0),
        )
        assertEquals(
            MAX_SESSION_TILES + 1,
            countTiles(
                zMin = 0,
                zMax = 19,
                xMin = 0,
                xMax = 524287,
                yMin = 0,
                yMax = 524287,
            ),
        )
    }

    @Test
    fun recordStartsPlannedWithZeroCounters() {
        val current = record()
        assertEquals(OfflinePackLifecycle.planned, current.lifecycle)
        assertFalse(current.isTerminal)
        assertEquals(0, current.receivedTiles)
        assertEquals(0L, current.receivedBytes)
        assertEquals("", current.failureDetail)
    }

    @Test
    fun terminalStatesAreCompleteFailedCancelled() {
        val current = record()
        current.lifecycle = OfflinePackLifecycle.downloading
        assertFalse(current.isTerminal)
        current.lifecycle = OfflinePackLifecycle.complete
        assertTrue(current.isTerminal)
        current.lifecycle = OfflinePackLifecycle.failed
        assertTrue(current.isTerminal)
        current.lifecycle = OfflinePackLifecycle.cancelled
        assertTrue(current.isTerminal)
        current.lifecycle = OfflinePackLifecycle.paused
        assertFalse(current.isTerminal)
        current.lifecycle = OfflinePackLifecycle.quotaPaused
        assertFalse(current.isTerminal)
    }

    @Test
    fun ageSecondsIsRawDifference() {
        assertEquals(500L, record().ageSeconds(1500L))
        assertEquals(-5L, record().ageSeconds(995L))
    }

    @Test
    fun tileCountAndEstimateComeFromPersistedFields() {
        val current = record()
        current.persistedTileCount = 7
        current.persistedEstimatedBytes = 700L
        assertEquals(7, current.tileCount)
        assertEquals(700L, current.estimatedBytes)
    }
}
