// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.unsignedVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UInt328ArithmeticTest {
    @Test
    fun additionReturnsIndependentLowBitsAndCarry() {
        for ((index, record) in unsignedVectors.withIndex()) {
            val case = UnsignedCase(record)

            val output = case.left.add(case.right)

            assertContentEquals(case.sum, output.value.toLittleEndian(), "Sum case $index")
            assertEquals(case.carry, output.carry, "Carry case $index")
        }
    }

    @Test
    fun subtractionReturnsIndependentLowBitsAndBorrow() {
        for ((index, record) in unsignedVectors.withIndex()) {
            val case = UnsignedCase(record)

            val output = case.left.subtract(case.right)

            assertContentEquals(
                case.difference,
                output.value.toLittleEndian(),
                "Difference case $index",
            )
            assertEquals(case.borrow, output.borrow, "Borrow case $index")
        }
    }

    @Test
    fun comparisonUsesUnsignedHighLimbPrecedence() {
        for ((index, record) in unsignedVectors.withIndex()) {
            val case = UnsignedCase(record)

            val output = case.left.compareTo(case.right)

            assertEquals(case.comparison, output, "Comparison case $index")
        }
    }

    @Test
    fun additionPreservesBothOperands() {
        val leftBytes = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val rightBytes = ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 }
        val left = UInt328.fromLittleEndian(leftBytes)
        val right = UInt328.fromLittleEndian(rightBytes)

        left.add(right)

        assertContentEquals(leftBytes, left.toLittleEndian(), "Left operand")
        assertContentEquals(rightBytes, right.toLittleEndian(), "Right operand")
    }

    @Test
    fun subtractionPreservesBothOperands() {
        val leftBytes = ByteArray(UInt328.ENCODED_BYTES)
        val rightBytes = ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 }
        val left = UInt328.fromLittleEndian(leftBytes)
        val right = UInt328.fromLittleEndian(rightBytes)

        left.subtract(right)

        assertContentEquals(leftBytes, left.toLittleEndian(), "Left operand")
        assertContentEquals(rightBytes, right.toLittleEndian(), "Right operand")
    }

    @Test
    fun additionOfSameInstanceUsesOriginalValue() {
        val input = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val value = UInt328.fromLittleEndian(input)
        val expected = input.copyOf().also { it[0] = -2 }

        val output = value.add(value)

        assertContentEquals(expected, output.value.toLittleEndian())
        assertEquals(1, output.carry)
        assertContentEquals(input, value.toLittleEndian(), "Original operand")
    }

    @Test
    fun subtractionOfSameInstanceProducesZeroWithoutBorrow() {
        val input = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val value = UInt328.fromLittleEndian(input)

        val output = value.subtract(value)

        assertContentEquals(ByteArray(UInt328.ENCODED_BYTES), output.value.toLittleEndian())
        assertEquals(0, output.borrow)
        assertContentEquals(input, value.toLittleEndian(), "Original operand")
    }
}

private class UnsignedCase(record: String) {
    private val fields = record.split(':')
    val left = UInt328.fromLittleEndian(fields[0].hexToByteArray())
    val right = UInt328.fromLittleEndian(fields[1].hexToByteArray())
    val sum = fields[2].hexToByteArray()
    val carry = fields[3].toInt()
    val difference = fields[4].hexToByteArray()
    val borrow = fields[5].toInt()
    val comparison = fields[6].toInt()
}
