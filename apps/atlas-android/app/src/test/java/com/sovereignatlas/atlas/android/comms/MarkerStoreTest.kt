// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.geo.cot.CotMarker
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class MarkerStoreTest {
    private fun marker(uid: String, callsign: String = "Hostile"): CotMarker {
        return CotMarker(
            uid = uid,
            type = "a-h-G",
            callsign = callsign,
            latitude = 44.9,
            longitude = -93.1,
            altitude = null,
            timestampMillis = System.currentTimeMillis(),
        )
    }

    @Test
    fun addMarkerPublishesByUid() {
        val store = MarkerStore(TestScope())
        store.addMarker(marker("m-1"))
        val markers = store.markerStream.value
        assertEquals(1, markers.size)
        assertEquals("Hostile", markers["m-1"]?.callsign)
    }

    @Test
    fun sameUidOverwrites() {
        val store = MarkerStore(TestScope())
        store.addMarker(marker("m-1", "Hostile"))
        store.addMarker(marker("m-1", "RENAMED"))
        val markers = store.markerStream.value
        assertEquals(1, markers.size)
        assertEquals("RENAMED", markers["m-1"]?.callsign)
    }

    @Test
    fun distinctUidsAccumulate() {
        val store = MarkerStore(TestScope())
        store.addMarker(marker("m-1"))
        store.addMarker(marker("m-2", "Neutral"))
        assertEquals(2, store.markerStream.value.size)
    }

    @Test
    fun tickerPrunesStaleMarkers() = runTest {
        val store = MarkerStore(this)
        val stale = marker("old").copy(timestampMillis = System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        store.addMarker(stale)
        store.addMarker(marker("fresh"))
        assertEquals(2, store.markerStream.value.size)
        advanceTimeBy(61_000L)
        assertEquals(1, store.markerStream.value.size)
        assertTrue(store.markerStream.value.containsKey("fresh"))
        store.shutdown()
    }
}
