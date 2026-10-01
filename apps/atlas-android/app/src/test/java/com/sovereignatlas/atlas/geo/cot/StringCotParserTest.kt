// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for the pure CoT string reader.
 *
 * These run in the host gate with no device, which is the entire reason this
 * parser exists: the shipped `AtakPayloadParser` needs `android.util.Xml` and so
 * none of its rules are provable off-device.
 */
final class StringCotParserTest {

    @Test
    fun parsesAFriendlyPli() {
        val event = StringCotParser.parse(FRIENDLY_PLI)

        assertNotNull(event)
        assertEquals("2.0", event!!.version)
        assertEquals("test-uid", event.uid)
        assertEquals("a-f-G-U-C", event.type)
        assertEquals("m-g", event.how)
        assertEquals("2026-10-01T00:00:00Z", event.time)
        assertEquals("2026-10-01T00:00:00Z", event.start)
        assertEquals("2026-10-01T00:05:00Z", event.stale)
        assertEquals(34.1, event.lat, 1e-9)
        assertEquals(-118.2, event.lon, 1e-9)
        assertEquals(10.0, event.hae!!, 1e-9)
        assertEquals(2.0, event.ce!!, 1e-9)
        assertEquals(2.0, event.le!!, 1e-9)
        assertEquals("GHOST-1", event.callsign)
    }

    @Test
    fun survivesSingleQuotesAndStrayWhitespaceBetweenAttributes() {
        // Producers in the field emit both quote styles and irregular spacing.
        val messy = "<event  version='2.0'   uid='u-2' type = 'a-f-G' how='m-g' " +
            "time='2026-10-01T00:00:00Z' start='2026-10-01T00:00:00Z' " +
            "stale='2026-10-01T00:05:00Z'>\n" +
            "  <point   lat = '45.5'   lon='-122.6' hae='0.0' />\n" +
            "  <detail><contact callsign = 'MESSY-1' /></detail>\n" +
            "</event>"

        val event = StringCotParser.parse(messy)

        assertNotNull(event)
        assertEquals("u-2", event!!.uid)
        assertEquals(45.5, event.lat, 1e-9)
        assertEquals(-122.6, event.lon, 1e-9)
        assertEquals("MESSY-1", event.callsign)
        // hae="0.0" is a reported zero, distinct from an absent attribute.
        assertEquals(0.0, event.hae!!, 1e-9)
    }

    @Test
    fun anEventWithNoPointReturnsNull() {
        val noPoint = "<event version=\"2.0\" uid=\"u-3\" type=\"a-f-G\" how=\"m-g\" " +
            "time=\"2026-10-01T00:00:00Z\" start=\"2026-10-01T00:00:00Z\" " +
            "stale=\"2026-10-01T00:05:00Z\"><detail/></event>"

        assertNull(StringCotParser.parse(noPoint))
    }

    @Test
    fun nullIslandIsAValidCoordinateAndIsNotRejected() {
        // The regression this parser exists partly to end: AtakPayloadParser
        // discards any point with a zero coordinate. 0,0 is a real position in the
        // Gulf of Guinea and a report of it is a report.
        val nullIsland = "<event version=\"2.0\" uid=\"u-4\" type=\"a-f-G-U-C\" how=\"m-g\" " +
            "time=\"2026-10-01T00:00:00Z\" start=\"2026-10-01T00:00:00Z\" " +
            "stale=\"2026-10-01T00:05:00Z\">" +
            "<point lat=\"0.0\" lon=\"0.0\" ce=\"9999999.0\" le=\"9999999.0\"/></event>"

        val event = StringCotParser.parse(nullIsland)

        assertNotNull("Null Island must parse", event)
        assertEquals(0.0, event!!.lat, 1e-9)
        assertEquals(0.0, event.lon, 1e-9)
    }

    @Test
    fun garbageReturnsNullWithoutThrowing() {
        assertNull(StringCotParser.parse("not xml at all"))
        assertNull(StringCotParser.parse(""))
        assertNull(StringCotParser.parse("<event"))
        // Truncated mid-tag: the open tag never closes.
        assertNull(StringCotParser.parse("<event version=\"2.0\" uid=\"u\""))
        assertNull(StringCotParser.parse("{\"json\":true}"))
    }

    @Test
    fun aBlankMandatoryAttributeIsTreatedAsMissing() {
        val blankUid = FRIENDLY_PLI.replace("uid=\"test-uid\"", "uid=\"\"")

        assertNull(StringCotParser.parse(blankUid))
    }

    @Test
    fun optionalAttributesAbsentFromTheDocumentAreNullNotZero() {
        val bare = "<event version=\"2.0\" uid=\"u-5\" type=\"a-f-G\" how=\"m-g\" " +
            "time=\"2026-10-01T00:00:00Z\" start=\"2026-10-01T00:00:00Z\" " +
            "stale=\"2026-10-01T00:05:00Z\"><point lat=\"10\" lon=\"20\"/></event>"

        val event = StringCotParser.parse(bare)

        assertNotNull(event)
        assertNull("absent hae must not become 0.0", event!!.hae)
        assertNull("absent ce must not become 0.0", event.ce)
        assertNull("absent le must not become 0.0", event.le)
        assertNull(event.callsign)
    }

    @Test
    fun aNonNumericUncertaintyIsDroppedWithoutLosingThePosition() {
        val badCe = FRIENDLY_PLI.replace("ce=\"2.0\"", "ce=\"unknown\"")

        val event = StringCotParser.parse(badCe)

        assertNotNull("a bad ce must not discard a good position", event)
        assertNull(event!!.ce)
        assertEquals(34.1, event.lat, 1e-9)
    }

    @Test
    fun aCoordinateOutOfRangeIsRejected() {
        val badLat = FRIENDLY_PLI.replace("lat=\"34.1\"", "lat=\"999.0\"")

        assertNull(StringCotParser.parse(badLat))
    }

    @Test
    fun aTagWhoseNameMerelyStartsWithTheWantedNameIsNotMatched() {
        // Guards the boundary check in openTagBody: "<eventual" must not satisfy a
        // search for "<event".
        val decoy = "<eventual version=\"2.0\"><point lat=\"1\" lon=\"2\"/></eventual>"

        assertNull(StringCotParser.parse(decoy))
    }

    @Test
    fun staleComparisonIsStringOrderedAndPure() {
        val event = StringCotParser.parse(FRIENDLY_PLI)!!

        assertTrue(event.isStaleAsOf("2026-10-01T00:10:00Z"))
        assertTrue(!event.isStaleAsOf("2026-10-01T00:01:00Z"))
    }

    private companion object {
        val FRIENDLY_PLI =
            "<event version=\"2.0\" uid=\"test-uid\" type=\"a-f-G-U-C\" " +
                "time=\"2026-10-01T00:00:00Z\" start=\"2026-10-01T00:00:00Z\" " +
                "stale=\"2026-10-01T00:05:00Z\" how=\"m-g\">" +
                "<point lat=\"34.1\" lon=\"-118.2\" hae=\"10.0\" ce=\"2.0\" le=\"2.0\"/>" +
                "<detail><contact callsign=\"GHOST-1\"/></detail></event>"
    }
}