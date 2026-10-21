// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.gcdVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals

class UInt328GcdTest {
    @Test
    fun gcdMatchesIndependentVectorsInBothOrders() {
        for ((index, record) in gcdVectors.withIndex()) {
            val fields = record.split(':')
            val left = UInt328.fromLittleEndian(fields[0].hexToByteArray())
            val right = UInt328.fromLittleEndian(fields[1].hexToByteArray())
            val expected = fields[2].hexToByteArray()
            for ((first, second) in listOf(left to right, right to left)) {
                val output = first.gcd(second)

                assertContentEquals(expected, output.toLittleEndian(), "GCD case $index")
            }
        }
    }

    @Test
    fun zeroAndZeroProduceZero() {
        val zero = smallUnsigned(0)

        val output = zero.gcd(zero)

        assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), output.toLittleEndian())
    }

    @Test
    fun aZeroOperandReturnsTheOtherValueInEitherOrder() {
        val zero = smallUnsigned(0)
        val expected = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val maximum = UInt328.fromLittleEndian(expected)
        for ((left, right) in listOf(zero to maximum, maximum to zero)) {
            val output = left.gcd(right)

            assertContentEquals(expected, output.toLittleEndian())
        }
    }

    @Test
    fun sameInstanceReturnsItsOriginalValue() {
        val encoded = ByteArray(UInt328.ENCODED_BYTES) { 0xaa.toByte() }
        val value = UInt328.fromLittleEndian(encoded)

        val output = value.gcd(value)

        assertContentEquals(encoded, output.toLittleEndian())
        assertContentEquals(encoded, value.toLittleEndian())
    }

    @Test
    fun smallPairsMatchIndependentDivisorSearch() {
        for (left in 0..31) {
            for (right in 0..31) {
                val leftValue = smallUnsigned(left)
                val rightValue = smallUnsigned(right)
                val expected =
                    if (left == 0 && right == 0) 0
                    else (1..maxOf(left, right)).last { left % it == 0 && right % it == 0 }

                val output = leftValue.gcd(rightValue)

                assertContentEquals(
                    smallUnsigned(expected).toLittleEndian(),
                    output.toLittleEndian(),
                    "gcd($left, $right)",
                )
            }
        }
    }

    @Test
    fun everyPowerOfTwoIsCoprimeToTheOddMaximum() {
        for (bit in 0 until 328) {
            val encoded = ByteArray(UInt328.ENCODED_BYTES)
            encoded[bit / 8] = (1 shl (bit % 8)).toByte()
            val value = UInt328.fromLittleEndian(encoded)
            val maximum = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { -1 })

            val output = value.gcd(maximum)

            assertContentEquals(
                smallUnsigned(1).toLittleEndian(),
                output.toLittleEndian(),
                "Bit $bit",
            )
        }
    }

    @Test
    fun powersOfTwoSharedByBothOperandsAreRetained() {
        for (bit in 0 until 326) {
            val expected = ByteArray(UInt328.ENCODED_BYTES)
            expected[bit / 8] = (1 shl (bit % 8)).toByte()
            val leftBytes = ByteArray(UInt328.ENCODED_BYTES)
            for (offset in listOf(0, 1)) {
                val position = bit + offset
                leftBytes[position / 8] =
                    (leftBytes[position / 8].toInt() or (1 shl (position % 8))).toByte()
            }
            val rightBytes = ByteArray(UInt328.ENCODED_BYTES)
            for (offset in listOf(0, 2)) {
                val position = bit + offset
                rightBytes[position / 8] =
                    (rightBytes[position / 8].toInt() or (1 shl (position % 8))).toByte()
            }
            val left = UInt328.fromLittleEndian(leftBytes)
            val right = UInt328.fromLittleEndian(rightBytes)

            val output = left.gcd(right)

            assertContentEquals(expected, output.toLittleEndian(), "Shared factor bit $bit")
        }
    }

    @Test
    fun gcdPreservesBothOperands() {
        val leftBytes = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val rightBytes = ByteArray(UInt328.ENCODED_BYTES) { 0x55 }
        val left = UInt328.fromLittleEndian(leftBytes)
        val right = UInt328.fromLittleEndian(rightBytes)

        left.gcd(right)

        assertContentEquals(leftBytes, left.toLittleEndian(), "Left operand")
        assertContentEquals(rightBytes, right.toLittleEndian(), "Right operand")
    }
}

private fun smallUnsigned(value: Int): UInt328 =
    UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = value.toByte() })
