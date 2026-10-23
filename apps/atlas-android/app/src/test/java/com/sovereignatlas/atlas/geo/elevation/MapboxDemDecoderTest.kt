// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.elevation

import org.junit.Assert.assertEquals
import org.junit.Test

class MapboxDemDecoderTest {

    @Test
    fun testSeaLevelDecoding() {
        val (r, g, b) = MapboxDemDecoder.encodeElevationToRgb(0.0)
        val decoded = MapboxDemDecoder.decodeRgbToElevation(r, g, b)
        assertEquals(0.0, decoded, 0.1)
    }

    @Test
    fun testMinElevationFloor() {
        val decoded = MapboxDemDecoder.decodeRgbToElevation(0, 0, 0)
        assertEquals(-10000.0, decoded, 0.001)

        val (r, g, b) = MapboxDemDecoder.encodeElevationToRgb(-10000.0)
        assertEquals(0, r)
        assertEquals(0, g)
        assertEquals(0, b)
    }

    @Test
    fun testHighElevationRoundTrip() {
        val testElevations = listOf(-414.0, 0.0, 150.5, 1441.2, 4392.0, 8848.0)
        for (target in testElevations) {
            val (r, g, b) = MapboxDemDecoder.encodeElevationToRgb(target)
            val decoded = MapboxDemDecoder.decodeRgbToElevation(r, g, b)
            assertEquals(target, decoded, 0.1)
        }
    }

    @Test
    fun testPixelDecodingWithAlpha() {
        val (r, g, b) = MapboxDemDecoder.encodeElevationToRgb(1234.5)
        val pixelWithAlpha = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        val decoded = MapboxDemDecoder.decodePixel(pixelWithAlpha)
        assertEquals(1234.5, decoded, 0.1)
    }
}
