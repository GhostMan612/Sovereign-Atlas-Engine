// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.geo.DemEngine
import com.sovereignatlas.atlas.geo.ImageDecoder
import com.sovereignatlas.atlas.geo.Rgb8Image
import com.sovereignatlas.atlas.offline.DemTileStore
import com.sovereignatlas.atlas.ui.nearestPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun profileTrack(elevations: List<Double>): Track {
    val coords = elevations.mapIndexed { index, alt ->
        "[0.0,${index * 0.001},$alt]"
    }.joinToString(",")
    return Track(
        id = "profile-1",
        name = "profile",
        timestamp = 1000L,
        distance_meters = 0.0,
        geometry = """{"type":"LineString","coordinates":[$coords]}""",
    )
}

final class TrackProfileTest {
    @Test
    fun hysteresisIgnoresSubThresholdNoise() {
        val profile = runBlocking {
            TrackProfileGenerator.generate(
                profileTrack(listOf(100.0, 101.0, 102.0, 101.0, 100.0, 104.0)),
                null,
            )
        }!!
        assertEquals(4.0, profile.totalGain, 0.0)
        assertEquals(0.0, profile.totalLoss, 0.0)
        assertEquals(6, profile.points.size)
        assertEquals(100.0, profile.minElevation, 0.0)
        assertEquals(104.0, profile.maxElevation, 0.0)
        assertTrue(profile.totalDistance > 0.0)
    }

    @Test
    fun emptyGeometryReturnsNull() {
        val track = Track(
            id = "empty",
            name = "empty",
            timestamp = 0L,
            distance_meters = 0.0,
            geometry = """{"type":"LineString","coordinates":[]}""",
        )
        assertNull(runBlocking { TrackProfileGenerator.generate(track, null) })
    }

    @Test
    fun twoDimensionalFallbackUsesDemEngine() {
        val store = object : DemTileStore {
            override fun tileBytes(z: Int, x: Int, y: Int): ByteArray = byteArrayOf(1)
            override fun tileFormat(): String = "png"
        }
        val decoder = ImageDecoder { bytes ->
            Rgb8Image(2, 2, IntArray(4) { (0xFF shl 24) or (10 shl 16) })
        }
        val engine = DemEngine(store, decoder)
        val track = Track(
            id = "flat",
            name = "flat",
            timestamp = 0L,
            distance_meters = 0.0,
            geometry = """{"type":"LineString","coordinates":[[0.0,0.0],[0.001,0.0]]}""",
        )
        val profile = runBlocking { TrackProfileGenerator.generate(track, engine) }!!
        assertEquals(2, profile.points.size)
        assertEquals(profile.points[0].elevationMeters, profile.points[1].elevationMeters, 0.0)
    }

    @Test
    fun twoDimensionalWithoutEngineReturnsNull() {
        val track = Track(
            id = "flat",
            name = "flat",
            timestamp = 0L,
            distance_meters = 0.0,
            geometry = """{"type":"LineString","coordinates":[[0.0,0.0],[0.001,0.0]]}""",
        )
        assertNull(runBlocking { TrackProfileGenerator.generate(track, null) })
    }

    @Test
    fun nearestPointSnapsByDistance() {
        val profile = runBlocking {
            TrackProfileGenerator.generate(
                profileTrack(listOf(100.0, 100.0, 100.0)),
                null,
            )
        }!!
        val first = nearestPoint(0f, 300f, profile)!!
        assertEquals(0.0, first.distanceMeters, 0.0)
        val last = nearestPoint(300f, 300f, profile)!!
        assertEquals(profile.totalDistance, last.distanceMeters, 0.001)
        val mid = nearestPoint(150f, 300f, profile)!!
        assertTrue(mid.distanceMeters > 0.0 && mid.distanceMeters < profile.totalDistance)
    }
}
