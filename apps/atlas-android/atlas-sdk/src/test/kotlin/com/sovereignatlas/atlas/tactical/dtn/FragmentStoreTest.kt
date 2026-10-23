// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.dtn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentStoreTest {

    @Test
    fun inboundOutOfOrderAssembly() {
        var simulatedTime = 1_000_000L
        val store = FragmentStore(clock = { simulatedTime })

        val chunk0 = "HELLO ".toByteArray(Charsets.UTF_8)
        val chunk1 = "SOVEREIGN ".toByteArray(Charsets.UTF_8)
        val chunk2 = "MESH".toByteArray(Charsets.UTF_8)

        val r1 = store.registerInbound("msg-1", 1, 3, FragmentTier.PUBLIC, "node-1", 0L, chunk1)
        assertEquals(RegisterOutcome.STORED, r1)
        assertFalse(store.isInboundComplete("msg-1"))
        assertNull(store.assembleInboundPayload("msg-1"))

        val r0 = store.registerInbound("msg-1", 0, 3, FragmentTier.PUBLIC, "node-1", 0L, chunk0)
        assertEquals(RegisterOutcome.STORED, r0)
        assertFalse(store.isInboundComplete("msg-1"))

        val r2 = store.registerInbound("msg-1", 2, 3, FragmentTier.PUBLIC, "node-1", 0L, chunk2)
        assertEquals(RegisterOutcome.STORED, r2)
        assertTrue(store.isInboundComplete("msg-1"))

        val fullPayload = store.assembleInboundPayload("msg-1")
        assertNotNull(fullPayload)
        assertEquals("HELLO SOVEREIGN MESH", String(fullPayload!!, Charsets.UTF_8))
    }

    @Test
    fun duplicateAndMismatchRejection() {
        val store = FragmentStore()
        val chunk = "DATA".toByteArray()

        assertEquals(RegisterOutcome.STORED, store.registerInbound("t1", 0, 2, FragmentTier.PUBLIC, "peer", 0L, chunk))
        assertEquals(RegisterOutcome.DUPLICATE, store.registerInbound("t1", 0, 2, FragmentTier.PUBLIC, "peer", 0L, chunk))
        assertEquals(RegisterOutcome.TOTAL_CHUNKS_MISMATCH, store.registerInbound("t1", 1, 3, FragmentTier.PUBLIC, "peer", 0L, chunk))
        assertEquals(RegisterOutcome.ALREADY_RESOLVED, store.registerInbound("t1", 1, 2, FragmentTier.PUBLIC, "peer", 0L, chunk))
    }

    @Test
    fun stallTriggersNackReport() {
        var now = 10_000L
        val store = FragmentStore(clock = { now })

        store.registerInbound("t-stall", 0, 4, FragmentTier.PUBLIC, "peer", 0L, "C0".toByteArray(), now = now)
        store.registerInbound("t-stall", 2, 4, FragmentTier.PUBLIC, "peer", 0L, "C2".toByteArray(), now = now)

        val immediateReports = store.tick(now)
        assertTrue(immediateReports.isEmpty())

        now += 5_000L
        val stalledReports = store.tick(now)
        assertEquals(1, stalledReports.size)
        val report = stalledReports[0]
        assertEquals("t-stall", report.transferId)
        assertEquals(1, report.round)
        assertEquals(4, report.totalChunks)
        assertEquals(1, report.contiguousHigh)
        assertEquals(1, report.windowBase)
        assertArrayEquals(booleanArrayOf(true, false, true), report.missing)
    }

    @Test
    fun outboundStorageAndRepairProcessing() {
        val store = FragmentStore()
        val c0 = "A".toByteArray()
        val c1 = "B".toByteArray()
        val c2 = "C".toByteArray()

        store.registerOutbound("out-1", 3, FragmentTier.DIRECT, "peer-dest", 0L, listOf(c0, c1, c2))
        assertTrue(store.isOutboundComplete("out-1"))

        val report = com.sovereignatlas.atlas.tactical.mesh.FragmentAckReport(
            transferId = "out-1",
            round = 1,
            totalChunks = 3,
            contiguousHigh = 1,
            windowBase = 1,
            missing = booleanArrayOf(true, false),
        )

        val repairs = store.processAckReport(report)
        assertEquals(1, repairs.size)
        assertArrayEquals(c1, repairs[0])
    }

    @Test
    fun sweepEvictsExpiredTransfers() {
        var now = 100_000L
        val store = FragmentStore(clock = { now })

        store.registerInbound("exp-1", 0, 2, FragmentTier.PUBLIC, "p", expiresAt = 105_000L, "X".toByteArray())
        store.registerInbound("live-1", 0, 2, FragmentTier.PUBLIC, "p", expiresAt = 120_000L, "Y".toByteArray())

        now = 110_000L
        store.sweep(now)

        assertEquals(listOf(1), store.getMissingChunkIndices("live-1"))
        assertTrue(store.getMissingChunkIndices("exp-1").isEmpty())
    }
}
