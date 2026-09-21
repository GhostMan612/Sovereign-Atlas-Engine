// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.field.StoredTrack
import com.sovereignatlas.atlas.field.StoredWaypoint
import com.sovereignatlas.atlas.field.WaypointSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun waypoint(): StoredWaypoint {
    return StoredWaypoint(
        id = "wp-000001",
        latitude = 45.0,
        longitude = -93.0,
        createdAt = 0L,
        label = "A&B",
        note = "x<y>",
        source = WaypointSource.mapSelected,
    )
}

final class GpxExportTest {
    @Test
    fun escapesXml() {
        assertEquals("A&amp;B&lt;c&gt;", escapeXml("A&B<c>"))
    }

    @Test
    fun timeIsUtcIso() {
        assertEquals("1970-01-01T00:00:00Z", formatGpxTime(0L))
    }

    @Test
    fun waypointsGpxHasEscapedFields() {
        val text = waypointsToGpx(listOf(waypoint()))
        assertTrue(text.contains("<gpx version=\"1.1\""))
        assertTrue(text.contains("<wpt lat=\"45.0\" lon=\"-93.0\">"))
        assertTrue(text.contains("<name>A&amp;B</name>"))
        assertTrue(text.contains("<desc>x&lt;y&gt;</desc>"))
        assertTrue(text.contains("<time>1970-01-01T00:00:00Z</time>"))
    }

    @Test
    fun unlabeledWaypointUsesId() {
        val text = waypointsToGpx(listOf(waypoint().copy(label = "", note = "")))
        assertTrue(text.contains("<name>wp-000001</name>"))
        assertTrue(!text.contains("<desc>"))
    }

    @Test
    fun trackGpxHasSegmentPoints() {
        val text = trackToGpx(
            StoredTrack(
                id = "trk-000001",
                createdAt = 0L,
                points = listOf(
                    waypoint().copy(id = "trk-000001-p0001"),
                    waypoint().copy(id = "trk-000001-p0002", latitude = 46.0),
                ),
                source = WaypointSource.gpsRecorded,
            ),
        )
        assertTrue(text.contains("<trk>"))
        assertTrue(text.contains("<name>trk-000001</name>"))
        assertTrue(text.contains("<trkpt lat=\"45.0\" lon=\"-93.0\">"))
        assertTrue(text.contains("<trkpt lat=\"46.0\" lon=\"-93.0\">"))
    }
}
