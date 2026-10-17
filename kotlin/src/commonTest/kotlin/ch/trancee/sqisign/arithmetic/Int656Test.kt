// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Int656Test {
    @Test
    fun everyEncodingBitRoundTripsIncludingTheSignedMinimum() {
        for (bit in 0 until Int656.ENCODED_BYTES * Byte.SIZE_BITS) {
            val encoded = ByteArray(Int656.ENCODED_BYTES)
            encoded[bit / Byte.SIZE_BITS] = (1 shl (bit % Byte.SIZE_BITS)).toByte()
            val value = Int656.fromLittleEndian(encoded)

            val output = value.toLittleEndian()

            assertContentEquals(encoded, output, "Bit $bit")
        }
    }

    @Test
    fun everySignByteRoundTripsIncludingNegativeOneAndZero() {
        for (top in 0..255) {
            val encoded = ByteArray(Int656.ENCODED_BYTES) { top.toByte() }
            val value = Int656.fromLittleEndian(encoded)

            val output = value.toLittleEndian()

            assertContentEquals(encoded, output, "Byte $top")
        }
    }

    @Test
    fun importAndEveryExportOwnTheirStorage() {
        val encoded = ByteArray(Int656.ENCODED_BYTES) { -1 }
        val expected = encoded.copyOf()
        val value = Int656.fromLittleEndian(encoded)
        encoded.fill(0)

        value.toLittleEndian().fill(0)

        assertContentEquals(expected, value.toLittleEndian())
    }

    @Test
    fun nonExactWidthsAreRejected() {
        for (size in listOf(0, 1, Int656.ENCODED_BYTES - 1, Int656.ENCODED_BYTES + 1)) {
            val encoded = ByteArray(size)

            val error =
                assertFailsWith<IllegalArgumentException> { Int656.fromLittleEndian(encoded) }

            assertEquals("Signed 656-bit values require exactly 82 bytes", error.message)
        }
    }
}
