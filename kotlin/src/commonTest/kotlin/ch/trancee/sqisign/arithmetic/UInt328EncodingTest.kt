// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UInt328EncodingTest {
    @Test
    fun everyBitSurvivesLittleEndianRoundTrip() {
        for (bit in 0 until 328) {
            val encoded = ByteArray(UInt328.ENCODED_BYTES)
            encoded[bit / 8] = (1 shl (bit % 8)).toByte()

            val output = UInt328.fromLittleEndian(encoded).toLittleEndian()

            assertContentEquals(encoded, output, "Bit $bit")
        }
    }

    @Test
    fun maximumValueUsesEveryEncodingBit() {
        val encoded = ByteArray(UInt328.ENCODED_BYTES) { -1 }

        val output = UInt328.fromLittleEndian(encoded).toLittleEndian()

        assertContentEquals(encoded, output)
    }

    @Test
    fun zeroRoundTripsAtExactWidth() {
        val encoded = ByteArray(UInt328.ENCODED_BYTES)

        val output = UInt328.fromLittleEndian(encoded).toLittleEndian()

        assertContentEquals(encoded, output)
    }

    @Test
    fun importDoesNotRetainCallerStorage() {
        val encoded = ByteArray(UInt328.ENCODED_BYTES) { it.toByte() }
        val expected = encoded.copyOf()
        val value = UInt328.fromLittleEndian(encoded)

        encoded.fill(0)

        assertContentEquals(expected, value.toLittleEndian())
    }

    @Test
    fun exportDoesNotExposeInternalStorage() {
        val encoded = ByteArray(UInt328.ENCODED_BYTES) { it.toByte() }
        val value = UInt328.fromLittleEndian(encoded)
        val exported = value.toLittleEndian()

        exported.fill(0)

        assertContentEquals(encoded, value.toLittleEndian())
    }

    @Test
    fun importRejectsNonExactWidths() {
        for (length in listOf(0, 1, 40, 42, 82)) {
            val encoded = ByteArray(length)

            val error =
                assertFailsWith<IllegalArgumentException> { UInt328.fromLittleEndian(encoded) }

            assertEquals("Unsigned 328-bit values require exactly 41 bytes", error.message)
        }
    }
}
