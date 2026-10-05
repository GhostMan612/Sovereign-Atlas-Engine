// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import com.sovereignatlas.atlas.core.AtlasBoundingBox
import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.db.Waypoint
import com.sovereignatlas.atlas.field.WaypointSharingPolicy
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.render.RenderFeatureCollection
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import com.sovereignatlas.atlas.track.trackGeometryJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun waypoint(id: String, latitude: Double, longitude: Double): Waypoint =
    Waypoint(
        id = id,
        name = id,
        latitude = latitude,
        longitude = longitude,
        timestamp = 1000L,
        notes = null,
        sharingPolicy = WaypointSharingPolicy.Private.storedValue,
    )

private fun segmentTrack(): Track = Track(
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

/**
 * The mappers, asserted against the PURE types they now emit.
 *
 * These assertions used to read `collection.features()[0].geometry() as Point` and
 * `getStringProperty("id")` against MapLibre's own classes. That is what made these
 * tests worthless as a check on the seam: they asserted that a MapLibre object was
 * built correctly, which is the renderer's job and its own guarantee. They now assert
 * that a pure [RenderFeature] carries the right shape, the right id, and the right
 * properties — which is the part that could actually be wrong.
 *
 * The one test that still touches MapLibre is deliberately absent: the conversion
 * itself needs a live `Style`, so it is not JVM-testable, and pretending otherwise
 * would mean testing my own mock.
 */
final class AtlasMapLayersTest {

    @Test
    fun waypointsBecomePointsWithIdentity() {
        val collection = waypointsToFeatures(
            listOf(waypoint("wp-000001", 45.0, -93.0), waypoint("wp-000002", 46.0, -94.0)),
        )

        assertEquals(2, collection.size)
        val first = collection.features[0]
        val point = first.geometry as RenderGeometry.Point
        assertEquals(-93.0, point.coordinates.longitude, 0.0)
        assertEquals(45.0, point.coordinates.latitude, 0.0)
    }

    @Test
    fun theWaypointIdIsAFirstClassFieldNotAProperty() {
        // Hit-testing recovers an id to act on. Reading it back out of a property bag
        // is how a mapper that forgets the property produces an unpickable feature,
        // so id is a constructor parameter and cannot be left unset.
        val collection = waypointsToFeatures(listOf(waypoint("wp-000001", 45.0, -93.0)))

        assertEquals("wp-000001", collection.features[0].id)
    }

    @Test
    fun aWaypointCarriesItsSharingPolicySoExposureIsReadableFromTheStyle() {
        // A plugin reading features back out of the layer has no database. Without
        // this, an exposed waypoint and a local one are indistinguishable to it.
        val shared = waypoint("wp-shared", 45.0, -93.0).copy(
            sharingPolicy = WaypointSharingPolicy.Team.storedValue,
        )

        val feature = waypointsToFeatures(listOf(shared)).features.single()

        assertEquals(
            RenderProperty.Text(WaypointSharingPolicy.Team.storedValue),
            feature.properties["sharingPolicy"],
        )
    }

    @Test
    fun anEmptyWaypointNameFallsBackToTheIdAsLabel() {
        // The style renders {label}. A waypoint with a blank name would draw an
        // invisible symbol that still occupied the layer.
        val nameless = waypoint("wp-fallback", 45.0, -93.0).copy(name = "")

        val feature = waypointsToFeatures(listOf(nameless)).features.single()

        assertEquals(
            RenderProperty.Text("wp-fallback"),
            feature.properties["label"],
        )
    }

    @Test
    fun trackBecomesSingleLine() {
        val collection = tracksToFeatures(listOf(segmentTrack()))

        assertEquals(1, collection.size)
        val line = collection.features[0].geometry as RenderGeometry.LineString
        assertEquals(2, line.coordinates.size)
        assertEquals("trk-000001", collection.features[0].id)
    }

    @Test
    fun aCorruptTrackIsSkippedRatherThanThrowing() {
        // A track whose geometry will not parse must not take down the whole layer's
        // push. This is the null-return path the removed Log.w used to report.
        val corrupt = segmentTrack().copy(geometry = "not json")

        val collection = tracksToFeatures(listOf(corrupt))

        assertTrue(collection.isEmpty)
    }

    @Test
    fun measureBecomesTwoPointLine() {
        val collection = measureToFeatures(
            AtlasCoordinate(latitude = 0.0, longitude = 0.0),
            AtlasCoordinate(latitude = 0.0, longitude = 1.0),
        )

        assertEquals(1, collection.size)
        val line = collection.features[0].geometry as RenderGeometry.LineString
        assertEquals(2, line.coordinates.size)
    }

    @Test
    fun graticuleUnitBoxYieldsEightLines() {
        val collection = graticuleToFeatures(
            AtlasBoundingBox(south = 0.0, west = 0.0, north = 3.0, east = 3.0),
            1.0,
        )

        assertEquals(8, collection.size)
    }

    @Test
    fun ringsNullYieldsEmpty() {
        assertTrue(ringsToFeatures(null, 3).isEmpty)
    }

    @Test
    fun ringsValidYieldsEightLines() {
        val collection = ringsToFeatures(
            AtlasCoordinate(latitude = 44.9778, longitude = -93.265),
            3,
        )

        assertEquals(8, collection.size)
    }

    @Test
    fun positionYieldsPoint() {
        val feature = positionToFeature(AtlasCoordinate(latitude = 10.0, longitude = 20.0))
        val point = feature.geometry as RenderGeometry.Point

        assertEquals(20.0, point.coordinates.longitude, 0.0)
        assertEquals(10.0, point.coordinates.latitude, 0.0)
    }

    @Test
    fun positionCarriesTheBearingAsANumberProperty() {
        // The style reads `bearing` and rotates the icon by it, so this must be a
        // number rather than a string. The RenderProperty union is what makes that
        // checkable; the old JsonObject path would have accepted either.
        val feature = positionToFeature(
            AtlasCoordinate(latitude = 10.0, longitude = 20.0),
            bearingDeg = 137.5,
        )

        assertEquals(RenderProperty.Number(137.5), feature.properties["bearing"])
    }

    @Test
    fun anAbsentBearingBecomesZeroRatherThanNull() {
        val feature = positionToFeature(AtlasCoordinate(latitude = 10.0, longitude = 20.0))

        assertEquals(RenderProperty.Number(0.0), feature.properties["bearing"])
    }

    @Test
    fun goToCarriesItsLabel() {
        val feature = goToToFeature(45.0, -93.0, "Bridge")

        assertEquals(RenderProperty.Text("Bridge"), feature.properties["label"])
    }

    @Test
    fun everyEmittedFeatureHasANonBlankId() {
        // Hit-testing acts on the id. A blank id produces a feature the app cannot
        // select, and it fails silently at the point of use.
        val fromWaypoints = waypointsToFeatures(
            listOf(waypoint("wp-a", 45.0, -93.0), waypoint("wp-b", 46.0, -94.0)),
        ).features
        val fromTracks = tracksToFeatures(listOf(segmentTrack())).features

        for (feature in fromWaypoints + fromTracks) {
            assertTrue("feature id was blank", feature.id.isNotBlank())
        }
    }

    @Test
    fun mgrsLinesCarryTextAsAStringProperty() {
        // The style reads `title` through an expression. A Number here would make the
        // label render as nothing at all rather than as an error.
        val labels = com.sovereignatlas.atlas.geo.MgrsGridLabel(
            text = "15T",
            latitude = 45.0,
            longitude = -93.0,
        )

        val feature = mgrsLabelsToFeatures(listOf(labels)).features.single()

        assertEquals(RenderProperty.Text("15T"), feature.properties["title"])
    }

    @Test
    fun anEmptyCollectionIsAvailableRatherThanConstructed() {
        // Several call sites clear a layer by pushing empty. A shared EMPTY keeps
        // that a single identity rather than a fresh allocation at each site.
        assertTrue(RenderFeatureCollection.EMPTY.isEmpty)
        assertEquals(0, RenderFeatureCollection.EMPTY.size)
        assertNull(RenderFeatureCollection.EMPTY.features.firstOrNull())
    }
}
