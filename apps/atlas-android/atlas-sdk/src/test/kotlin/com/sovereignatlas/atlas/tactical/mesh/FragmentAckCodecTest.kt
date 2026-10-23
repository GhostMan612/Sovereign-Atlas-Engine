// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentAckCodecTest {

    @Test
    fun roundTripSingleReportExact() {
        val missing = booleanArrayOf(false, true, false, true, true, false, false, true)
        val report = FragmentAckReport(
            transferId = "xfer-101",
            round = 1,
            totalChunks = 16,
            contiguousHigh = 4,
            windowBase = 4,
            missing = missing,
        )

        val encoded = FragmentAckCodec.encode(listOf(report))
        val decoded = FragmentAckCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.size)
        val result = decoded[0]
        assertEquals("xfer-101", result.transferId)
        assertEquals(1, result.round)
        assertEquals(16, result.totalChunks)
        assertEquals(4, result.contiguousHigh)
        assertEquals(4, result.windowBase)
        assertArrayEquals(missing, result.missing)
    }

    @Test
    fun roundTripNonByteAlignedBitsPreservesExactSize() {
        val missing7 = booleanArrayOf(true, false, true, false, true, true, false)
        val report = FragmentAckReport(
            transferId = "short-xfer",
            round = 2,
            totalChunks = 7,
            contiguousHigh = 0,
            windowBase = 0,
            missing = missing7,
        )

        val encoded = FragmentAckCodec.encode(listOf(report))
        val decoded = FragmentAckCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.size)
        assertEquals(7, decoded[0].missing.size)
        assertArrayEquals(missing7, decoded[0].missing)
    }

    @Test
    fun roundTripMultipleReportsInBatch() {
        val r1 = FragmentAckReport(
            transferId = "batch-1",
            round = 1,
            totalChunks = 10,
            contiguousHigh = 3,
            windowBase = 3,
            missing = booleanArrayOf(false, true, false),
        )
        val r2 = FragmentAckReport(
            transferId = "batch-2",
            round = 3,
            totalChunks = 20,
            contiguousHigh = 15,
            windowBase = 15,
            missing = booleanArrayOf(true, true),
        )

        val encoded = FragmentAckCodec.encode(listOf(r1, r2))
        val decoded = FragmentAckCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(2, decoded!!.size)
        assertEquals("batch-1", decoded[0].transferId)
        assertEquals("batch-2", decoded[1].transferId)
    }

    @Test
    fun rejectsMalformedMagic() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x00)
        val decoded = FragmentAckCodec.decode(bytes)
        assertNull(decoded)
    }

    @Test
    fun rejectsTruncatedBytes() {
        val r = FragmentAckReport(
            transferId = "trunc",
            round = 1,
            totalChunks = 5,
            contiguousHigh = 0,
            windowBase = 0,
            missing = booleanArrayOf(true),
        )
        val encoded = FragmentAckCodec.encode(listOf(r))
        val truncated = encoded.copyOfRange(0, encoded.size - 2)
        val decoded = FragmentAckCodec.decode(truncated)
        assertNull(decoded)
    }
}
