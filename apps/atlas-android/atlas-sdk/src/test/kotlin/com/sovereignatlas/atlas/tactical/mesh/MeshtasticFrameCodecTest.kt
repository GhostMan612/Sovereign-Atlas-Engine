// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshtasticFrameCodecTest {

    @Test
    fun encodeFrameGeneratesValidMeshtasticHeader() {
        val payload = "COTPACKET".toByteArray(Charsets.UTF_8)
        val frame = MeshtasticFrameCodec.encodeFrame(payload)

        assertEquals(4 + payload.size, frame.size)
        assertEquals(0x94.toByte(), frame[0])
        assertEquals(0xC3.toByte(), frame[1])
        assertEquals(0x00.toByte(), frame[2])
        assertEquals(payload.size.toByte(), frame[3])
        assertTrue(MeshtasticFrameCodec.isValidFrame(frame))
    }

    @Test
    fun decodeStreamParsesCompleteAndFragmentedFrames() {
        val f1 = MeshtasticFrameCodec.encodeFrame("FRAME1".toByteArray())
        val f2 = MeshtasticFrameCodec.encodeFrame("FRAME2".toByteArray())
        val combined = f1 + f2

        val result = MeshtasticFrameCodec.decodeStream(combined)
        assertEquals(2, result.frames.size)
        assertArrayEquals("FRAME1".toByteArray(), result.frames[0])
        assertArrayEquals("FRAME2".toByteArray(), result.frames[1])
        assertEquals(0, result.remainingBytes.size)

        val partial = f1 + f2.copyOfRange(0, 3)
        val partialResult = MeshtasticFrameCodec.decodeStream(partial)
        assertEquals(1, partialResult.frames.size)
        assertArrayEquals("FRAME1".toByteArray(), partialResult.frames[0])
        assertEquals(3, partialResult.remainingBytes.size)
    }

    @Test
    fun decodeStreamResynchronizesAfterGarbagePrefix() {
        val noise = byteArrayOf(0x01, 0x02, 0x94.toByte(), 0x03, 0xFF.toByte())
        val valid = MeshtasticFrameCodec.encodeFrame("RECOVERED".toByteArray())
        val buffer = noise + valid

        val result = MeshtasticFrameCodec.decodeStream(buffer)
        assertEquals(1, result.frames.size)
        assertArrayEquals("RECOVERED".toByteArray(), result.frames[0])
    }
}
