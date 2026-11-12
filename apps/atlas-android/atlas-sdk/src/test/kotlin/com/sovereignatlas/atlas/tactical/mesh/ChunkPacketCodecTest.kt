// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkPacketCodecTest {

    @Test
    fun boundaryPayloadFitsExactly237Bytes() {
        val id = "cot-123"
        val data = ByteArray(ChunkPacketCodec.maxDataBytes(id)) { it.toByte() }
        val wire = ChunkPacketCodec.encode(id, 0, 1, data)
        assertNotNull(wire)
        assertEquals(ChunkPacketCodec.MAX_WIRE_BYTES, wire!!.size)
        val parsed = ChunkPacketCodec.decode(wire)!!
        assertEquals(id, parsed.transferId)
        assertEquals(0, parsed.chunkIndex)
        assertEquals(1, parsed.totalChunks)
        assertArrayEquals(data, parsed.data)
        assertNull(ChunkPacketCodec.encode(id, 0, 1, data + byteArrayOf(0)))
    }

    @Test
    fun rejectsIndexCountAndIdentifierOverflowWithoutTruncation() {
        val payload = byteArrayOf(7)
        assertNotNull(ChunkPacketCodec.encode("x", 254, 255, payload))
        assertNull(ChunkPacketCodec.encode("x", 255, 255, payload))
        assertNull(ChunkPacketCodec.encode("x", 0, 256, payload))
        assertNull(ChunkPacketCodec.encode("x", 0, 0, payload))
        assertNull(ChunkPacketCodec.encode("x", 0, 1, byteArrayOf()))
        assertNull(ChunkPacketCodec.encode("é".repeat(61), 0, 1, payload))
        assertNull(ChunkPacketCodec.encode("x".repeat(256), 0, 1, payload))
        assertNull(ChunkPacketCodec.encode("", 0, 1, payload))
    }

    @Test
    fun malformedWirePacketsFailClosed() {
        val base = ChunkPacketCodec.encode("safe", 0, 2, byteArrayOf(4))!!
        val badIndex = base.copyOf().also { it[5] = 2 }
        val badCount = base.copyOf().also { it[6] = 0 }
        val badFlags = base.copyOf().also { it[7] = 1 }
        val longId = base.copyOf().also { it[4] = 121.toByte() }
        val malformedUtf8 = ChunkPacketCodec.encode("é", 0, 1, byteArrayOf(1))!!
            .also { it[8] = 0xFF.toByte() }
        assertNull(ChunkPacketCodec.decode(base.copyOfRange(0, 8)))
        assertNull(ChunkPacketCodec.decode(badIndex))
        assertNull(ChunkPacketCodec.decode(badCount))
        assertNull(ChunkPacketCodec.decode(badFlags))
        assertNull(ChunkPacketCodec.decode(longId))
        assertNull(ChunkPacketCodec.decode(malformedUtf8))
        assertNull(ChunkPacketCodec.decode(base + ByteArray(ChunkPacketCodec.MAX_WIRE_BYTES)))
        assertFalse(ChunkPacketCodec.hasMagic(byteArrayOf(0, 1, 2, 3)))
        assertTrue(ChunkPacketCodec.hasMagic(base))
    }
}
