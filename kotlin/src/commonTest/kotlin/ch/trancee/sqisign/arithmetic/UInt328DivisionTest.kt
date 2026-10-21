// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.unsignedVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UInt328DivisionTest {
    @Test
    fun divisionMatchesIndependentVectorsOrRejectsZeroDivisors() {
        for ((index, record) in unsignedVectors.withIndex()) {
            val fields = record.split(':')
            val dividend = UInt328.fromLittleEndian(fields[0].hexToByteArray())
            val divisor = UInt328.fromLittleEndian(fields[1].hexToByteArray())
            if (fields[8] == "-") {
                assertFailsWith<IllegalArgumentException>("Zero divisor case $index") {
                    dividend.divideAndRemainder(divisor)
                }
                continue
            }
            val quotient = fields[8].hexToByteArray()
            val remainder = fields[9].hexToByteArray()

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
    fun dividingMaximumByOneRetainsAllQuotientBits() {
        val maximumBytes = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val maximum = UInt328.fromLittleEndian(maximumBytes)
        val one = smallUnsigned(1)

        val output = maximum.divideAndRemainder(one)

        assertContentEquals(maximumBytes, output.quotient.toLittleEndian())
        assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), output.remainder.toLittleEndian())
    }

    @Test
    fun sameInstanceProducesOneAndZeroWithoutChangingOperand() {
        val encoded = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val value = UInt328.fromLittleEndian(encoded)

        val output = value.divideAndRemainder(value)

        assertContentEquals(smallUnsigned(1).toLittleEndian(), output.quotient.toLittleEndian())
        assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), output.remainder.toLittleEndian())
        assertContentEquals(encoded, value.toLittleEndian())
    }

    @Test
    fun divisionByEveryPowerOfTwoSplitsMaximumAtTheSpecifiedBit() {
        val maximum = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { -1 })
        for (bit in 0 until 328) {
            val divisor = ByteArray(UInt328.ENCODED_BYTES)
            divisor[bit / 8] = (1 shl (bit % 8)).toByte()
            val expectedQuotient = lowBits(328 - bit)
            val expectedRemainder = lowBits(bit)

            val output = maximum.divideAndRemainder(UInt328.fromLittleEndian(divisor))

            assertContentEquals(expectedQuotient, output.quotient.toLittleEndian(), "Bit $bit")
            assertContentEquals(expectedRemainder, output.remainder.toLittleEndian(), "Bit $bit")
        }
    }

    @Test
    fun smallDivisionsMatchIndependentIntegerArithmetic() {
        for (dividend in 0..31) {
            for (divisor in 1..31) {
                val left = smallUnsigned(dividend)
                val right = smallUnsigned(divisor)
                val expectedQuotient = smallUnsigned(dividend / divisor).toLittleEndian()
                val expectedRemainder = smallUnsigned(dividend % divisor).toLittleEndian()

                val output = left.divideAndRemainder(right)

                val context = "$dividend divided by $divisor"
                assertContentEquals(expectedQuotient, output.quotient.toLittleEndian(), context)
                assertContentEquals(expectedRemainder, output.remainder.toLittleEndian(), context)
            }
        }
    }

    @Test
    fun divisionPreservesBothOperands() {
        val leftBytes = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val rightBytes = ByteArray(UInt328.ENCODED_BYTES) { 0x55 }
        val left = UInt328.fromLittleEndian(leftBytes)
        val right = UInt328.fromLittleEndian(rightBytes)

        left.divideAndRemainder(right)

        assertContentEquals(leftBytes, left.toLittleEndian(), "Dividend")
        assertContentEquals(rightBytes, right.toLittleEndian(), "Divisor")
    }

    @Test
    fun zeroDivisorIsRejectedIncludingZeroDividend() {
        val zero = smallUnsigned(0)
        for (dividend in listOf(zero, smallUnsigned(1))) {
            val error =
                assertFailsWith<IllegalArgumentException> { dividend.divideAndRemainder(zero) }

            assertEquals("Unsigned division requires a nonzero divisor", error.message)
            assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), zero.toLittleEndian())
        }
    }
}

private fun smallUnsigned(value: Int): UInt328 =
    UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = value.toByte() })

private fun lowBits(count: Int): ByteArray =
    ByteArray(UInt328.ENCODED_BYTES).also { encoded ->
        for (bit in 0 until count) {
            encoded[bit / 8] = (encoded[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
        }
    }
