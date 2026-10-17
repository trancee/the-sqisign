// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertContentEquals

class Int328MultiplyTest {
    @Test
    fun smallProductsMatchIndependentMachineArithmetic() {
        for (left in -16..16) {
            for (right in -16..16) {
                val first = Int328.fromLittleEndian(smallSignedBytes(left, Int328.ENCODED_BYTES))
                val second = Int328.fromLittleEndian(smallSignedBytes(right, Int328.ENCODED_BYTES))
                val expected = smallSignedBytes(left * right, Int656.ENCODED_BYTES)

                val product = first.multiply(second)

                assertContentEquals(expected, product.toLittleEndian(), "$left * $right")
            }
        }
    }

    @Test
    fun everyPossibleProductBitIncludingMinimumSquareIsRetained() {
        val signBit = Int328.ENCODED_BYTES * Byte.SIZE_BITS - 1
        for (bit in 0..2 * signBit) {
            val leftBit = minOf(signBit, bit)
            val rightBit = bit - leftBit
            val leftBytes = powerBytes(leftBit, Int328.ENCODED_BYTES)
            val rightBytes = powerBytes(rightBit, Int328.ENCODED_BYTES)
            val left = Int328.fromLittleEndian(leftBytes)
            val right = Int328.fromLittleEndian(rightBytes)
            val negative = (leftBit == signBit) != (rightBit == signBit)
            val expected =
                if (negative) negativePowerBytes(bit) else powerBytes(bit, Int656.ENCODED_BYTES)

            val product = left.multiply(right)

            assertContentEquals(expected, product.toLittleEndian(), "Product bit $bit")
            assertContentEquals(leftBytes, left.toLittleEndian(), "Left bit $leftBit")
            assertContentEquals(rightBytes, right.toLittleEndian(), "Right bit $rightBit")
        }
    }

    @Test
    fun minimumTimesNegativeOneIsPositiveWithoutNarrowing() {
        val minimum = Int328.fromLittleEndian(powerBytes(327, Int328.ENCODED_BYTES))
        val negativeOne = Int328.fromLittleEndian(ByteArray(Int328.ENCODED_BYTES) { -1 })

        val product = minimum.multiply(negativeOne)

        assertContentEquals(powerBytes(327, Int656.ENCODED_BYTES), product.toLittleEndian())
    }

    @Test
    fun multiplyingNegativeValuesByZeroProducesCanonicalZero() {
        val minimum = Int328.fromLittleEndian(powerBytes(327, Int328.ENCODED_BYTES))
        val zero = Int328.fromLittleEndian(ByteArray(Int328.ENCODED_BYTES))

        val product = minimum.multiply(zero)

        assertContentEquals(ByteArray(Int656.ENCODED_BYTES), product.toLittleEndian())
    }

    @Test
    fun mutatingProductEncodingDoesNotAffectTheProductOrOperands() {
        val encoded = ByteArray(Int328.ENCODED_BYTES) { -1 }
        val value = Int328.fromLittleEndian(encoded)
        val product = value.multiply(value)
        val expected = smallSignedBytes(1, Int656.ENCODED_BYTES)

        product.toLittleEndian().fill(0)

        assertContentEquals(expected, product.toLittleEndian())
        assertContentEquals(encoded, value.toLittleEndian())
    }
}

private fun smallSignedBytes(value: Int, size: Int): ByteArray =
    ByteArray(size) { index ->
        if (index < Int.SIZE_BYTES) (value shr (index * Byte.SIZE_BITS)).toByte()
        else if (value < 0) -1 else 0
    }

private fun powerBytes(bit: Int, size: Int): ByteArray =
    ByteArray(size).also { it[bit / Byte.SIZE_BITS] = (1 shl (bit % Byte.SIZE_BITS)).toByte() }

private fun negativePowerBytes(bit: Int): ByteArray =
    ByteArray(Int656.ENCODED_BYTES) { index ->
        when {
            index < bit / Byte.SIZE_BITS -> 0
            index == bit / Byte.SIZE_BITS -> (-1 shl (bit % Byte.SIZE_BITS)).toByte()
            else -> -1
        }
    }
