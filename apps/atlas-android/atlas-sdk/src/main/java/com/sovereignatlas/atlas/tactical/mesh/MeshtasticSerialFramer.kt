// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

class MeshtasticSerialFramer(
    private val maxPayloadBytes: Int = MAX_SERIAL_PAYLOAD_BYTES,
) {
    private val buffer = ArrayList<Byte>(maxPayloadBytes + 8)
    var droppedCorruptFrames: Long = 0L
        private set

    fun feed(chunk: ByteArray, length: Int = chunk.size): List<ByteArray> {
        if (length <= 0 || chunk.isEmpty()) return emptyList()
        val safeLen = length.coerceAtMost(chunk.size)
        for (i in 0 until safeLen) {
            buffer.add(chunk[i])
        }
        val frames = ArrayList<ByteArray>()
        while (true) {
            val startIdx = findMagicIndex()
            if (startIdx < 0) {
                val keepLastMagic = buffer.isNotEmpty() && buffer.last() == START_BYTE_1
                buffer.clear()
                if (keepLastMagic) {
                    buffer.add(START_BYTE_1)
                }
                break
            }
            if (startIdx > 0) {
                discardPrefix(startIdx)
            }
            if (buffer.size < HEADER_BYTES) break

            val lenMsb = buffer[2].toInt() and 0xFF
            val lenLsb = buffer[3].toInt() and 0xFF
            val payloadLength = (lenMsb shl 8) or lenLsb

            if (payloadLength <= 0 || payloadLength > maxPayloadBytes) {
                droppedCorruptFrames++
                discardPrefix(2)
                continue
            }

            val totalFrameBytes = HEADER_BYTES + payloadLength
            if (buffer.size < totalFrameBytes) break

            val payload = ByteArray(payloadLength)
            for (i in 0 until payloadLength) {
                payload[i] = buffer[HEADER_BYTES + i]
            }
            frames.add(payload)
            discardPrefix(totalFrameBytes)
        }
        return frames
    }

    fun reset() {
        buffer.clear()
    }

    private fun findMagicIndex(): Int {
        for (i in 0 until buffer.size - 1) {
            if (buffer[i] == START_BYTE_1 && buffer[i + 1] == START_BYTE_2) {
                return i
            }
        }
        return -1
    }

    private fun discardPrefix(count: Int) {
        if (count <= 0) return
        if (count >= buffer.size) {
            buffer.clear()
            return
        }
        buffer.subList(0, count).clear()
    }

    companion object {
        const val START_BYTE_1: Byte = 0x94.toByte()
        const val START_BYTE_2: Byte = 0xC3.toByte()
        const val HEADER_BYTES: Int = 4
        const val MAX_SERIAL_PAYLOAD_BYTES: Int = 512
        val WAKE_PREAMBLE: ByteArray = ByteArray(32) { START_BYTE_2 }

        fun frame(payload: ByteArray): ByteArray {
            require(payload.isNotEmpty() && payload.size <= MAX_SERIAL_PAYLOAD_BYTES) {
                "Meshtastic serial payload must be 1..$MAX_SERIAL_PAYLOAD_BYTES bytes (got ${payload.size})"
            }
            val framed = ByteArray(HEADER_BYTES + payload.size)
            framed[0] = START_BYTE_1
            framed[1] = START_BYTE_2
            framed[2] = ((payload.size ushr 8) and 0xFF).toByte()
            framed[3] = (payload.size and 0xFF).toByte()
            System.arraycopy(payload, 0, framed, HEADER_BYTES, payload.size)
            return framed
        }
    }
}
