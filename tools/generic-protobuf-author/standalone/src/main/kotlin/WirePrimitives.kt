// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream

data class KeyResult(val fieldNumber: Int, val wireType: Int, val nextOffset: Int)

object WirePrimitives {
    private const val MAX_FIELD_NUMBER = 536870911

    fun encodeVarint(value: ULong): ByteArray {
        val bits = value.toLong()
        val output = ByteArray(CodedOutputStream.computeUInt64SizeNoTag(bits))
        val stream = CodedOutputStream.newInstance(output)
        stream.writeUInt64NoTag(bits)
        stream.checkNoSpaceLeft()
        return output
    }

    fun decodeVarint(bytes: ByteArray, offset: Int = 0): Pair<ULong, Int> {
        require(offset >= 0 && offset < bytes.size) { "Invalid varint offset" }
        val available = minOf(10, bytes.size - offset)
        val snapshot = bytes.copyOfRange(offset, offset + available)
        var length = 0
        for (index in snapshot.indices) {
            val current = snapshot[index].toInt() and 0xff
            if (index == 9 && current > 1) {
                throw IllegalArgumentException("Varint exceeds 64 bits")
            }
            if ((current and 0x80) == 0) {
                length = index + 1
                break
            }
        }
        require(length != 0) { "Truncated or overlong varint" }
        var canonicalLength = length
        while (canonicalLength > 1 && (snapshot[canonicalLength - 1].toInt() and 0x7f) == 0) {
            canonicalLength--
        }
        val canonical = snapshot.copyOf(canonicalLength)
        canonical[canonical.lastIndex] = (canonical.last().toInt() and 0x7f).toByte()
        val stream = CodedInputStream.newInstance(canonical)
        val value = stream.readUInt64().toULong()
        check(stream.totalBytesRead == canonicalLength) { "Unexpected varint length" }
        return value to (offset + length)
    }

    fun encodeKey(fieldNumber: Int, wireType: Int): ByteArray {
        require(fieldNumber in 1..MAX_FIELD_NUMBER) { "Invalid field number" }
        require(wireType in 0..5) { "Invalid wire type" }
        return encodeVarint((fieldNumber.toULong() shl 3) or wireType.toULong())
    }

    fun decodeKey(bytes: ByteArray, offset: Int = 0): KeyResult {
        val (key, nextOffset) = decodeVarint(bytes, offset)
        val field = key shr 3
        val wire = (key and 7uL).toInt()
        require(field in 1uL..MAX_FIELD_NUMBER.toULong()) { "Invalid field number" }
        require(wire in 0..5) { "Invalid wire type" }
        return KeyResult(field.toInt(), wire, nextOffset)
    }
}
