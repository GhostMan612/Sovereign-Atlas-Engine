// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryBackedMalformedTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun tenthByteOverflowCannotBeSilentlyTruncated() {
        val overflow = bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x02)
        assertThrows(IllegalArgumentException::class.java) { WirePrimitives.decodeVarint(overflow) }
        assertThrows(IllegalArgumentException::class.java) { WirePrimitives.decodeKey(overflow) }
    }

    @Test
    fun unterminatedTenthByteIsRejectedEvenWithTrailingTerminator() {
        val oversized = bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0)
        assertThrows(IllegalArgumentException::class.java) { WirePrimitives.decodeVarint(oversized) }
    }

    @Test
    fun uint64BitPatternAndFollowingBytesRemainDistinct() {
        val encoded = WirePrimitives.encodeVarint(ULong.MAX_VALUE)
        assertEquals(10, encoded.size)
        val appended = bytes(0x37) + encoded + bytes(0x12)
        assertEquals(ULong.MAX_VALUE to 11, WirePrimitives.decodeVarint(appended, 1))
        assertEquals(0x12, appended[11].toInt() and 0xff)
    }

    @Test
    fun keysAreValidatedBeforeUnsignedValuesAreNarrowed() {
        val invalid = WirePrimitives.encodeVarint(ULong.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { WirePrimitives.decodeKey(invalid) }
        assertThrows(IllegalArgumentException::class.java) {
            WirePrimitives.decodeKey(bytes(0x88, 0x80, 0x80, 0x80, 0x10))
        }
        val valid = WirePrimitives.encodeKey(536870911, 5)
        assertArrayEquals(bytes(0xfd, 0xff, 0xff, 0xff, 0x0f), valid)
        assertEquals(KeyResult(536870911, 5, valid.size), WirePrimitives.decodeKey(valid))
    }

    @Test
    fun overlongValidKeysPreserveInputBoundaryAndAcceptReservedSchemaNumber() {
        val paddedKey = bytes(0x88, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x00, 0x55)
        assertEquals(KeyResult(1, 0, 10), WirePrimitives.decodeKey(paddedKey))
        assertEquals(0x55, paddedKey[10].toInt())
        assertEquals(19000, WirePrimitives.decodeKey(WirePrimitives.encodeKey(19000, 0)).fieldNumber)
    }

    @Test
    fun tenByteNonminimalValuesRetainAllLowerPayloadGroups() {
        val zero = bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x00)
        assertEquals(0uL to 10, WirePrimitives.decodeVarint(zero))
        val lower63 = bytes(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00)
        assertEquals(((1uL shl 63) - 1uL) to 10, WirePrimitives.decodeVarint(lower63))
    }
}
