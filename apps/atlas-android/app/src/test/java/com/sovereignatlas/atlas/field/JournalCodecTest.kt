// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import com.sovereignatlas.atlas.core.AtlasContractException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

final class JournalCodecTest {
    private fun waypoint(
        id: String = "wp-000001",
        source: WaypointSource = WaypointSource.mapSelected,
    ): StoredWaypoint {
        return StoredWaypoint(
            id = id,
            latitude = 45.0,
            longitude = -93.0,
            createdAt = 1000,
            label = "Alpha",
            note = "",
            source = source,
        )
    }

    @Test
    fun envelopeRoundTrip() {
        val records = listOf(
            waypoint("wp-000001"),
            waypoint("wp-000002", WaypointSource.gpsRecorded),
        )
        val tracks = listOf(
            StoredTrack(
                id = "trk-000001",
                createdAt = 2000,
                points = records,
                source = WaypointSource.gpsRecorded,
            ),
        )
        val envelope = JournalCodec.envelope(records, tracks)
        assertEquals(1, envelope["version"])
        val parsed = JournalCodec.parseEnvelope(envelope)
        assertEquals(listOf("wp-000001", "wp-000002"), parsed.waypoints.keys.toList())
        assertEquals(listOf("trk-000001"), parsed.tracks.keys.toList())
        assertEquals(0, parsed.skipped)
        assertEquals(2, parsed.waypointSequence)
        assertEquals(1, parsed.trackSequence)
        assertEquals(2, parsed.tracks["trk-000001"]?.pointCount)
    }

    @Test
    fun versionMismatchRejected() {
        try {
            JournalCodec.parseEnvelope(mapOf("version" to 2, "waypoints" to emptyList<Any>()))
            throw AssertionError("version 2 must be rejected")
        } catch (error: AtlasContractException) {
            assertEquals("UNSUPPORTED_JOURNAL", error.rejection.category)
        }
    }

    @Test
    fun missingWaypointListRejected() {
        try {
            JournalCodec.parseEnvelope(mapOf("version" to 1))
            throw AssertionError("missing waypoints must be rejected")
        } catch (error: AtlasContractException) {
            assertEquals("JOURNAL_NO_WAYPOINTS", error.rejection.category)
        }
    }

    @Test
    fun corruptRecordsSkippedWithCount() {
        val parsed = JournalCodec.parseEnvelope(
            mapOf(
                "version" to 1,
                "waypoints" to listOf(
                    waypoint("wp-000001").toMap(),
                    mapOf("id" to "", "latitude" to 0.0, "longitude" to 0.0),
                    mapOf("id" to "wp-000002", "latitude" to 91.0, "longitude" to 0.0),
                    waypoint("wp-000001").toMap(),
                ),
            ),
        )
        assertEquals(listOf("wp-000001"), parsed.waypoints.keys.toList())
        assertEquals(3, parsed.skipped)
    }

    @Test
    fun strictCreatedAtRejectsFractional() {
        assertNull(
            StoredWaypoint.tryParse(
                mapOf(
                    "id" to "wp-000001",
                    "latitude" to 45.0,
                    "longitude" to -93.0,
                    "created_at" to 1000.0,
                    "source" to "map_selected",
                ),
            ),
        )
    }

    @Test
    fun provenanceRoundTrip() {
        assertEquals("map_selected", waypointSourceName(WaypointSource.mapSelected))
        assertEquals("gps_recorded", waypointSourceName(WaypointSource.gpsRecorded))
        assertEquals(WaypointSource.mapSelected, waypointSourceFromName("map_selected"))
        assertEquals(WaypointSource.gpsRecorded, waypointSourceFromName("gps_recorded"))
        assertNull(waypointSourceFromName("satellite"))
    }

    @Test
    fun idFormatsMatchFlutter() {
        assertEquals("wp-000001", JournalCodec.waypointIdFor(1))
        assertEquals("trk-000042", JournalCodec.trackIdFor(42))
        assertEquals(5, JournalCodec.waypointSequenceOf("wp-000005"))
        assertEquals(0, JournalCodec.waypointSequenceOf("trk-000005"))
        assertEquals(7, JournalCodec.trackSequenceOf("trk-000007"))
    }
}
