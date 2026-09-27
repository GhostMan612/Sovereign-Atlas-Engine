// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.geo.GeoPoint
import com.sovereignatlas.atlas.geo.cot.CotProtobufGenerator
import com.sovereignatlas.atlas.geo.cot.ParsedCot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

final class AtakPayloadParserTest {
    private val parser = AtakPayloadParser()

    private fun pliPayload(
        altitude: Double = 250.0,
        ce: Double? = 5.0,
    ): ByteArray {
        return CotProtobufGenerator.generatePliProto(
            localUid = "unit-1",
            callsign = "ALPHA",
            geoPoint = GeoPoint(44.9, -93.1, altitude, null, null, 0L),
            ceFallback = ce,
        )
    }

    @Test
    fun protoPliRoundTrips() {
        val parsed = parser.parse(pliPayload())
        assertTrue(parsed is ParsedCot.Pli)
        val pli = (parsed as ParsedCot.Pli).pli
        assertEquals("unit-1", pli.uid)
        assertEquals("a-f-G-U-C", pli.type)
        assertEquals("ALPHA", pli.callsign)
        assertEquals(44.9, pli.latitude, 0.0)
        assertEquals(250.0, pli.altitude!!, 0.0)
    }

    @Test
    fun nanAltitudeIsDropped() {
        val parsed = parser.parse(pliPayload(altitude = Double.NaN))
        val pli = (parsed as ParsedCot.Pli).pli
        assertNull(pli.altitude)
    }

    @Test
    fun infiniteAltitudeIsDropped() {
        val parsed = parser.parse(pliPayload(altitude = Double.POSITIVE_INFINITY))
        val pli = (parsed as ParsedCot.Pli).pli
        assertNull(pli.altitude)
    }

    @Test
    fun zeroAltitudeWithoutPrecisionIsDropped() {
        val parsed = parser.parse(pliPayload(altitude = 0.0))
        val pli = (parsed as ParsedCot.Pli).pli
        assertNull(pli.altitude)
    }

    @Test
    fun zeroAltitudeWithPrecisionIsKept() {
        val event = atakmap.commoncommo.v1.CotEvent(
            type = "a-f-G-U-C",
            uid = "unit-1",
            lat = 44.9,
            lon = -93.1,
            hae = 0.0,
            detail = atakmap.commoncommo.v1.Detail(
                precisionLocation = atakmap.commoncommo.v1.PrecisionLocation(
                    geopointsrc = "GPS",
                    altsrc = "GPS",
                ),
            ),
        )
        val msg = atakmap.commoncommo.v1.TakMessage(cotEvent = event)
        val payload = byteArrayOf(0xBF.toByte(), 0x01.toByte(), 0xBF.toByte()) +
            atakmap.commoncommo.v1.TakMessage.ADAPTER.encode(msg)
        val parsed = parser.parse(payload)
        val pli = (parsed as ParsedCot.Pli).pli
        assertEquals(0.0, pli.altitude!!, 0.0)
    }

    @Test
    fun garbageProtoReturnsNull() {
        val header = byteArrayOf(0xBF.toByte(), 0x01.toByte(), 0xBF.toByte())
        assertNull(parser.parse(header + byteArrayOf(9, 9, 9, 9, 9)))
    }

    @Test
    fun unknownVersionReturnsNull() {
        val header = byteArrayOf(0xBF.toByte(), 0x7F.toByte(), 0xBF.toByte())
        assertNull(parser.parse(header + byteArrayOf(1, 2, 3)))
    }

    @Test
    fun shortPacketReturnsNull() {
        assertNull(parser.parse(byteArrayOf(0xBF.toByte(), 0x01.toByte())))
    }
}
