// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.db.Waypoint
import com.sovereignatlas.atlas.geo.AtlasBoundingBox
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.track.trackGeometryJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private fun waypoint(id: String, latitude: Double, longitude: Double): Waypoint {
    return Waypoint(
        id = id,
        name = id,
        latitude = latitude,
        longitude = longitude,
        timestamp = 1000L,
        notes = null,
    )
}

private fun segmentTrack(): Track {
    return Track(
        id = "trk-000001",
        name = "trk-000001",
        timestamp = 1000L,
        distance_meters = 0.0,
        geometry = trackGeometryJson(
            listOf(
                AtlasCoordinate(latitude = 10.0, longitude = 20.0),
                AtlasCoordinate(latitude = 11.0, longitude = 21.0),
            ),
        ),
    )
}

final class AtlasMapLayersTest {
    @Test
    fun waypointsBecomePointsWithIdentity() {
        val collection = waypointsToFeatures(
            listOf(waypoint("wp-000001", 45.0, -93.0), waypoint("wp-000002", 46.0, -94.0)),
        )
        assertEquals(2, collection.features()?.size)
        val first = collection.features()?.get(0)?.geometry() as Point
        assertEquals(-93.0, first.longitude(), 0.0)
        assertEquals(45.0, first.latitude(), 0.0)
        assertEquals(
            "wp-000001",
            collection.features()?.get(0)?.getStringProperty("id"),
        )
    }

    @Test
    fun trackBecomesSingleLine() {
        val collection = tracksToFeatures(listOf(segmentTrack()))
        assertEquals(1, collection.features()?.size)
        val line = collection.features()?.get(0)?.geometry() as LineString
        assertEquals(2, line.coordinates().size)
    }

    @Test
    fun lineStringFromJsonParsesValidGeometry() {
        val line = LineString.fromJson(
            """{"type":"LineString","coordinates":[[20.0,10.0],[21.0,11.0]]}""",
        )
        assertEquals(2, line.coordinates().size)
        assertEquals(20.0, line.coordinates()[0].longitude(), 0.0)
        assertEquals(10.0, line.coordinates()[0].latitude(), 0.0)
    }

    @Test
    fun measureBecomesTwoPointLine() {
        val collection = measureToFeatures(
            AtlasCoordinate(latitude = 0.0, longitude = 0.0),
            AtlasCoordinate(latitude = 0.0, longitude = 1.0),
        )
        assertEquals(1, collection.features()?.size)
        val line = collection.features()?.get(0)?.geometry() as LineString
        assertEquals(2, line.coordinates().size)
    }

    @Test
    fun graticuleUnitBoxYieldsEightLines() {
        val collection = graticuleToFeatures(
            AtlasBoundingBox(south = 0.0, west = 0.0, north = 3.0, east = 3.0),
            1.0,
        )
        assertEquals(8, collection.features()?.size)
    }

    @Test
    fun ringsNullYieldsEmpty() {
        assertTrue(ringsToFeatures(null, 3).features()?.isEmpty() == true)
    }

    @Test
    fun ringsValidYieldsEightLines() {
        val collection = ringsToFeatures(
            AtlasCoordinate(latitude = 44.9778, longitude = -93.265),
            3,
        )
        assertEquals(8, collection.features()?.size)
    }

    @Test
    fun positionYieldsPoint() {
        val feature = positionToFeature(
            AtlasCoordinate(latitude = 10.0, longitude = 20.0),
        )
        val point = feature.geometry() as Point
        assertEquals(20.0, point.longitude(), 0.0)
        assertEquals(10.0, point.latitude(), 0.0)
    }
}
