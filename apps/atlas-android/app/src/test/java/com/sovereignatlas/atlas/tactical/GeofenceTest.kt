// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun point(latitude: Double, longitude: Double): AtlasCoordinate {
    return AtlasCoordinate(latitude = latitude, longitude = longitude)
}

final class GeofenceTest {
    private val fence = RadialFence(
        center = point(0.0, 0.0),
        radiusMeters = 1000.0,
    )

    @Test
    fun tac2002InsideOutsideDisarmed() {
        assertTrue(fence.breachedBy(point(0.0, 0.004)))
        assertFalse(fence.breachedBy(point(0.0, 0.02)))
        assertFalse(fence.copy(armed = false).breachedBy(point(0.0, 0.004)))
    }

    @Test
    fun invalidFenceRejected() {
        assertTrue(fence.isValid())
        assertFalse(fence.copy(radiusMeters = 0.0).isValid())
        assertFalse(fence.copy(radiusMeters = -5.0).isValid())
    }

    @Test
    fun polygonClosesRing() {
        val ring = fencePolygon(point(0.0, 0.0), 1000.0)
        assertEquals(65, ring.size)
        assertEquals(ring.first().latitude, ring.last().latitude, 1e-9)
        assertEquals(ring.first().longitude, ring.last().longitude, 1e-9)
    }
}
