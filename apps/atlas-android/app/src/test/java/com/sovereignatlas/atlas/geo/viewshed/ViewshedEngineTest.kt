// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.viewshed

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.los.ElevationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewshedEngineTest {

    private val origin = AtlasCoordinate(37.7749, -122.4194)

    private class FlatTerrainProvider(private val height: Double = 50.0) : ElevationProvider {
        override suspend fun getElevations(points: List<Pair<Double, Double>>): List<Double?> =
            List(points.size) { height }
    }

    private class RidgeProvider(
        private val observerCoord: AtlasCoordinate,
        private val baseHeight: Double = 50.0,
        private val ridgeDistMin: Double = 1800.0,
        private val ridgeDistMax: Double = 2200.0,
        private val ridgeHeight: Double = 500.0,
    ) : ElevationProvider {
        override suspend fun getElevations(points: List<Pair<Double, Double>>): List<Double?> {
            return points.map { (lat, lon) ->
                val dist = AtlasGeoMath.haversine(
                    observerCoord.latitude,
                    observerCoord.longitude,
                    lat,
                    lon,
                )
                if (dist in ridgeDistMin..ridgeDistMax) {
                    ridgeHeight
                } else {
                    baseHeight
                }
            }
        }
    }

    @Test
    fun testShortRangeFlatTerrainIsVisible() = runTest {
        val engine = ViewshedEngine(FlatTerrainProvider(100.0))
        val config = ViewshedConfig(
            observer = origin,
            observerHeightAglMeters = 2.0,
            maxRadiusMeters = 1000.0,
            rayStepMeters = 100.0,
            angularStepDeg = 45.0,
        )

        val result = engine.computeViewshed(config)
        assertEquals(8, result.rays.size)
        assertEquals(8, result.visiblePerimeter.size)
        assertEquals(8, result.maxRangeCircle.size)

        for (ray in result.rays) {
            assertFalse(ray.isObscuredAtMaxRange)
            assertEquals(1000.0, ray.maxVisibleDistanceMeters, 0.001)
            assertTrue(ray.samples.all { it.isVisible })
        }

        val poly = result.toVisiblePolygon()
        assertNotNull(poly)
        assertTrue(poly.coordinates.isNotEmpty())
        assertTrue(poly.coordinates.first().size >= 4)
    }

    @Test
    fun testCurvatureObscuresLongRangeFlatTerrain() = runTest {
        val engine = ViewshedEngine(FlatTerrainProvider(0.0))
        val config = ViewshedConfig(
            observer = origin,
            observerHeightAglMeters = 2.0,
            maxRadiusMeters = 15000.0,
            rayStepMeters = 500.0,
            angularStepDeg = 90.0,
        )

        val result = engine.computeViewshed(config)
        assertEquals(4, result.rays.size)

        for (ray in result.rays) {
            assertTrue(ray.isObscuredAtMaxRange)
            assertTrue(ray.maxVisibleDistanceMeters in 5000.0..7000.0)
        }
    }

    @Test
    fun testRidgeBlocksVisibilityBehindIt() = runTest {
        val engine = ViewshedEngine(
            RidgeProvider(
                observerCoord = origin,
                baseHeight = 100.0,
                ridgeDistMin = 1800.0,
                ridgeDistMax = 2200.0,
                ridgeHeight = 600.0,
            ),
        )
        val config = ViewshedConfig(
            observer = origin,
            observerHeightAglMeters = 2.0,
            maxRadiusMeters = 5000.0,
            rayStepMeters = 200.0,
            angularStepDeg = 60.0,
        )

        val result = engine.computeViewshed(config)
        assertEquals(6, result.rays.size)

        for (ray in result.rays) {
            assertTrue(ray.isObscuredAtMaxRange)
            assertTrue(ray.maxVisibleDistanceMeters <= 2200.0)
        }

        val boundaryLine = result.toBoundaryLine()
        assertNotNull(boundaryLine)
        assertTrue(boundaryLine.coordinates.size >= 2)

        val obscuredPoly = result.toObscuredPolygon()
        assertNotNull(obscuredPoly)
        assertTrue(obscuredPoly.coordinates.isNotEmpty())
    }

    @Test
    fun testDegenerateGeometryReturnsEmptySafely() = runTest {
        val engine = ViewshedEngine(FlatTerrainProvider(0.0))
        val zeroRadiusConfig = ViewshedConfig(
            observer = origin,
            maxRadiusMeters = 0.0,
        )
        val result = engine.computeViewshed(zeroRadiusConfig)
        assertTrue(result.rays.isEmpty())
        assertTrue(result.visiblePerimeter.isEmpty())
    }
}
