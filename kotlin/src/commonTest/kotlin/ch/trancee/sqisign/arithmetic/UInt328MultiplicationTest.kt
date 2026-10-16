// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.unsignedVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals

class UInt328MultiplicationTest {
    @Test
    fun fullProductsMatchIndependentVectors() {
        for ((index, record) in unsignedVectors.withIndex()) {
            val fields = record.split(':')
            val left = UInt328.fromLittleEndian(fields[0].hexToByteArray())
            val right = UInt328.fromLittleEndian(fields[1].hexToByteArray())
            val expected = fields[7].hexToByteArray()

            val output = left.multiply(right)

            assertContentEquals(expected, output.toLittleEndian(), "Product case $index")
        }
    }

    @Test
    fun reversedOperandsMatchIndependentProducts() {
        for ((index, record) in unsignedVectors.withIndex()) {
            val fields = record.split(':')
            val left = UInt328.fromLittleEndian(fields[0].hexToByteArray())
            val right = UInt328.fromLittleEndian(fields[1].hexToByteArray())
            val expected = fields[7].hexToByteArray()

            val output = right.multiply(left)

            assertContentEquals(expected, output.toLittleEndian(), "Reversed product case $index")
        }
    }

    @Test
    fun maximumSquaredRetainsBothHalves() {
        val maximum = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { -1 })
        // (2^328 - 1)^2 = 2^656 - 2^329 + 1.
        val expected = ByteArray(UInt656.ENCODED_BYTES)
        expected[0] = 1
        expected[41] = -2
        expected.fill(-1, 42)

        val output = maximum.multiply(maximum)

        assertContentEquals(expected, output.toLittleEndian())
    }

    @Test
    fun singleBitProductsReachEveryPossibleBitPosition() {
        for (bit in 0 until 655) {
            val leftBit = minOf(bit, 327)
            val rightBit = bit - leftBit
            val left = UInt328.fromLittleEndian(singleBit(UInt328.ENCODED_BYTES, leftBit))
            val right = UInt328.fromLittleEndian(singleBit(UInt328.ENCODED_BYTES, rightBit))
            val expected = singleBit(UInt656.ENCODED_BYTES, bit)

            val output = left.multiply(right)

            assertContentEquals(expected, output.toLittleEndian(), "Product bit $bit")
        }
    }

    @Test
    fun multiplicationPreservesBothOperands() {
        val leftBytes = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val rightBytes = ByteArray(UInt328.ENCODED_BYTES) { 0x55 }
        val left = UInt328.fromLittleEndian(leftBytes)
        val right = UInt328.fromLittleEndian(rightBytes)

        left.multiply(right)

        assertContentEquals(leftBytes, left.toLittleEndian(), "Left operand")
        assertContentEquals(rightBytes, right.toLittleEndian(), "Right operand")
    }
}

private fun singleBit(bytes: Int, bit: Int): ByteArray =
    ByteArray(bytes).also { it[bit / 8] = (1 shl (bit % 8)).toByte() }
