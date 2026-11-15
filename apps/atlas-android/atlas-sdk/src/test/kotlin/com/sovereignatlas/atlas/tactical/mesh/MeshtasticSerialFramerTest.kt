// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshtasticSerialFramerTest {

    @Test
    fun reassemblesSingleByteFragmentsMultipleFramesAndRecoversFromGarbagePrefix() {
        val framer = MeshtasticSerialFramer()
        val payloadA = byteArrayOf(0x18, 0x2A, 0x01, 0x02)
        val payloadB = "MESHTASTIC".encodeToByteArray()

        val wireStream = MeshtasticSerialFramer.WAKE_PREAMBLE +
            byteArrayOf(0x00, 0x94.toByte(), 0x12) +
            MeshtasticSerialFramer.frame(payloadA) +
            byteArrayOf(0xFF.toByte()) +
            MeshtasticSerialFramer.frame(payloadB)

        val collected = ArrayList<ByteArray>()
        for (b in wireStream) {
            collected.addAll(framer.feed(byteArrayOf(b)))
        }

        assertEquals(2, collected.size)
        assertArrayEquals(payloadA, collected[0])
        assertArrayEquals(payloadB, collected[1])
    }

    @Test
    fun corruptLengthHeadersIncrementDropCountAndResyncCleanly() {
        val framer = MeshtasticSerialFramer()
        val payload = byteArrayOf(0x08, 0x01)
        val corruptZero = byteArrayOf(0x94.toByte(), 0xC3.toByte(), 0x00, 0x00)
        val corruptHuge = byteArrayOf(0x94.toByte(), 0xC3.toByte(), 0x03, 0x00)
        val combined = corruptZero + corruptHuge + MeshtasticSerialFramer.frame(payload)

        val out = framer.feed(combined)
        assertEquals(1, out.size)
        assertArrayEquals(payload, out.single())
        assertEquals(2L, framer.droppedCorruptFrames)

        framer.reset()
        assertTrue(framer.feed(byteArrayOf(0x94.toByte())).isEmpty())
    }
}
