// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic coverage for [MarkerStore].
 *
 * Moved here with the class it tests. Every expiry case names its instant; the
 * ticker case still drives the scheduler, because that job is scheduling rather
 * than policy and is worth proving fires.
 */
final class MarkerStoreTest {

    private fun marker(
        uid: String,
        timestampMillis: Long = NOW,
        expiresAtMillis: Long? = null,
        callsign: String = "Hostile",
    ): CotMarker = CotMarker(
        uid = uid,
        type = "a-h-G",
        callsign = callsign,
        latitude = 44.9,
        longitude = -93.1,
        altitude = null,
        timestampMillis = timestampMillis,
        expiresAtMillis = expiresAtMillis,
    )

    @Test
    fun upsertPublishesByUid() {
        val store = MarkerStore(TestScope())
        store.upsert(marker("m-1"))

        val markers = store.observe().value
        assertEquals(1, markers.size)
        assertEquals("Hostile", markers["m-1"]?.callsign)
    }

    @Test
    fun sameUidOverwrites() {
        val store = MarkerStore(TestScope())
        store.upsert(marker("m-1", callsign = "Hostile"))
        store.upsert(marker("m-1", callsign = "RENAMED"))

        val markers = store.observe().value
        assertEquals(1, markers.size)
        assertEquals("RENAMED", markers["m-1"]?.callsign)
    }

    @Test
    fun distinctUidsAccumulate() {
        val store = MarkerStore(TestScope())
        store.upsert(marker("m-1"))
        store.upsert(marker("m-2", callsign = "Neutral"))

        assertEquals(2, store.observe().value.size)
    }

    @Test
    fun pruneDropsEntriesPastTheFallbackTtl() {
        val store = MarkerStore(TestScope())
        store.upsert(marker("old", timestampMillis = NOW - 25 * 60 * 60 * 1000L))

        // Past the 24h fallback, no producer truth involved.
        store.prune(NOW)
        assertTrue(store.observe().value.isEmpty())
    }

    @Test
    fun producerStaleOverridesTheFallbackTtl() {
        val store = MarkerStore(TestScope())
        store.upsert(marker("short", timestampMillis = NOW, expiresAtMillis = NOW + 1_000L))
        store.upsert(marker("long", timestampMillis = NOW, expiresAtMillis = NOW + 30L * 86_400_000L))

        // An instant that is past the fallback TTL for the first track, but the
        // sender said the second is good for a month.
        store.prune(NOW + 2 * 86_400_000L)

        val remaining = store.observe().value
        assertEquals(1, remaining.size)
        assertTrue("the sender-declared long track must survive", remaining.containsKey("long"))
    }

    @Test
    fun tickerPrunesStaleMarkers() = runTest {
        val store = MarkerStore(this)
        // Both timestamps are relative to the REAL clock, because the ticker
        // supplies the real clock. This case is about the job firing at all; the
        // two cases above pin the expiry instant exactly.
        val realNow = System.currentTimeMillis()
        store.upsert(marker("old", timestampMillis = realNow - 25 * 60 * 60 * 1000L))
        store.upsert(marker("fresh", timestampMillis = realNow))
        assertEquals(2, store.observe().value.size)

        advanceTimeBy(MarkerStore.PRUNE_INTERVAL_MILLIS + 1_000L)

        assertEquals(1, store.observe().value.size)
        assertTrue(store.observe().value.containsKey("fresh"))
        store.shutdown()
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}