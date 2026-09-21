// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.field.StoredWaypoint
import com.sovereignatlas.atlas.field.WaypointSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun point(latitude: Double, longitude: Double, atMs: Long): StoredWaypoint {
    return StoredWaypoint(
        id = "p",
        latitude = latitude,
        longitude = longitude,
        createdAt = atMs,
        source = WaypointSource.gpsRecorded,
    )
}

final class TrackFormatTest {
    @Test
    fun durationFormatsMinutesAndHours() {
        assertEquals("00:00", formatTrackDuration(0L))
        assertEquals("01:05", formatTrackDuration(65000L))
        assertEquals("1:02:03", formatTrackDuration(3723000L))
    }

    @Test
    fun distanceUsesEngineFormat() {
        assertEquals("500 M", formatTrackDistance(500.0))
        assertEquals("2.00 KM", formatTrackDistance(2000.0))
    }

    @Test
    fun lengthSumsSegments() {
        assertEquals(0.0, trackLengthMeters(emptyList()), 0.0)
        assertEquals(0.0, trackLengthMeters(listOf(point(0.0, 0.0, 0L))), 0.0)
        val length = trackLengthMeters(
            listOf(point(0.0, 0.0, 0L), point(0.0, 1.0, 1000L)),
        )
        assertTrue(length > 110000.0 && length < 112000.0)
    }

    @Test
    fun startFormatsLocalTimestamp() {
        val text = formatTrackStart(1000L)
        assertTrue(text.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }
}
