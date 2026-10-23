// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import java.io.ByteArrayOutputStream

data class FrameDecodeResult(
    val frames: List<ByteArray>,
    val remainingBytes: ByteArray,
)

object MeshtasticFrameCodec {
    const val SYNC_1: Byte = 0x94.toByte()
    const val SYNC_2: Byte = 0xC3.toByte()
    const val HEADER_LEN: Int = 4
    const val MAX_FRAME_LEN: Int = 512

    fun encodeFrame(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_FRAME_LEN) {
            "Payload size ${payload.size} exceeds maximum Meshtastic frame length $MAX_FRAME_LEN"
        }
        val out = ByteArray(HEADER_LEN + payload.size)
        out[0] = SYNC_1
        out[1] = SYNC_2
        out[2] = ((payload.size ushr 8) and 0xFF).toByte()
        out[3] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, out, HEADER_LEN, payload.size)
        return out
    }

    fun decodeStream(buffer: ByteArray): FrameDecodeResult {
        if (buffer.size < HEADER_LEN) {
            return FrameDecodeResult(emptyList(), buffer.copyOf())
        }

        val frames = ArrayList<ByteArray>()
        var offset = 0
        val len = buffer.size

        while (offset <= len - HEADER_LEN) {
            if (buffer[offset] == SYNC_1 && buffer[offset + 1] == SYNC_2) {
                val payloadLen = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                    (buffer[offset + 3].toInt() and 0xFF)

                if (payloadLen < 0 || payloadLen > MAX_FRAME_LEN) {
                    offset += 2
                    continue
                }

                val frameEnd = offset + HEADER_LEN + payloadLen
                if (frameEnd <= len) {
                    val payload = buffer.copyOfRange(offset + HEADER_LEN, frameEnd)
                    frames.add(payload)
                    offset = frameEnd
                } else {
                    break
                }
            } else {
                offset++
            }
        }

        val remaining = if (offset < len) {
            buffer.copyOfRange(offset, len)
        } else {
            ByteArray(0)
        }

        return FrameDecodeResult(frames, remaining)
    }

    fun isValidFrame(frame: ByteArray): Boolean {
        if (frame.size < HEADER_LEN) return false
        if (frame[0] != SYNC_1 || frame[1] != SYNC_2) return false
        val payloadLen = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
        return frame.size == HEADER_LEN + payloadLen && payloadLen <= MAX_FRAME_LEN
    }
}
