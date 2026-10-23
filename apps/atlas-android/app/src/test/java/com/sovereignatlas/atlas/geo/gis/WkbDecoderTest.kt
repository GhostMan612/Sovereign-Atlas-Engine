// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.geo.render.RenderGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WkbDecoderTest {

    @Test
    fun detectsGeoPackageMagicBytes() {
        val validGpkg = byteArrayOf(0x47, 0x50, 0x00, 0x01, 0x00, 0x00, 0x10, 0xE6.toByte())
        assertTrue(WkbDecoder.isGeoPackageBinary(validGpkg))

        val invalid = byteArrayOf(0x01, 0x01, 0x00, 0x00, 0x00)
        assertFalse(WkbDecoder.isGeoPackageBinary(invalid))

        val tooShort = byteArrayOf(0x47, 0x50)
        assertFalse(WkbDecoder.isGeoPackageBinary(tooShort))
    }

    @Test
    fun extractsGpkgSrsId() {
        val buffer = ByteBuffer.allocate(8)
        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x01.toByte()) // Little Endian
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(4326)

        val srsId = WkbDecoder.extractGpkgSrsId(buffer.array())
        assertEquals(4326, srsId)
    }

    @Test
    fun decodesBigEndianPoint() {
        val buffer = ByteBuffer.allocate(21)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(0x00.toByte())
        buffer.putInt(1)
        buffer.putDouble(-105.0)
        buffer.putDouble(40.0)

        val geom = WkbDecoder.decode(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is RenderGeometry.Point)
        val pt = geom as RenderGeometry.Point
        assertEquals(-105.0, pt.coordinates.longitude, 1e-6)
        assertEquals(40.0, pt.coordinates.latitude, 1e-6)
    }

    @Test
    fun decodesLittleEndianPoint() {
        val buffer = ByteBuffer.allocate(21)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x01.toByte())
        buffer.putInt(1)
        buffer.putDouble(-122.4194)
        buffer.putDouble(37.7749)

        val geom = WkbDecoder.decode(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is RenderGeometry.Point)
        val pt = geom as RenderGeometry.Point
        assertEquals(-122.4194, pt.coordinates.longitude, 1e-6)
        assertEquals(37.7749, pt.coordinates.latitude, 1e-6)
    }

    @Test
    fun decodesIsoPointZ() {
        val buffer = ByteBuffer.allocate(29)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(0x00.toByte())
        buffer.putInt(1001)
        buffer.putDouble(-105.0)
        buffer.putDouble(40.0)
        buffer.putDouble(1600.0)

        val geom = WkbDecoder.decode(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is RenderGeometry.Point)
        val pt = geom as RenderGeometry.Point
        assertEquals(-105.0, pt.coordinates.longitude, 1e-6)
        assertEquals(40.0, pt.coordinates.latitude, 1e-6)
    }

    @Test
    fun decodesLineString() {
        val buffer = ByteBuffer.allocate(1 + 4 + 4 + 16 * 2)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(0x00.toByte())
        buffer.putInt(2)
        buffer.putInt(2)
        buffer.putDouble(-105.0)
        buffer.putDouble(40.0)
        buffer.putDouble(-104.0)
        buffer.putDouble(41.0)

        val geom = WkbDecoder.decode(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is RenderGeometry.LineString)
        val line = geom as RenderGeometry.LineString
        assertEquals(2, line.coordinates.size)
        assertEquals(-105.0, line.coordinates[0].longitude, 1e-6)
        assertEquals(-104.0, line.coordinates[1].longitude, 1e-6)
    }

    @Test
    fun decodesPolygonWithHole() {
        val numOuter = 4
        val numInner = 4
        val totalBytes = 1 + 4 + 4 + (4 + numOuter * 16) + (4 + numInner * 16)
        val buffer = ByteBuffer.allocate(totalBytes)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(0x00.toByte())
        buffer.putInt(3)
        buffer.putInt(2)

        buffer.putInt(numOuter)
        buffer.putDouble(-105.0); buffer.putDouble(40.0)
        buffer.putDouble(-104.0); buffer.putDouble(40.0)
        buffer.putDouble(-104.0); buffer.putDouble(41.0)
        buffer.putDouble(-105.0); buffer.putDouble(40.0)

        buffer.putInt(numInner)
        buffer.putDouble(-104.8); buffer.putDouble(40.2)
        buffer.putDouble(-104.2); buffer.putDouble(40.2)
        buffer.putDouble(-104.2); buffer.putDouble(40.8)
        buffer.putDouble(-104.8); buffer.putDouble(40.2)

        val geom = WkbDecoder.decode(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is RenderGeometry.Polygon)
        val poly = geom as RenderGeometry.Polygon
        assertEquals(2, poly.coordinates.size)
        assertEquals(4, poly.coordinates[0].size)
        assertEquals(4, poly.coordinates[1].size)
    }

    @Test
    fun decodesGeoPackageBinaryWithEnvelope() {
        val totalBytes = 8 + 32 + 21
        val buffer = ByteBuffer.allocate(totalBytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x47.toByte())
        buffer.put(0x50.toByte())
        buffer.put(0x00.toByte())
        buffer.put(0x03.toByte()) // bit 0 = 1 (LE), bits 1-3 = 1 (envelope 32 bytes)
        buffer.putInt(4326)

        buffer.putDouble(-106.0)
        buffer.putDouble(-104.0)
        buffer.putDouble(39.0)
        buffer.putDouble(41.0)

        buffer.put(0x01.toByte())
        buffer.putInt(1)
        buffer.putDouble(-105.5)
        buffer.putDouble(40.2)

        val geom = WkbDecoder.decode(buffer.array())
        assertNotNull(geom)
        assertTrue(geom is RenderGeometry.Point)
        val pt = geom as RenderGeometry.Point
        assertEquals(-105.5, pt.coordinates.longitude, 1e-6)
        assertEquals(40.2, pt.coordinates.latitude, 1e-6)
    }

    @Test
    fun returnsNullOnTruncatedBytes() {
        val truncated = byteArrayOf(0x01, 0x01, 0x00)
        assertNull(WkbDecoder.decode(truncated))
        assertNull(WkbDecoder.decode(ByteArray(0)))
    }
}
