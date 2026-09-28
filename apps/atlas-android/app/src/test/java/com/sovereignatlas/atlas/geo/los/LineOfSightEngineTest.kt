// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.los

import com.sovereignatlas.atlas.geo.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeElevation(private val fn: (Double, Double) -> Double?) : ElevationProvider {
    override suspend fun getElevations(points: List<Pair<Double, Double>>): List<Double?> =
        points.map { (latitude, longitude) -> fn(latitude, longitude) }
}

private class FakeBatch(private val fn: (Int, Double, Double) -> Double?) : ElevationProvider {
    override suspend fun getElevations(points: List<Pair<Double, Double>>): List<Double?> =
        points.mapIndexed { index, (latitude, longitude) -> fn(index, latitude, longitude) }
}

private fun point(latitude: Double, longitude: Double, altitude: Double? = null): GeoPoint {
    return GeoPoint(latitude, longitude, altitude, null, null, 1000L)
}

final class LineOfSightEngineTest {
    @Test
    fun flatTerrainHasLineOfSight() = runBlocking {
        val engine = LineOfSightEngine(FakeElevation { _, _ -> 100.0 })
        val profile = engine.calculateProfile(point(44.9, -93.1), point(44.91, -93.09))
        assertNull(profile.errorMessage)
        assertTrue(profile.hasLineOfSight)
        assertEquals(101, profile.points.size)
        assertEquals(102.0, profile.observerElevationMeters, 0.0)
        assertEquals(102.0, profile.targetElevationMeters, 0.0)
        assertTrue(profile.points.all { it.isVisible })
    }

    @Test
    fun coincidentPointsReportError() = runBlocking {
        val engine = LineOfSightEngine(FakeElevation { _, _ -> 100.0 })
        val profile = engine.calculateProfile(point(44.9, -93.1), point(44.9, -93.1))
        assertFalse(profile.hasLineOfSight)
        assertTrue(profile.points.isEmpty())
        assertEquals("Observer and target are at the same location", profile.errorMessage)
    }

    @Test
    fun missingEndpointDemReportsError() = runBlocking {
        val engine = LineOfSightEngine(FakeElevation { _, _ -> null })
        val profile = engine.calculateProfile(point(44.9, -93.1), point(44.91, -93.09))
        assertFalse(profile.hasLineOfSight)
        assertEquals("Missing DEM data at endpoints", profile.errorMessage)
    }

    @Test
    fun endpointAltitudeFallbackUsedWhenDemMissing() = runBlocking {
        val engine = LineOfSightEngine(FakeBatch { index, _, _ ->
            if (index == 0 || index == 100) null else 100.0
        })
        val profile = engine.calculateProfile(
            point(44.9, -93.1, altitude = 120.0),
            point(44.91, -93.09, altitude = 120.0),
        )
        assertNull(profile.errorMessage)
        assertTrue(profile.hasLineOfSight)
        assertEquals(122.0, profile.observerElevationMeters, 0.0)
        assertEquals(122.0, profile.targetElevationMeters, 0.0)
    }

    @Test
    fun ridgeBlocksLineOfSight() = runBlocking {
        val engine = LineOfSightEngine(FakeElevation { latitude, _ ->
            if (latitude > 44.903 && latitude < 44.907) 500.0 else 100.0
        })
        val profile = engine.calculateProfile(point(44.9, -93.1), point(44.91, -93.09))
        assertNull(profile.errorMessage)
        assertFalse(profile.hasLineOfSight)
        assertTrue(profile.points.any { !it.isVisible })
    }

    @Test
    fun missingMidPathDemReportsError() = runBlocking {
        val engine = LineOfSightEngine(FakeBatch { index, _, _ ->
            if (index == 0 || index == 100) 100.0 else null
        })
        val profile = engine.calculateProfile(point(44.9, -93.1), point(44.91, -93.09))
        assertFalse(profile.hasLineOfSight)
        assertEquals("Missing DEM data along path", profile.errorMessage)
    }

    @Test
    fun batchSizeMismatchReportsError() = runBlocking {
        val engine = LineOfSightEngine(object : ElevationProvider {
            override suspend fun getElevations(points: List<Pair<Double, Double>>): List<Double?> =
                listOf(100.0)
        })
        val profile = engine.calculateProfile(point(44.9, -93.1), point(44.91, -93.09))
        assertFalse(profile.hasLineOfSight)
        assertEquals("Elevation batch size mismatch", profile.errorMessage)
    }
}
