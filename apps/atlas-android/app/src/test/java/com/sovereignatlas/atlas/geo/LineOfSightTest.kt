// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.offline.DemTileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ridgeStore(ridgeX: Int, ridgeR: Int, baseR: Int): DemTileStore {
    return object : DemTileStore {
        override fun tileBytes(z: Int, x: Int, y: Int): ByteArray {
            val red = if (x == ridgeX) ridgeR else baseR
            return byteArrayOf(red.toByte())
        }

        override fun tileFormat(): String = "png"
    }
}

private fun redDecoder(): ImageDecoder {
    return ImageDecoder { bytes ->
        val red = bytes[0].toInt() and 0xFF
        Rgb8Image(4, 4, IntArray(16) { (0xFF shl 24) or (red shl 16) })
    }
}

private fun engineFor(ridgeX: Int, ridgeR: Int, baseR: Int): DemEngine {
    return DemEngine(ridgeStore(ridgeX, ridgeR, baseR), redDecoder())
}

private fun point(latitude: Double, longitude: Double): GeoPoint {
    return GeoPoint(latitude, longitude, null, null, null, 0L)
}

final class LineOfSightTest {
    @Test
    fun haversineEquatorialDegree() {
        val meters = LineOfSightEngine.haversine(0.0, 0.0, 0.0, 1.0)
        assertTrue(meters > 111000.0 && meters < 111400.0)
    }

    @Test
    fun slerpEndpointsAreExact() {
        val (lat0, lng0) = LineOfSightEngine.interpolateGreatCircle(10.0, 20.0, 11.0, 21.0, 0.0, 150000.0)
        assertEquals(10.0, lat0, 1e-9)
        assertEquals(20.0, lng0, 1e-9)
        val (lat1, lng1) = LineOfSightEngine.interpolateGreatCircle(10.0, 20.0, 11.0, 21.0, 1.0, 150000.0)
        assertEquals(11.0, lat1, 1e-6)
        assertEquals(21.0, lng1, 1e-6)
    }

    @Test
    fun flatTerrainReportsClear() {
        val engine = engineFor(ridgeX = -1, ridgeR = 0, baseR = 10)
        val result = runBlocking {
            LineOfSightEngine.calculate(
                LoSRequest(point(0.0, 0.0), point(0.0, 0.05)),
                engine,
            )
        }
        assertTrue(result.isVisible)
        assertNull(result.blockingPoint)
        assertNull(result.errorMessage)
        assertTrue(result.profile.isNotEmpty())
    }

    @Test
    fun ridgeReportsObstructedWithMidpointBlock() {
        // Equator z14 tiles are ~2445m wide; observer x=8192, ridge x=8193.
        val engine = engineFor(ridgeX = 8193, ridgeR = 11, baseR = 10)
        val result = runBlocking {
            LineOfSightEngine.calculate(
                LoSRequest(point(0.0, 0.0), point(0.0, 0.05)),
                engine,
            )
        }
        assertTrue(!result.isVisible)
        assertNotNull(result.blockingPoint)
        assertNotNull(result.blockingDistanceMeters)
        // Flat-topped ridge: obstruction grows with the earth-bulge parabola,
        // so maximum obstruction sits at the chord midpoint (D/2), not at the
        // ridge entry. This is the specified maximum-obstruction semantic.
        assertTrue(result.blockingDistanceMeters!! > 2700.0 && result.blockingDistanceMeters!! < 2900.0)
        assertNull(result.errorMessage)
    }

    @Test
    fun missingDemAbortsWithoutZeroDefault() {
        val empty = object : DemTileStore {
            override fun tileBytes(z: Int, x: Int, y: Int): ByteArray? = null
            override fun tileFormat(): String = "png"
        }
        val engine = DemEngine(empty, redDecoder())
        val result = runBlocking {
            LineOfSightEngine.calculate(
                LoSRequest(point(0.0, 0.0), point(0.0, 0.05)),
                engine,
            )
        }
        assertTrue(!result.isVisible)
        assertEquals("Missing DEM data at Observer or Target.", result.errorMessage)
        assertTrue(result.profile.isEmpty())
        assertNull(result.blockingPoint)
    }

    @Test
    fun nullEngineReportsDemUnavailable() {
        val result = runBlocking {
            LineOfSightEngine.calculate(
                LoSRequest(point(0.0, 0.0), point(0.0, 0.05)),
                null,
            )
        }
        assertTrue(!result.isVisible)
        assertEquals("DEM unavailable.", result.errorMessage)
    }
}
