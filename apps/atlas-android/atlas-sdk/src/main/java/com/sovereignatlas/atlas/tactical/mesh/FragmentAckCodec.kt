// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import java.io.ByteArrayOutputStream

data class FragmentAckReport(
    val transferId: String,
    val round: Int,
    val totalChunks: Int,
    val contiguousHigh: Int,
    val windowBase: Int,
    val missing: BooleanArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FragmentAckReport) return false
        return transferId == other.transferId &&
            round == other.round &&
            totalChunks == other.totalChunks &&
            contiguousHigh == other.contiguousHigh &&
            windowBase == other.windowBase &&
            missing.contentEquals(other.missing)
    }

    override fun hashCode(): Int {
        var result = transferId.hashCode()
        result = 31 * result + round
        result = 31 * result + totalChunks
        result = 31 * result + contiguousHigh
        result = 31 * result + windowBase
        result = 31 * result + missing.contentHashCode()
        return result
    }
}

object FragmentAckCodec {
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'R'.code.toByte(), 'A'.code.toByte(), '1'.code.toByte())

    const val MAX_WINDOW_BITS: Int = 8192
    const val MAX_REPORTS: Int = 0xFF

    fun encode(reports: List<FragmentAckReport>): ByteArray {
        require(reports.size <= MAX_REPORTS) { "cannot encode ${reports.size} reports in one batch" }
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(reports.size)
        for (r in reports) {
            require(r.missing.size <= MAX_WINDOW_BITS) { "report window of ${r.missing.size} bits exceeds cap" }
            val idBytes = r.transferId.toByteArray(Charsets.UTF_8)
            require(idBytes.size <= 0xFF) { "transferId too long to encode: ${r.transferId}" }
            out.write(idBytes.size)
            out.write(idBytes)
            writeU16BE(out, r.round)
            writeI32BE(out, r.totalChunks)
            writeI32BE(out, r.contiguousHigh)
            writeI32BE(out, r.windowBase)
            writeU16BE(out, r.missing.size)
            out.write(packBits(r.missing))
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): List<FragmentAckReport>? {
        if (bytes.size < 5) return null
        if (bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1] || bytes[2] != MAGIC[2] || bytes[3] != MAGIC[3]) return null
        val count = bytes[4].toInt() and 0xFF
        var o = 5
        val reports = ArrayList<FragmentAckReport>(count)
        repeat(count) {
            if (o + 1 > bytes.size) return null
            val idLen = bytes[o].toInt() and 0xFF
            o += 1
            if (idLen <= 0 || o + idLen > bytes.size) return null
            val transferId = String(bytes, o, idLen, Charsets.UTF_8)
            o += idLen
            if (o + 2 > bytes.size) return null
            val round = readU16BE(bytes, o)
            o += 2
            if (o + 4 > bytes.size) return null
            val totalChunks = readI32BE(bytes, o)
            o += 4
            if (totalChunks < 0 || totalChunks > MAX_WINDOW_BITS) return null
            if (o + 4 > bytes.size) return null
            val contiguousHigh = readI32BE(bytes, o)
            o += 4
            if (contiguousHigh < 0 || contiguousHigh > totalChunks) return null
            if (o + 4 > bytes.size) return null
            val windowBase = readI32BE(bytes, o)
            o += 4
            if (windowBase < 0 || windowBase > totalChunks) return null
            if (o + 2 > bytes.size) return null
            val bitCount = readU16BE(bytes, o)
            o += 2
            if (bitCount < 0 || bitCount > MAX_WINDOW_BITS) return null
            val bitmapLen = (bitCount + 7) / 8
            if (o + bitmapLen > bytes.size) return null
            val bitmapBytes = bytes.copyOfRange(o, o + bitmapLen)
            o += bitmapLen
            reports.add(
                FragmentAckReport(
                    transferId = transferId,
                    round = round,
                    totalChunks = totalChunks,
                    contiguousHigh = contiguousHigh,
                    windowBase = windowBase,
                    missing = unpackBits(bitmapBytes, bitCount),
                )
            )
        }
        return reports
    }

    private fun packBits(bits: BooleanArray): ByteArray {
        val out = ByteArray((bits.size + 7) / 8)
        for (i in bits.indices) {
            if (bits[i]) {
                out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
            }
        }
        return out
    }

    private fun unpackBits(bytes: ByteArray, bitCount: Int): BooleanArray =
        BooleanArray(bitCount) { i -> (bytes[i / 8].toInt() shr (7 - i % 8)) and 1 == 1 }

    private fun writeU16BE(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 8 and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readU16BE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun writeI32BE(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 24 and 0xFF)
        out.write(value ushr 16 and 0xFF)
        out.write(value ushr 8 and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readI32BE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
