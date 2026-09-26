// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.offline.DemTileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun flatDecoder(red: Int, green: Int, blue: Int, size: Int = 4): ImageDecoder {
    val pixel = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    return ImageDecoder { Rgb8Image(size, size, IntArray(size * size) { pixel }) }
}

private fun stubStore(bytes: ByteArray = byteArrayOf(1)): DemTileStore {
    return object : DemTileStore {
        override fun tileBytes(z: Int, x: Int, y: Int): ByteArray = bytes
        override fun tileFormat(): String = "png"
    }
}

final class DemEngineTest {
    @Test
    fun flatTileReturnsMapboxFormulaValue() {
        val engine = DemEngine(stubStore(), flatDecoder(100, 150, 200))
        val elevation = engine.getElevation(0.0, 0.0)
        assertEquals(649220.0, elevation!!, 0.001)
    }

    @Test
    fun decodeElevationMatchesMapboxSpec() {
        val engine = DemEngine(stubStore(), flatDecoder(0, 0, 0))
        assertEquals(-10000.0, engine.decodeElevation(0, 0, 0), 0.0)
        assertEquals(0.0, engine.decodeElevation(1, 134, 160), 0.001)
        assertEquals(649220.0, engine.decodeElevation(100, 150, 200), 0.001)
    }

    @Test
    fun missingTileReadsNull() {
        val empty = object : DemTileStore {
            override fun tileBytes(z: Int, x: Int, y: Int): ByteArray? = null
            override fun tileFormat(): String = "png"
        }
        val engine = DemEngine(empty, flatDecoder(100, 150, 200))
        assertNull(engine.getElevation(0.0, 0.0))
    }

    @Test
    fun outOfMercatorRangeReadsNull() {
        val engine = DemEngine(stubStore(), flatDecoder(100, 150, 200))
        assertNull(engine.getElevation(86.0, 0.0))
        assertNull(engine.getElevation(-86.0, 0.0))
    }

    @Test
    fun edgeSamplingStaysInBounds() {
        // 2x2 distinct tile: corners must not index out of range.
        val pixels = intArrayOf(
            (0xFF shl 24),
            (0xFF shl 24) or (10 shl 16),
            (0xFF shl 24) or (10 shl 8),
            (0xFF shl 24) or 10,
        )
        val decoder = ImageDecoder { Rgb8Image(2, 2, pixels) }
        val engine = DemEngine(stubStore(), decoder)
        val elevation = engine.getElevation(85.0, 179.0)
        assertEquals(true, elevation != null)
    }
}
