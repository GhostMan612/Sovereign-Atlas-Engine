// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.goto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

final class GoToCameraTest {
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
    fun inactiveYieldsNoIntent() {
        assertNull(goToCameraIntent(GoToState(), 13.0, 0.0))
    }

    @Test
    fun activeTargetMovesCameraWithZoomPreserved() {
        val intent = goToCameraIntent(active(), 11.5, 30.0)
        assertEquals(45.0, intent?.center?.latitude)
        assertEquals(-93.0, intent?.center?.longitude)
        assertEquals(11.5, intent?.zoom)
        assertEquals(30.0, intent?.bearing)
        assertEquals(0.0, intent?.pitch)
    }

    @Test
    fun clearedTargetYieldsNoIntent() {
        val state = active()
        state.clear()
        assertNull(goToCameraIntent(state, 13.0, 0.0))
    }
}
