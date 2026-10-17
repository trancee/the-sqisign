// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.unsignedVectors
import ch.trancee.sqisign.wideDivisionVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UInt656DivisionTest {
    @Test
    fun fullWidthDivisionsMatchIndependentVectorsOrRejectZero() {
        for ((index, record) in wideDivisionVectors.withIndex()) {
            val fields = record.split(':')
            val dividend = UInt656.fromLittleEndian(fields[0].hexToByteArray())
            val divisor = UInt328.fromLittleEndian(fields[1].hexToByteArray())
            if (fields[2] == "-") {
                assertFailsWith<IllegalArgumentException>("Zero divisor case $index") {
                    dividend.divideAndRemainder(divisor)
                }
                continue
            }
            val quotient = fields[2].hexToByteArray()
            val remainder = fields[3].hexToByteArray()

            val output = dividend.divideAndRemainder(divisor)

            assertContentEquals(quotient, output.quotient.toLittleEndian(), "Quotient case $index")
            assertContentEquals(
                remainder,
                output.remainder.toLittleEndian(),
                "Remainder case $index",
            )
            assertEquals(-1, output.remainder.compareTo(divisor), "Remainder bound case $index")
        }
    }

    @Test
    fun productsDividedByANonzeroFactorRecoverTheOtherFactor() {
        for ((index, record) in unsignedVectors.withIndex()) {
            val fields = record.split(':')
            if (fields[8] == "-") continue
            val left = UInt328.fromLittleEndian(fields[0].hexToByteArray())
            val right = UInt328.fromLittleEndian(fields[1].hexToByteArray())
            val product = left.multiply(right)
            val expected = left.toLittleEndian().copyOf(UInt656.ENCODED_BYTES)

            val output = product.divideAndRemainder(right)

            assertContentEquals(expected, output.quotient.toLittleEndian(), "Factor case $index")
            assertContentEquals(
                ByteArray(UInt328.ENCODED_BYTES),
                output.remainder.toLittleEndian(),
                "Exact product case $index",
            )
        }
    }

    @Test
    fun dividingByOneRetainsAll656QuotientBits() {
        val maximumBytes = ByteArray(UInt656.ENCODED_BYTES) { -1 }
        val dividend = UInt656.fromLittleEndian(maximumBytes)
        val divisor = smallDivisor(1)

        val output = dividend.divideAndRemainder(divisor)

        assertContentEquals(maximumBytes, output.quotient.toLittleEndian())
        assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), output.remainder.toLittleEndian())
    }

    @Test
    fun maximumDividedByNarrowMaximumProducesA329BitQuotient() {
        val dividend = UInt656.fromLittleEndian(ByteArray(UInt656.ENCODED_BYTES) { -1 })
        val divisor = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { -1 })
        val expected =
            ByteArray(UInt656.ENCODED_BYTES).also {
                it[0] = 1
                it[41] = 1
            }

        val output = dividend.divideAndRemainder(divisor)

        assertContentEquals(expected, output.quotient.toLittleEndian())
        assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), output.remainder.toLittleEndian())
    }

    @Test
    fun shiftedRemainderRetainsThe329thBitBeforeSubtraction() {
        val dividendBytes =
            ByteArray(UInt656.ENCODED_BYTES).also {
                it[0] = 1
                it[41] = 1
            }
        val divisorBytes =
            ByteArray(UInt328.ENCODED_BYTES).also {
                it[0] = 1
                it[40] = 0x80.toByte()
            }
        val dividend = UInt656.fromLittleEndian(dividendBytes)
        val divisor = UInt328.fromLittleEndian(divisorBytes)
        val expectedQuotient = ByteArray(UInt656.ENCODED_BYTES).also { it[0] = 1 }
        val expectedRemainder = ByteArray(UInt328.ENCODED_BYTES).also { it[40] = 0x80.toByte() }

        val output = dividend.divideAndRemainder(divisor)

        assertContentEquals(expectedQuotient, output.quotient.toLittleEndian())
        assertContentEquals(expectedRemainder, output.remainder.toLittleEndian())
    }

    @Test
    fun everyPowerOfTwoDivisorSplitsTheFullWidthDividend() {
        val dividend = UInt656.fromLittleEndian(ByteArray(UInt656.ENCODED_BYTES) { -1 })
        for (bit in 0 until 328) {
            val divisorBytes = ByteArray(UInt328.ENCODED_BYTES)
            divisorBytes[bit / 8] = (1 shl (bit % 8)).toByte()
            val divisor = UInt328.fromLittleEndian(divisorBytes)
            val expectedQuotient = lowBits(UInt656.ENCODED_BYTES, 656 - bit)
            val expectedRemainder = lowBits(UInt328.ENCODED_BYTES, bit)

            val output = dividend.divideAndRemainder(divisor)

            assertContentEquals(expectedQuotient, output.quotient.toLittleEndian(), "Bit $bit")
            assertContentEquals(expectedRemainder, output.remainder.toLittleEndian(), "Bit $bit")
        }
    }

    @Test
    fun aLargerDivisorReturnsZeroQuotientAndOriginalDividend() {
        val dividendBytes = ByteArray(UInt656.ENCODED_BYTES).also { it[0] = 7 }
        val dividend = UInt656.fromLittleEndian(dividendBytes)
        val divisor = smallDivisor(8)
        val expectedRemainder = ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 7 }

        val output = dividend.divideAndRemainder(divisor)

        assertContentEquals(ByteArray(UInt656.ENCODED_BYTES), output.quotient.toLittleEndian())
        assertContentEquals(expectedRemainder, output.remainder.toLittleEndian())
    }

    @Test
    fun zeroDividendReturnsTwoExactWidthZeros() {
        val dividend = UInt656.fromLittleEndian(ByteArray(UInt656.ENCODED_BYTES))
        val divisor = smallDivisor(1)

        val output = dividend.divideAndRemainder(divisor)

        assertContentEquals(ByteArray(UInt656.ENCODED_BYTES), output.quotient.toLittleEndian())
        assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), output.remainder.toLittleEndian())
    }

    @Test
    fun divisionPreservesBothOperands() {
        val dividendBytes = ByteArray(UInt656.ENCODED_BYTES) { -1 }
        val divisorBytes = ByteArray(UInt328.ENCODED_BYTES) { 0x55 }
        val dividend = UInt656.fromLittleEndian(dividendBytes)
        val divisor = UInt328.fromLittleEndian(divisorBytes)

        dividend.divideAndRemainder(divisor)

        assertContentEquals(dividendBytes, dividend.toLittleEndian(), "Dividend")
        assertContentEquals(divisorBytes, divisor.toLittleEndian(), "Divisor")
    }

    @Test
    fun zeroDivisorsAreRejectedWithoutChangingEitherOperand() {
        val divisor = smallDivisor(0)
        for (value in listOf(0.toByte(), (-1).toByte())) {
            val dividendBytes = ByteArray(UInt656.ENCODED_BYTES) { value }
            val dividend = UInt656.fromLittleEndian(dividendBytes)

            val error =
                assertFailsWith<IllegalArgumentException> { dividend.divideAndRemainder(divisor) }

            assertEquals("Unsigned division requires a nonzero divisor", error.message)
            assertContentEquals(dividendBytes, dividend.toLittleEndian())
            assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), divisor.toLittleEndian())
        }
    }
}

private fun smallDivisor(value: Int): UInt328 =
    UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = value.toByte() })

private fun lowBits(bytes: Int, count: Int): ByteArray =
    ByteArray(bytes).also { encoded ->
        for (bit in 0 until count) {
            encoded[bit / 8] = (encoded[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
        }
    }
