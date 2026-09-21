// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.camera

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.location.AtlasLocationFix
import com.sovereignatlas.atlas.location.AtlasLocationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fix(
    latitude: Double = 10.0,
    longitude: Double = 20.0,
): AtlasLocationFix {
    return AtlasLocationFix(
        position = AtlasCoordinate(latitude = latitude, longitude = longitude),
        atMs = 1L,
        source = "gps",
    )
}

final class AtlasCameraPolicyTest {
    @Test
    fun startupValidFixYieldsLocalView() {
        val intent = startupIntent(AtlasLocationStatus.valid, fix())
            ?: throw AssertionError("intent must exist")
        assertEquals(10.0, intent.center.latitude, 0.0)
        assertEquals(20.0, intent.center.longitude, 0.0)
        assertEquals(STARTUP_LOCAL_ZOOM, intent.zoom, 0.0)
        assertEquals(0.0, intent.bearing, 0.0)
        assertTrue(intent.validate().isValid)
    }

    @Test
    fun startupRejectsNonValid() {
        assertNull(startupIntent(AtlasLocationStatus.stale, fix()))
        assertNull(startupIntent(AtlasLocationStatus.acquiring, fix()))
        assertNull(startupIntent(AtlasLocationStatus.denied, fix()))
        assertNull(startupIntent(AtlasLocationStatus.valid, null))
        assertNull(startupIntent(AtlasLocationStatus.error, fix()))
    }

    @Test
    fun myLocationClampsUpToFifteen() {
        fun zoomAt(current: Double): Double {
            return myLocationIntent(AtlasLocationStatus.valid, fix(), current, 0.0)?.zoom
                ?: Double.NaN
        }
        assertEquals(15.0, zoomAt(7.0), 0.0)
        assertEquals(15.0, zoomAt(10.0), 0.0)
        assertEquals(15.0, zoomAt(15.0), 0.0)
    }

    @Test
    fun myLocationPreservesCloserZoom() {
        fun zoomAt(current: Double): Double {
            return myLocationIntent(AtlasLocationStatus.valid, fix(), current, 0.0)?.zoom
                ?: Double.NaN
        }
        assertEquals(17.0, zoomAt(17.0), 0.0)
        assertEquals(18.0, zoomAt(18.0), 0.0)
    }

    @Test
    fun myLocationPreservesBearing() {
        val intent = myLocationIntent(AtlasLocationStatus.valid, fix(), 15.0, 37.0)
            ?: throw AssertionError("intent must exist")
        assertEquals(37.0, intent.bearing, 0.0)
        assertEquals(10.0, intent.center.latitude, 0.0)
    }

    @Test
    fun myLocationRejectsNonValid() {
        assertNull(myLocationIntent(AtlasLocationStatus.stale, fix(), 15.0, 0.0))
        assertNull(myLocationIntent(AtlasLocationStatus.acquiring, null, 15.0, 0.0))
        assertNull(myLocationIntent(AtlasLocationStatus.valid, null, 15.0, 0.0))
    }

    @Test
    fun myLocationRejectsInvalidZoom() {
        assertNull(myLocationIntent(AtlasLocationStatus.valid, fix(), Double.NaN, 0.0))
        assertNull(myLocationIntent(AtlasLocationStatus.valid, fix(), 99.0, 0.0))
    }

    @Test
    fun goToPreservesZoomWithoutRotation() {
        val target = AtlasCoordinate(latitude = 45.0, longitude = -93.0)
        val intent = goToIntent(target, 13.5, 30.0)
            ?: throw AssertionError("intent must exist")
        assertEquals(45.0, intent.center.latitude, 0.0)
        assertEquals(13.5, intent.zoom, 0.0)
        assertEquals(30.0, intent.bearing, 0.0)
    }

    @Test
    fun rotationPolicyDefaultsGated() {
        assertEquals(AtlasRotationPolicy.gated, DEFAULT_ROTATION_POLICY)
    }
}
