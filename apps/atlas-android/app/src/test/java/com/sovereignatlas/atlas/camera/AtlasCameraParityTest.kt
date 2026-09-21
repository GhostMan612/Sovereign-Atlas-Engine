// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.camera

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.parity.GoldenHarness
import com.sovereignatlas.atlas.parity.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

final class AtlasCameraParityTest {
    @Test
    fun serdeRoundTripMatches() {
        val vector = GoldenHarness.load("camera", "CAM-002_serde.json")
        val serialized =
            ((vector.expected as JsonValue.Obj).entries["serialized"] as JsonValue.Str).value
        val state = AtlasCameraState.parse(serialized)
        assertEquals(39.83, state.center.latitude, 0.0)
        assertEquals(-98.58, state.center.longitude, 0.0)
        assertEquals(3.0, state.zoom, 0.0)
        assertEquals(0.0, state.bearing, 0.0)
        assertEquals(0.0, state.pitch, 0.0)
        assertTrue(state.validate().isValid)
        assertEquals(serialized, state.serialize())
    }

    @Test
    fun zoomBoundsMatch() {
        val vector = GoldenHarness.load("camera", "CAM-005_zoom-bounds.json")
        val expected = vector.expected as JsonValue.Obj
        val accept = (expected.entries["accept"] as JsonValue.Arr).items
            .map { (it as JsonValue.Num).value }
        val reject = (expected.entries["reject"] as JsonValue.Arr).items
            .map { (it as JsonValue.Num).value }
        for (zoom in accept) {
            val state = AtlasCameraState(
                center = AtlasCoordinate(latitude = 10.0, longitude = 20.0),
                zoom = zoom,
                bearing = 0.0,
                pitch = 0.0,
            )
            assertTrue("zoom $zoom accepted", state.validate().isValid)
        }
        for (zoom in reject) {
            val state = AtlasCameraState(
                center = AtlasCoordinate(latitude = 10.0, longitude = 20.0),
                zoom = zoom,
                bearing = 0.0,
                pitch = 0.0,
            )
            assertFalse("zoom $zoom rejected", state.validate().isValid)
        }
    }

    @Test
    fun homeMatchesCanonicalOverview() {
        val home = AtlasCameraState.home()
        assertEquals(39.83, home.center.latitude, 0.0)
        assertEquals(-98.58, home.center.longitude, 0.0)
        assertEquals(3.0, home.zoom, 0.0)
        assertTrue(home.validate().isValid)
    }
}
