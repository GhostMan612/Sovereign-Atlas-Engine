// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

class IndependentAcceptanceTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun rejected(operation: () -> Unit) {
        try {
            operation()
        } catch (error: Exception) {
            assertFalse("Malformed input caused an uncontrolled exception: $error", error is IndexOutOfBoundsException || error is ArithmeticException || error is NegativeArraySizeException || error is ClassCastException)
            return
        }
        fail("Expected controlled rejection")
    }

    @Test
    fun canonicalUnsignedVarintVectors() {
        val vectors = listOf(
            0uL to bytes(0x00),
            1uL to bytes(0x01),
            127uL to bytes(0x7f),
            128uL to bytes(0x80, 0x01),
            150uL to bytes(0x96, 0x01),
            300uL to bytes(0xac, 0x02),
            16383uL to bytes(0xff, 0x7f),
            16384uL to bytes(0x80, 0x80, 0x01),
            4294967295uL to bytes(0xff, 0xff, 0xff, 0xff, 0x0f),
            (1uL shl 63) to bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x01),
            ULong.MAX_VALUE to bytes(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x01),
        )
        for ((value, expected) in vectors) {
            assertArrayEquals(expected, WirePrimitives.encodeVarint(value))
            val (actual, offset) = WirePrimitives.decodeVarint(expected)
            assertEquals(value, actual)
            assertEquals(expected.size, offset)
        }
    }

    @Test
    fun nonminimalTerminatingVarintsAreAccepted() {
        assertEquals(0uL to 2, WirePrimitives.decodeVarint(bytes(0x80, 0x00)))
        assertEquals(127uL to 2, WirePrimitives.decodeVarint(bytes(0xff, 0x00)))
        assertEquals(1uL to 10, WirePrimitives.decodeVarint(bytes(0x81, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x00)))
    }

    @Test
    fun malformedVarintsAreRejected() {
        val malformed = listOf(
            bytes(),
            bytes(0x80),
            bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x02),
            bytes(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x02),
            bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80),
            bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x00),
        )
        for (input in malformed) rejected { WirePrimitives.decodeVarint(input) }
    }

    @Test
    fun invalidOffsetsAreControlledErrors() {
        rejected { WirePrimitives.decodeVarint(bytes(0x01), -1) }
        rejected { WirePrimitives.decodeVarint(bytes(0x01), 1) }
        rejected { WirePrimitives.decodeVarint(bytes(0x01), Int.MAX_VALUE) }
        rejected { WirePrimitives.decodeKey(bytes(0x08), -1) }
        rejected { WirePrimitives.decodeKey(bytes(0x08), 1) }
    }

    @Test
    fun fieldOneValue150IsEncodedAsOneKeyAndOneValue() {
        val message = WirePrimitives.encodeKey(1, 0) + WirePrimitives.encodeVarint(150uL)
        assertArrayEquals(bytes(0x08, 0x96, 0x01), message)
        val padded = bytes(0x55) + message + bytes(0x7f)
        val key = WirePrimitives.decodeKey(padded, 1)
        assertEquals(1, key.fieldNumber)
        assertEquals(0, key.wireType)
        assertEquals(2, key.nextOffset)
        val (value, end) = WirePrimitives.decodeVarint(padded, key.nextOffset)
        assertEquals(150uL, value)
        assertEquals(4, end)
        assertEquals(0x7f, padded[end].toInt() and 0xff)
    }

    @Test
    fun keysHaveCorrectWireTypesAndMaximalFieldNumber() {
        for (type in 0..5) {
            val encoded = WirePrimitives.encodeKey(1, type)
            assertArrayEquals(bytes(0x08 or type), encoded)
            val decoded = WirePrimitives.decodeKey(encoded)
            assertEquals(1, decoded.fieldNumber)
            assertEquals(type, decoded.wireType)
            assertEquals(encoded.size, decoded.nextOffset)
        }
        val max = WirePrimitives.encodeKey(536870911, 5)
        assertArrayEquals(bytes(0xfd, 0xff, 0xff, 0xff, 0x0f), max)
        val decoded = WirePrimitives.decodeKey(max)
        assertEquals(536870911, decoded.fieldNumber)
        assertEquals(5, decoded.wireType)
        assertEquals(5, decoded.nextOffset)
        assertEquals(19000, WirePrimitives.decodeKey(WirePrimitives.encodeKey(19000, 0)).fieldNumber)
    }

    @Test
    fun invalidFieldNumbersAndWireTypesAreRejected() {
        for (field in listOf(-1, 0, 536870912, Int.MAX_VALUE)) {
            rejected { WirePrimitives.encodeKey(field, 0) }
        }
        for (type in listOf(-1, 6, 7, 8, Int.MAX_VALUE)) {
            rejected { WirePrimitives.encodeKey(1, type) }
        }
        for (input in listOf(bytes(), bytes(0x00), bytes(0x0e), bytes(0x0f), bytes(0x80), bytes(0x80, 0x80, 0x80, 0x80, 0x10))) {
            rejected { WirePrimitives.decodeKey(input) }
        }
    }

    @Test
    fun deterministicRoundTripsAcross64BitRange() {
        val random = Random(0x501b0fL)
        repeat(5000) {
            val value = random.nextLong().toULong()
            val encoded = WirePrimitives.encodeVarint(value)
            assertFalse(encoded.isEmpty() || encoded.size > 10)
            val padded = bytes(0x55) + encoded + bytes(0x7f)
            val decoded = WirePrimitives.decodeVarint(padded, 1)
            assertEquals(value, decoded.first)
            assertEquals(1 + encoded.size, decoded.second)
            assertEquals(0x7f, padded[decoded.second].toInt() and 0xff)
        }
    }
}
