// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.navigation

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSnapperTest {

    private fun createTestRoute(): RoadRouteResult {
        val coords = listOf(
            AtlasCoordinate(45.0, -93.0),
            AtlasCoordinate(45.0, -92.99),
            AtlasCoordinate(45.01, -92.99),
        )
        return RoadRouteResult(
            startNodeId = 1L,
            targetNodeId = 3L,
            totalDistanceMeters = 1890.0,
            estimatedDurationSeconds = 120.0,
            coordinates = coords,
            edges = emptyList(),
            maneuvers = emptyList(),
            profile = TravelProfile.DRIVING,
        )
    }

    @Test
    fun pointOnRouteSnapsWithHighConfidence() {
        val route = createTestRoute()
        val snapper = RouteSnapper(route, offRouteThresholdMeters = 30.0)

        val onRoutePoint = AtlasCoordinate(45.0, -92.995)
        val snapped = snapper.snap(onRoutePoint)

        assertFalse(snapped.isOffRoute)
        assertTrue(snapped.confidence > 0.9)
        assertEquals(0, snapped.segmentIndex)
        assertTrue(snapped.distanceAlongRouteMeters > 0.0)
        assertTrue(snapped.remainingDistanceMeters < route.totalDistanceMeters)
    }

    @Test
    fun distantPointTriggersOffRoute() {
        val route = createTestRoute()
        val snapper = RouteSnapper(route, offRouteThresholdMeters = 30.0)

        val distantPoint = AtlasCoordinate(45.05, -92.95)
        val snapped = snapper.snap(distantPoint)

        assertTrue(snapped.isOffRoute)
        assertEquals(0.0, snapped.confidence, 0.001)
    }

    @Test
    fun endOfRouteShowsNearZeroRemainingDistance() {
        val route = createTestRoute()
        val snapper = RouteSnapper(route)

        val endPoint = AtlasCoordinate(45.01, -92.99)
        val snapped = snapper.snap(endPoint)

        assertFalse(snapped.isOffRoute)
        assertEquals(0.0, snapped.remainingDistanceMeters, 5.0)
    }
}
