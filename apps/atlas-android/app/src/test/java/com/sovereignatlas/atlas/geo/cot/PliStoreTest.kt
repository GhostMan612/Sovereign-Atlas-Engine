// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic coverage for [PliStore].
 *
 * No `Thread.sleep`. The previous version slept 120 ms to prove a 50 ms TTL, which
 * made the suite slow and still could not state WHICH instant it meant. `prune`
 * now takes the instant, so every case below names it.
 */
final class PliStoreTest {

    private fun pli(
        uid: String,
        timestamp: Long,
        expiresAtMillis: Long? = null,
        callsign: String = uid,
    ): CotPli = CotPli(
        uid = uid,
        type = "a-f-G-U-C-I",
        callsign = callsign,
        latitude = 44.9,
        longitude = -93.1,
        timestamp = timestamp,
        expiresAtMillis = expiresAtMillis,
    )

    @Test
    fun upsertPublishesByUid() {
        val store = PliStore(localDeviceUid = "local")
        store.upsert(pli("unit-1", NOW))

        assertEquals(1, store.observe().value.size)
        assertEquals("unit-1", store.observe().value["unit-1"]?.callsign)
    }

    @Test
    fun sameUidOverwrites() {
        val store = PliStore(localDeviceUid = "local")
        store.upsert(pli("unit-1", NOW, callsign = "First"))
        store.upsert(pli("unit-1", NOW, callsign = "RENAMED"))

        val active = store.observe().value
        assertEquals(1, active.size)
        assertEquals("RENAMED", active["unit-1"]?.callsign)
    }

    @Test
    fun localLoopbackIsFiltered() {
        val store = PliStore(localDeviceUid = "local")
        store.upsert(pli("local", NOW))

        assertTrue(store.observe().value.isEmpty())
    }

    @Test
    fun pruneDropsEntriesPastTheTtl() {
        val store = PliStore(localDeviceUid = "local", ttlMillis = TTL)
        store.upsert(pli("ephemeral", NOW))

        store.prune(NOW + TTL - 1)
        assertEquals("one millisecond before expiry it is still live", 1, store.observe().value.size)

        store.prune(NOW + TTL)
        assertTrue("at the expiry instant it is gone", store.observe().value.isEmpty())
    }

    @Test
    fun pruneKeepsFreshEntriesWhenNothingExpired() {
        val store = PliStore(localDeviceUid = "local", ttlMillis = TTL)
        store.upsert(pli("a", NOW))
        store.upsert(pli("b", NOW + 1))
        val before = store.observe().value

        store.prune(NOW)

        assertEquals(before, store.observe().value)
    }

    @Test
    fun producerStaleOverridesTheLocalTtl() {
        // The whole point of expiresAtMillis: a sender said this expires at
        // NOW+1000, which is far inside the local TTL, and the store honours the
        // sender rather than the guess.
        val store = PliStore(localDeviceUid = "local", ttlMillis = 60_000L)
        store.upsert(pli("short-lived", NOW, expiresAtMillis = NOW + 1_000L))

        store.prune(NOW + 999)
        assertEquals(1, store.observe().value.size)

        store.prune(NOW + 1_000)
        assertTrue("producer stale must beat the local TTL", store.observe().value.isEmpty())
    }

    @Test
    fun producerStaleMayExceedTheLocalTtl() {
        // The reverse case, and the one a blanket TTL would get wrong: a long-lived
        // track whose sender said it is good for a day.
        val store = PliStore(localDeviceUid = "local", ttlMillis = 1_000L)
        store.upsert(pli("long-lived", NOW, expiresAtMillis = NOW + 86_400_000L))

        store.prune(NOW + 60_000L)
        assertEquals("a sender's hour-long track survives a 1s local TTL", 1, store.observe().value.size)
    }

    @Test
    fun upsertDropsAlreadyExpiredEntriesWithoutAPruneCall() {
        // A receiver that only pruned on its 30s ticker would hold dead tracks for
        // up to 30s. upsert filters, so the map does not show a ghost.
        val store = PliStore(localDeviceUid = "local", ttlMillis = TTL, clock = { NOW + 10_000L })
        store.upsert(pli("doomed", NOW, expiresAtMillis = NOW + 1_000L))
        store.upsert(pli("current", NOW))

        val active = store.observe().value
        assertEquals(1, active.size)
        assertTrue(active.containsKey("current"))
    }

    private companion object {
        const val TTL = 50L
        const val NOW = 1_700_000_000_000L
    }
}