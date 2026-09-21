// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.field.StoredTrack
import com.sovereignatlas.atlas.field.StoredWaypoint
import com.sovereignatlas.atlas.field.WaypointSource
import com.sovereignatlas.atlas.geo.AtlasBoundingBox
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private fun waypoint(id: String, latitude: Double, longitude: Double): StoredWaypoint {
    return StoredWaypoint(
        id = id,
        latitude = latitude,
        longitude = longitude,
        createdAt = 1000L,
        source = WaypointSource.mapSelected,
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
        val track = StoredTrack(
            id = "trk-000001",
            createdAt = 1000L,
            points = listOf(
                waypoint("trk-000001-p0001", 10.0, 20.0),
                waypoint("trk-000001-p0002", 11.0, 21.0),
            ),
            source = WaypointSource.gpsRecorded,
        )
        val collection = trackToFeatures(track)
        assertEquals(1, collection.features()?.size)
        val line = collection.features()?.get(0)?.geometry() as LineString
        assertEquals(2, line.coordinates().size)
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
