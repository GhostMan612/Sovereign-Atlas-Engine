// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

final class WaypointSelectionTest {
    @Test
    fun initialSelectionIsNull() {
        assertNull(WaypointSelection().selectedWaypointId.value)
    }

    @Test
    fun selectAndClearRoundTrip() {
        val selection = WaypointSelection()
        selection.selectWaypoint("wp-1")
        assertEquals("wp-1", selection.selectedWaypointId.value)
        selection.clearWaypointSelection()
        assertNull(selection.selectedWaypointId.value)
    }

    @Test
    fun reselectReplacesPrevious() {
        val selection = WaypointSelection()
        selection.selectWaypoint("wp-1")
        selection.selectWaypoint("wp-2")
        assertEquals("wp-2", selection.selectedWaypointId.value)
    }
}
