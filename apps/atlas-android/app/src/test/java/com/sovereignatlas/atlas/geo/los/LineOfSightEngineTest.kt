// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.los

import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.GeoPoint
import com.sovereignatlas.atlas.geo.LoSRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the single line-of-sight engine.
 *
 * This replaces two independent suites, neither of which compared its output to
 * the other's. Every branch either engine tested is here, plus the two cases that
 * divergence made untestable:
 *
 * - a ridge crossed and then cleared, where the two engines disagreed about
 *   whether the ray was blocked at all (`dipAndRecoverIsStillBlocked`)
 * - terrain missing mid-path, where one engine reported CLEAR over ground it had
 *   never measured (`aGapMidPathIsNotReportedAsClear`)
 *
 * The second is the reason this consolidation happened: a boolean result cannot
 * tell "blocked" from "unmeasured", and a skipped sample looks exactly like a
 * clear one.
 */
final class LineOfSightEngineTest {

    private fun point(lat: Double, lon: Double, altitude: Double? = null, ts: Long = 1_000L) =
        GeoPoint(lat, lon, altitude, null, null, ts)

    /** Flat terrain at a constant elevation. */
    private class FlatTerrain(private val elevation: Double) : ElevationProvider {
        override suspend fun getElevations(points: List<Pair<Double, Double>>) =
            List(points.size) { elevation }
    }

    /**
     * A ridge of [ridgeElevation] between the two given fractions of the path.
     *
     * Fractions are of the full path INCLUDING endpoints, so a band of 0.4..0.6 is
     * genuinely the middle of the ray and not an interior offset.
     */
    private class Ridge(
        private val fromFraction: Double,
        private val toFraction: Double,
        private val ridgeElevation: Double,
        private val baseElevation: Double = 100.0,
    ) : ElevationProvider {
        override suspend fun getElevations(points: List<Pair<Double, Double>>) =
            List(points.size) {
                val fraction = it.toDouble() / (points.size - 1)
                if (fraction in fromFraction..toFraction) ridgeElevation else baseElevation
            }
    }

    /**
     * A provider that returns null over one band, simulating a DEM gap.
     *
     * Fractions are of the full path INCLUDING endpoints, matching what the engine
     * now asks for, so a gap band that reaches 0.0 would swallow the observer.
     */
    private class Gap(
        private val gapFrom: Double,
        private val gapTo: Double,
        private val elevation: Double = 100.0,
    ) : ElevationProvider {
        override suspend fun getElevations(points: List<Pair<Double, Double>>) =
            List(points.size) {
                val fraction = it.toDouble() / (points.size - 1)
                if (fraction in gapFrom..gapTo) null else elevation
            }
    }

    private class ShortBatch : ElevationProvider {
        override suspend fun getElevations(points: List<Pair<Double, Double>>) =
            List(maxOf(1, points.size - 1)) { 100.0 }
    }

    /** Terrain everywhere except the two endpoints: an uncovered observer/target. */
    private class NullAtEndpoints(private val elevation: Double = 102.0) : ElevationProvider {
        override suspend fun getElevations(points: List<Pair<Double, Double>>) =
            List(points.size) {
                if (it == 0 || it == points.size - 1) null else elevation
            }
    }

    private val observer = point(44.9000, -93.1000)
    private val target = point(44.9500, -93.1000)

    @Test
    fun flatTerrainIsClear() = runTest {
        val profile = LineOfSightEngine(FlatTerrain(102.0)).calculateProfile(observer, target)

        assertEquals(LoSStatus.Clear, profile.lineOfSight.status)
        assertTrue(profile.lineOfSight.isVisible)
        assertTrue(profile.lineOfSight.isMeasured)
        assertNull(profile.lineOfSight.blockingPoint)
        assertNull(profile.errorMessage)
        assertTrue(profile.points.isNotEmpty())
        assertTrue(profile.points.all { it.isVisible })
    }

    @Test
    fun aRidgeIsReportedWithItsDistanceAndLocation() = runTest {
        // Ridge across the middle third, 300 m above both endpoints.
        val profile = LineOfSightEngine(Ridge(0.4, 0.6, ridgeElevation = 400.0))
            .calculateProfile(observer, target)

        assertEquals(LoSStatus.BlockedTerrain, profile.lineOfSight.status)
        assertFalse(profile.lineOfSight.isVisible)
        assertTrue("a blocked verdict is still a measured one", profile.lineOfSight.isMeasured)

        val distance = profile.lineOfSight.blockingDistanceMeters!!
        val total = AtlasGeoMath.haversine(44.9000, -93.1000, 44.9500, -93.1000)
        assertTrue(
            "blocking point should be at the ridge, not an endpoint (was $distance of $total)",
            distance > total * 0.3 && distance < total * 0.7,
        )
        assertNotNull(profile.lineOfSight.blockingPoint)
        assertNull("a blocked result is not an error", profile.errorMessage)
    }

    @Test
    fun dipAndRecoverIsStillBlocked() = runTest {
        // The divergence case. The terrain dips into the ray and returns above it,
        // so the final sample is clear. An engine that only tests the endpoint
        // would call this visible; the maximum-obstruction semantic must not.
        val provider = object : ElevationProvider {
            override suspend fun getElevations(points: List<Pair<Double, Double>>) =
                List(points.size) { index ->
                    val fraction = index.toDouble() / (points.size - 1)
                    // Endpoints 100 m, middle 400 m: blocked mid-path, clear after.
                    if (fraction in 0.35..0.65) 400.0 else 100.0
                }
        }

        val profile = LineOfSightEngine(provider).calculateProfile(observer, target)

        assertEquals(
            "a ray that crosses a ridge and emerges is still obstructed",
            LoSStatus.BlockedTerrain,
            profile.lineOfSight.status,
        )
        assertFalse(profile.hasLineOfSight)
        // The final interior sample itself is unobstructed, which is exactly why an
        // endpoint-only test would have missed this.
        assertTrue(profile.points.last().obstructionMeters < 0.0)
    }

    @Test
    fun aGapMidPathIsNotReportedAsClear() = runTest {
        // THE regression guard. The old object engine skipped null elevations and
        // could return isVisible=true over terrain it had never measured.
        val profile = LineOfSightEngine(Gap(gapFrom = 0.45, gapTo = 0.55))
            .calculateProfile(observer, target)

        assertEquals(LoSStatus.IncompleteTerrain, profile.lineOfSight.status)
        assertFalse(
            "unmeasured ground must never read as clear",
            profile.lineOfSight.isVisible,
        )
        assertFalse("an incomplete verdict is not a measured one", profile.lineOfSight.isMeasured)
        assertTrue(profile.points.isEmpty())
        assertNotNull(profile.errorMessage)
    }

    @Test
    fun aRidgeHiddenInsideAGapIsStillNotClear() = runTest {
        // The gap covers the midpoint. Measuring only what was available would have
        // found flat ground on both sides and reported CLEAR.
        val provider = object : ElevationProvider {
            override suspend fun getElevations(points: List<Pair<Double, Double>>) =
                List(points.size) { index ->
                    val fraction = index.toDouble() / (points.size - 1)
                    if (fraction in 0.45..0.55) null else 100.0
                }
        }

        val profile = LineOfSightEngine(provider).calculateProfile(observer, target)

        assertEquals(LoSStatus.IncompleteTerrain, profile.lineOfSight.status)
        assertFalse(profile.hasLineOfSight)
    }

    @Test
    fun anUncoveredEndpointIsNoTerrainDataNotBlocked() = runTest {
        // The boolean collapse: both used to arrive as isVisible=false. Distinct
        // statuses are the whole point of LoSStatus.
        val noData = LineOfSightEngine(NullAtEndpoints())
            .calculateProfile(observer, target)
        val blocked = LineOfSightEngine(Ridge(0.4, 0.6, 400.0))
            .calculateProfile(observer, target)

        assertEquals(LoSStatus.NoTerrainData, noData.lineOfSight.status)
        assertEquals(LoSStatus.BlockedTerrain, blocked.lineOfSight.status)
        assertFalse("no-data is not a measurement", noData.lineOfSight.isMeasured)
        assertTrue("blocked IS a measurement", blocked.lineOfSight.isMeasured)
        assertFalse(noData.lineOfSight.isVisible)
        assertFalse(blocked.lineOfSight.isVisible)
    }

    @Test
    fun anExplicitAltitudeOverridesTheSourceWithoutHidingAGap() = runTest {
        // Endpoints are supplied on the GeoPoints, so the source cannot answer for
        // them and the gap is interior-only. The result must still be
        // IncompleteTerrain: an endpoint override is not licence to ignore a hole.
        val profile = LineOfSightEngine(FlatTerrain(102.0))
            .calculateProfile(
                start = observer.copy(altitude = 500.0),
                end = target.copy(altitude = 500.0),
            )

        assertEquals(LoSStatus.Clear, profile.lineOfSight.status)
        assertEquals(
            "an explicit altitude must win over the source",
            500.0,
            profile.observerElevationMeters,
            1e-9,
        )
    }

    @Test
    fun anExplicitAltitudeDoesNotRescueAnUncoveredInterior() = runTest {
        val profile = LineOfSightEngine(object : ElevationProvider {
            override suspend fun getElevations(points: List<Pair<Double, Double>>) =
                List(points.size) { null }
        }).calculateProfile(
            start = observer.copy(altitude = 100.0),
            end = target.copy(altitude = 100.0),
        )

        assertEquals(LoSStatus.IncompleteTerrain, profile.lineOfSight.status)
        assertFalse(profile.hasLineOfSight)
    }

    @Test
    fun coincidentPointsAreDegenerateNotBlocked() = runTest {
        val profile = LineOfSightEngine(FlatTerrain(100.0)).calculateProfile(observer, observer)

        assertEquals(LoSStatus.DegenerateGeometry, profile.lineOfSight.status)
        assertFalse(profile.lineOfSight.isVisible)
    }

    @Test
    fun aProviderViolatingItsContractIsAProviderError() = runTest {
        val profile = LineOfSightEngine(ShortBatch()).calculateProfile(observer, target)

        assertEquals(LoSStatus.ProviderError, profile.lineOfSight.status)
        assertFalse(profile.lineOfSight.isMeasured)
    }

    @Test
    fun everySampleCarriesBothRayAndTerrainHeight() = runTest {
        val profile = LineOfSightEngine(FlatTerrain(102.0)).calculateProfile(observer, target)
        val total = AtlasGeoMath.haversine(44.9000, -93.1000, 44.9500, -93.1000)
        val eye = profile.observerElevationMeters + 2.0
        val targetEye = profile.targetElevationMeters + 2.0

        for (sample in profile.points) {
            val distance = sample.distanceFromStartMeters
            val expectedBulge = (distance * (total - distance)) /
                (2.0 * (6_371_000.0 * (4.0 / 3.0)))
            assertEquals(
                "corrected terrain must be terrain plus the symmetric earth bulge",
                sample.terrainElevationMeters + expectedBulge,
                sample.correctedTerrainElevationMeters,
                1e-6,
            )
            assertEquals(
                "the ray must be the straight chord between the two eyes",
                eye + (targetEye - eye) * (distance / total),
                sample.rayElevationMeters,
                1e-6,
            )
            assertEquals(
                "obstruction must equal corrected terrain minus ray",
                sample.correctedTerrainElevationMeters - sample.rayElevationMeters,
                sample.obstructionMeters,
                1e-6,
            )
        }
        // The bulge must be SYMMETRIC about the midpoint, and must shrink toward
        // both ends. The replaced engine used distance-squared, which was zero at
        // the observer and LARGEST at the target, so its correction never vanished
        // at the far end. Symmetry is the property that catches that.
        val firstBulge = profile.points.first().correctedTerrainElevationMeters - 102.0
        val lastBulge = profile.points.last().correctedTerrainElevationMeters - 102.0
        val midBulge = profile.points[profile.points.size / 2].correctedTerrainElevationMeters - 102.0

        assertEquals(
            "the earth bulge must be symmetric about the midpoint",
            firstBulge,
            lastBulge,
            1e-6,
        )
        assertTrue(
            "bulge must grow toward the midpoint (was $firstBulge then $midBulge)",
            midBulge > firstBulge,
        )
    }

    @Test
    fun samplingDensifiesShortRaysAndThinsLongOnes() = runTest {
        val near = LineOfSightEngine(FlatTerrain(100.0))
            .calculateProfile(point(44.9000, -93.1000), point(44.9010, -93.1000))
        val far = LineOfSightEngine(FlatTerrain(100.0))
            .calculateProfile(point(44.9000, -93.1000), point(44.9800, -93.1000))

        fun spacing(profile: TerrainProfile): Double {
            assertTrue("expected interior samples", profile.points.size >= 2)
            return profile.points[1].distanceFromStartMeters - profile.points[0].distanceFromStartMeters
        }

        val nearSpacing = spacing(near)
        val farSpacing = spacing(far)

        // Both are interval-quantised; the point is that spacing is bounded and
        // predictable rather than a fixed sample COUNT that drifts with distance.
        // Spacing is `total / (n + 1)`, so it can exceed the nominal interval
        // slightly when the path length is not a multiple of it - 20.03 m for a
        // path that does not divide evenly - and must never exceed that rounding.
        assertTrue("short-ray spacing should be finer or equal", nearSpacing <= farSpacing)
        assertTrue(
            "long-ray spacing must stay near the 20 m interval (was $farSpacing)",
            farSpacing <= 20.5,
        )
    }

    @Test
    fun messageForEveryStatusIsEitherNullOrActionable() {
        assertNull(messageFor(LoSStatus.Clear))
        assertNull(messageFor(LoSStatus.BlockedTerrain))
        for (status in listOf(
            LoSStatus.NoTerrainData,
            LoSStatus.IncompleteTerrain,
            LoSStatus.DegenerateGeometry,
            LoSStatus.ProviderError,
        )) {
            assertNotNull("status $status must carry a message", messageFor(status))
        }
    }

}