// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.goto

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

final class GoToStateTest {
    private fun active(): GoToState {
        val state = GoToState()
        state.activate(
            id = "wp-000001",
            latitude = 45.0,
            longitude = -93.0,
            label = "Alpha",
        )
        return state
    }

    @Test
    fun inactiveByDefault() {
        val state = GoToState()
        assertFalse(state.isActive())
        assertNull(state.targetOrNull())
        assertNull(state.distanceKmTo(AtlasCoordinate(latitude = 0.0, longitude = 0.0)))
        assertNull(state.bearingDegTo(AtlasCoordinate(latitude = 0.0, longitude = 0.0)))
    }

    @Test
    fun activateSnapshotsTarget() {
        val state = active()
        assertTrue(state.isActive())
        assertEquals("wp-000001", state.targetOrNull()?.id)
        assertEquals("Alpha", state.targetOrNull()?.label)
        assertEquals(45.0, state.targetOrNull()?.latitude)
    }

    @Test
    fun activateRejectsInvalidCoordinate() {
        val state = GoToState()
        try {
            state.activate(id = "x", latitude = 91.0, longitude = 0.0, label = "bad")
            throw AssertionError("latitude 91 must be rejected")
        } catch (error: Exception) {
            assertFalse(state.isActive())
        }
    }

    @Test
    fun clearDeactivatesIdempotently() {
        val state = active()
        var count = 0
        state.addListener { count++ }
        state.clear()
        assertFalse(state.isActive())
        state.clear()
        assertEquals(1, count)
    }

    @Test
    fun distanceAndBearingAreLive() {
        val state = active()
        val fix = AtlasCoordinate(latitude = 44.0, longitude = -93.0)
        val distance = state.distanceKmTo(fix)
        assertTrue(distance != null && distance > 100.0 && distance < 120.0)
        assertEquals(0.0, state.bearingDegTo(fix)!!, 0.5)
    }

    @Test
    fun nullFixYieldsNull() {
        val state = active()
        assertNull(state.distanceKmTo(null))
        assertNull(state.bearingDegTo(null))
    }

    @Test
    fun coincidentBearingIsNull() {
        val state = active()
        val fix = AtlasCoordinate(latitude = 45.0, longitude = -93.0)
        assertEquals(0.0, state.distanceKmTo(fix)!!, 0.0)
        assertNull(state.bearingDegTo(fix))
    }

    @Test
    fun listenersNotified() {
        val state = GoToState()
        var count = 0
        state.addListener { count++ }
        state.activate(id = "a", latitude = 1.0, longitude = 1.0, label = "A")
        state.clear()
        assertEquals(2, count)
    }
}
