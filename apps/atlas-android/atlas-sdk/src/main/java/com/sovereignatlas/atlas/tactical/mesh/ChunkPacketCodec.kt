// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

/**
 * CHK1 is the existing eight-byte fragment header followed by a UTF-8 ID
 * and fragment bytes. All lengths are checked in *bytes*, never characters.
 *
 * Version 1 encodes its chunk index and count as unsigned bytes (1..255).
 * Changing those limits requires a new protocol version, not truncation.
 */
data class ChunkPacket(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray,
)

object ChunkPacketCodec {
    const val MAX_WIRE_BYTES: Int = 237
    const val HEADER_BYTES: Int = 8
    const val MAX_TRANSFER_ID_BYTES: Int = 120
    const val MAX_CHUNKS: Int = 255

    private val magic = byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())

    fun hasMagic(bytes: ByteArray): Boolean =
        bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    fun maxDataBytes(transferId: String): Int {
        val idSize = transferId.toByteArray(Charsets.UTF_8).size
        if (idSize !in 1..MAX_TRANSFER_ID_BYTES) return 0
        return MAX_WIRE_BYTES - HEADER_BYTES - idSize
    }

    fun encode(transferId: String, chunkIndex: Int, totalChunks: Int, data: ByteArray): ByteArray? {
        if (totalChunks !in 1..MAX_CHUNKS || chunkIndex !in 0 until totalChunks) return null
        val idBytes = transferId.toByteArray(Charsets.UTF_8)
        if (idBytes.size !in 1..MAX_TRANSFER_ID_BYTES) return null
        if (data.isEmpty() || data.size > MAX_WIRE_BYTES - HEADER_BYTES - idBytes.size) return null
        val frame = ByteArray(HEADER_BYTES + idBytes.size + data.size)
        magic.copyInto(frame)
        frame[4] = idBytes.size.toByte()
        frame[5] = chunkIndex.toByte()
        frame[6] = totalChunks.toByte()
        frame[7] = 0
        idBytes.copyInto(frame, HEADER_BYTES)
        data.copyInto(frame, HEADER_BYTES + idBytes.size)
        return frame
    }

    fun decode(frame: ByteArray): ChunkPacket? {
        if (!hasMagic(frame) || frame.size <= HEADER_BYTES || frame.size > MAX_WIRE_BYTES) return null
        val idSize = frame[4].toInt() and 0xFF
        val chunkIndex = frame[5].toInt() and 0xFF
        val totalChunks = frame[6].toInt() and 0xFF
        if (idSize !in 1..MAX_TRANSFER_ID_BYTES || totalChunks !in 1..MAX_CHUNKS ||
            chunkIndex !in 0 until totalChunks || frame[7] != 0.toByte() ||
            frame.size <= HEADER_BYTES + idSize
        ) return null
        val idBytes = frame.copyOfRange(HEADER_BYTES, HEADER_BYTES + idSize)
        val transferId = String(idBytes, Charsets.UTF_8)
        // The default String decoder replaces malformed UTF-8. Reject it
        // instead of permitting two different wire IDs to alias.
        if (!idBytes.contentEquals(transferId.toByteArray(Charsets.UTF_8))) return null
        return ChunkPacket(
            transferId = transferId,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            data = frame.copyOfRange(HEADER_BYTES + idSize, frame.size),
        )
    }
}
