// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

final class PliBroadcastSchedulerTest {
    @Test
    fun firstCheckAlwaysFires() {
        val scheduler = PliBroadcastScheduler(timeProvider = { 1_000L })
        assertTrue(scheduler.shouldBroadcast(44.9, -93.1))
    }

    @Test
    fun sixtySecondLockSuppressesRepeat() {
        var now = 100_000L
        val scheduler = PliBroadcastScheduler(timeProvider = { now })
        assertTrue(scheduler.shouldBroadcast(44.9, -93.1))
        now += 30_000L
        assertFalse(scheduler.shouldBroadcast(44.9, -93.1))
        now += 30_000L
        assertTrue(scheduler.shouldBroadcast(44.9, -93.1))
    }

    @Test
    fun movementBeyondFiftyMetersAccelerates() {
        var now = 200_000L
        val scheduler = PliBroadcastScheduler(timeProvider = { now })
        assertTrue(scheduler.shouldBroadcast(44.9, -93.1))
        now += 10_000L
        assertFalse(scheduler.shouldBroadcast(44.9, -93.1))
        now += 10_000L
        assertTrue(scheduler.shouldBroadcast(44.9006, -93.1))
    }

    @Test
    fun smallDriftDoesNotFire() {
        var now = 300_000L
        val scheduler = PliBroadcastScheduler(timeProvider = { now })
        assertTrue(scheduler.shouldBroadcast(44.9, -93.1))
        now += 10_000L
        assertFalse(scheduler.shouldBroadcast(44.9001, -93.1))
    }
}
