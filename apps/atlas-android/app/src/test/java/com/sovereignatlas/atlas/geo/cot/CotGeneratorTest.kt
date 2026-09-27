// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class CotGeneratorTest {
    private val fixed = Instant.parse("2026-09-27T12:00:00.000Z")

    @Test
    fun timestampsAreExactIso8601() {
        val xml = CotGenerator.generatePliXml(
            uid = "unit-1",
            callsign = "ALPHA",
            latitude = 44.9,
            longitude = -93.1,
            altitude = 250.0,
            accuracyMeters = 5.0,
            observedAt = fixed,
        )
        assertTrue(xml.contains("time=\"2026-09-27T12:00:00.000Z\""))
        assertTrue(xml.contains("stale=\"2026-09-27T12:15:00.000Z\""))
    }

    @Test
    fun specialCharactersAreEscaped() {
        val xml = CotGenerator.generatePliXml(
            uid = "a&b",
            callsign = "A<B>\"C\"",
            latitude = 44.9,
            longitude = -93.1,
            altitude = 0.0,
            accuracyMeters = 5.0,
            observedAt = fixed,
        )
        assertTrue(xml.contains("uid=\"a&amp;b\""))
        assertTrue(xml.contains("callsign=\"A&lt;B&gt;&quot;C&quot;\""))
    }

    @Test
    fun controlCharactersAreStripped() {
        val xml = CotGenerator.generatePliXml(
            uid = "unit-1",
            callsign = "A\u0001B\u0007C",
            latitude = 44.9,
            longitude = -93.1,
            altitude = 0.0,
            accuracyMeters = 5.0,
            observedAt = fixed,
        )
        assertTrue(xml.contains("callsign=\"A?B?C\""))
        assertTrue(!xml.contains(""))
    }

    @Test
    fun schemaShapeIsComplete() {
        val xml = CotGenerator.generatePliXml(
            uid = "unit-1",
            callsign = "ALPHA",
            latitude = 44.9,
            longitude = -93.1,
            altitude = 250.0,
            accuracyMeters = 5.0,
            observedAt = fixed,
        )
        assertTrue(xml.contains("type=\"a-f-G-U-C\""))
        assertTrue(xml.contains("<point lat=\"44.9\" lon=\"-93.1\" hae=\"250.0\" ce=\"5.0\" le=\"9999999.0\"/>"))
        assertTrue(xml.contains("<__group name=\"Cyan\" role=\"Team Member\"/>"))
    }
}
