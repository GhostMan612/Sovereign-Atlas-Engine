// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.db.Waypoint
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun waypoint(): Waypoint {
    return Waypoint(
        id = "wp-000001",
        name = "A&B",
        latitude = 45.0,
        longitude = -93.0,
        timestamp = 0L,
        notes = "x<y>",
    )
}

private fun segmentTrack(): Track {
    return Track(
        id = "trk-000001",
        name = "",
        timestamp = 0L,
        distance_meters = 0.0,
        geometry = trackGeometryJson(
            listOf(
                AtlasCoordinate(latitude = 45.0, longitude = -93.0),
                AtlasCoordinate(latitude = 46.0, longitude = -93.0),
            ),
        ),
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
        val text = waypointsToGpx(listOf(waypoint().copy(name = "", notes = "")))
        assertTrue(text.contains("<name>wp-000001</name>"))
        assertTrue(!text.contains("<desc>"))
    }

    @Test
    fun trackGpxHasSegmentPoints() {
        val text = trackToGpx(segmentTrack())
        assertTrue(text.contains("<trk>"))
        assertTrue(text.contains("<name>trk-000001</name>"))
        assertTrue(text.contains("<trkpt lat=\"45.0\" lon=\"-93.0\">"))
        assertTrue(text.contains("<trkpt lat=\"46.0\" lon=\"-93.0\">"))
    }
}
