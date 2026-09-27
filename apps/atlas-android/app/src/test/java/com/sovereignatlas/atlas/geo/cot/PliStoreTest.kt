// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

final class PliStoreTest {
    private fun pli(uid: String, ageMillis: Long): CotPli {
        return CotPli(
            uid = uid,
            type = "a-f-G-U-C-I",
            callsign = uid,
            latitude = 44.9,
            longitude = -93.1,
            timestamp = System.currentTimeMillis() - ageMillis,
        )
    }

    @Test
    fun updatePrunesStaleEntries() {
        val store = PliStore()
        store.update(pli("stale", 16 * 60 * 1000L))
        store.update(pli("fresh", 60 * 1000L))
        val active = store.activePlis.value
        assertEquals(1, active.size)
        assertTrue(active.containsKey("fresh"))
    }

    @Test
    fun pruneExpiredDropsOldMarkers() {
        val store = PliStore(ttlMillis = 50L)
        store.update(pli("ephemeral", 0L))
        assertEquals(1, store.activePlis.value.size)
        Thread.sleep(120L)
        store.pruneExpired()
        assertTrue(store.activePlis.value.isEmpty())
    }

    @Test
    fun pruneKeepsFreshWhenNothingExpired() {
        val store = PliStore()
        store.update(pli("a", 0L))
        store.update(pli("b", 5 * 60 * 1000L))
        val before = store.activePlis.value
        store.pruneExpired()
        assertEquals(before, store.activePlis.value)
    }

    @Test
    fun sameUidOverwrites() {
        val store = PliStore()
        store.update(pli("unit-1", 10 * 60 * 1000L))
        store.update(pli("unit-1", 0L).copy(callsign = "RENAMED"))
        val active = store.activePlis.value
        assertEquals(1, active.size)
        assertEquals("RENAMED", active["unit-1"]?.callsign)
    }
}
