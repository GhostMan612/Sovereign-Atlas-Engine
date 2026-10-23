// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.viewshed

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewshedStateTest {

    @Test
    fun testInitialState() {
        val state = ViewshedState()
        assertNull(state.result.value)
        assertFalse(state.isCalculating.value)
        assertNull(state.observer.value)
    }

    @Test
    fun testSetAndClear() {
        val state = ViewshedState()
        val coord = AtlasCoordinate(37.7749, -122.4194)

        state.setObserver(coord)
        assertEquals(coord, state.observer.value)

        state.setCalculating(true)
        assertTrue(state.isCalculating.value)

        val config = ViewshedConfig(observer = coord)
        val dummyResult = ViewshedResult(
            config = config,
            observerElevationMeters = 50.0,
            rays = emptyList(),
            visiblePerimeter = emptyList(),
            maxRangeCircle = emptyList(),
        )

        state.setResult(dummyResult)
        assertNotNull(state.result.value)
        assertEquals(50.0, state.result.value!!.observerElevationMeters, 0.001)

        state.clear()
        assertNull(state.result.value)
        assertFalse(state.isCalculating.value)
        assertNull(state.observer.value)
    }
}
