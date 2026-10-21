// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UInt656EncodingTest {
    @Test
    fun everyBitSurvivesLittleEndianRoundTrip() {
        for (bit in 0 until 656) {
            val encoded = ByteArray(UInt656.ENCODED_BYTES)
            encoded[bit / 8] = (1 shl (bit % 8)).toByte()

            val output = UInt656.fromLittleEndian(encoded).toLittleEndian()

            assertContentEquals(encoded, output, "Bit $bit")
        }
    }

    @Test
    fun maximumUsesAllEncodingBits() {
        val encoded = ByteArray(UInt656.ENCODED_BYTES) { -1 }

        val output = UInt656.fromLittleEndian(encoded).toLittleEndian()

        assertContentEquals(encoded, output)
    }

    @Test
    fun zeroRetainsExactWidth() {
        val encoded = ByteArray(UInt656.ENCODED_BYTES)

        val output = UInt656.fromLittleEndian(encoded).toLittleEndian()

        assertContentEquals(encoded, output)
    }

    @Test
    fun importDoesNotRetainCallerStorage() {
        val encoded = ByteArray(UInt656.ENCODED_BYTES) { it.toByte() }
        val expected = encoded.copyOf()
        val value = UInt656.fromLittleEndian(encoded)

        encoded.fill(0)

        assertContentEquals(expected, value.toLittleEndian())
    }

    @Test
    fun exportDoesNotExposeInternalStorage() {
        val encoded = ByteArray(UInt656.ENCODED_BYTES) { it.toByte() }
        val value = UInt656.fromLittleEndian(encoded)
        val exported = value.toLittleEndian()

        exported.fill(0)

        assertContentEquals(encoded, value.toLittleEndian())
    }

    @Test
    fun importRejectsNonExactWidths() {
        for (length in listOf(0, 1, 41, 81, 83)) {
            val encoded = ByteArray(length)

            val error =
                assertFailsWith<IllegalArgumentException> { UInt656.fromLittleEndian(encoded) }

            assertEquals("Unsigned 656-bit values require exactly 82 bytes", error.message)
        }
    }
}
