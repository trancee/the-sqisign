// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.signedWideVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Int656ArithmeticTest {
    @Test
    fun comparisonUsesSignedMathematicalOrder() {
        val minimum = Int656.fromLittleEndian(minimumBytes())
        val negative = Int656.fromLittleEndian(signedValue(-1))
        val zero = Int656.fromLittleEndian(ByteArray(Int656.ENCODED_BYTES))
        val positive = Int656.fromLittleEndian(signedValue(1))
        val maximum = Int656.fromLittleEndian(maximumBytes())

        assertEquals(-1, minimum.compareTo(negative))
        assertEquals(-1, negative.compareTo(zero))
        assertEquals(-1, zero.compareTo(positive))
        assertEquals(-1, positive.compareTo(maximum))
        assertEquals(0, minimum.compareTo(minimum))
        assertEquals(0, maximum.compareTo(maximum))
        assertEquals(1, maximum.compareTo(minimum))
    }

    @Test
    fun additionPreservesTheExactNegativeBoundaryAndOperands() {
        val leftBytes = minimumBytes()
        val rightBytes = maximumBytes()
        val left = Int656.fromLittleEndian(leftBytes)
        val right = Int656.fromLittleEndian(rightBytes)

        val sum = left.add(right)

        assertContentEquals(signedValue(-1), sum.toLittleEndian())
        assertContentEquals(leftBytes, left.toLittleEndian())
        assertContentEquals(rightBytes, right.toLittleEndian())
    }

    @Test
    fun additionRejectsPositiveSignedOverflow() {
        val maximumBytes = maximumBytes()
        val oneBytes = signedValue(1)
        val maximum = Int656.fromLittleEndian(maximumBytes)
        val one = Int656.fromLittleEndian(oneBytes)

        val error = assertFailsWith<ArithmeticException> { maximum.add(one) }

        assertEquals("Signed 656-bit addition overflow", error.message)
        assertContentEquals(maximumBytes, maximum.toLittleEndian())
        assertContentEquals(oneBytes, one.toLittleEndian())
    }

    @Test
    fun additionRejectsNegativeSignedOverflow() {
        val minimumBytes = minimumBytes()
        val negativeOneBytes = signedValue(-1)
        val minimum = Int656.fromLittleEndian(minimumBytes)
        val negativeOne = Int656.fromLittleEndian(negativeOneBytes)

        val error = assertFailsWith<ArithmeticException> { minimum.add(negativeOne) }

        assertEquals("Signed 656-bit addition overflow", error.message)
        assertContentEquals(minimumBytes, minimum.toLittleEndian())
        assertContentEquals(negativeOneBytes, negativeOne.toLittleEndian())
    }

    @Test
    fun subtractionProducesTheExactMinimumAndDoesNotMutateOperands() {
        val leftBytes = minimumBytes()
        val rightBytes = signedValue(0)
        val left = Int656.fromLittleEndian(leftBytes)
        val right = Int656.fromLittleEndian(rightBytes)

        val difference = left.subtract(right)

        assertContentEquals(minimumBytes(), difference.toLittleEndian())
        assertContentEquals(leftBytes, left.toLittleEndian())
        assertContentEquals(rightBytes, right.toLittleEndian())
    }

    @Test
    fun subtractionRejectsPositiveSignedOverflow() {
        val maximumBytes = maximumBytes()
        val negativeOneBytes = signedValue(-1)
        val maximum = Int656.fromLittleEndian(maximumBytes)
        val negativeOne = Int656.fromLittleEndian(negativeOneBytes)

        val error = assertFailsWith<ArithmeticException> { maximum.subtract(negativeOne) }

        assertEquals("Signed 656-bit subtraction overflow", error.message)
        assertContentEquals(maximumBytes, maximum.toLittleEndian())
        assertContentEquals(negativeOneBytes, negativeOne.toLittleEndian())
    }

    @Test
    fun subtractionRejectsNegativeSignedOverflow() {
        val minimumBytes = minimumBytes()
        val oneBytes = signedValue(1)
        val minimum = Int656.fromLittleEndian(minimumBytes)
        val one = Int656.fromLittleEndian(oneBytes)

        val error = assertFailsWith<ArithmeticException> { minimum.subtract(one) }

        assertEquals("Signed 656-bit subtraction overflow", error.message)
        assertContentEquals(minimumBytes, minimum.toLittleEndian())
        assertContentEquals(oneBytes, one.toLittleEndian())
    }

    @Test
    fun arithmeticResultsOwnTheirExportedBytes() {
        val left = Int656.fromLittleEndian(signedValue(37))
        val right = Int656.fromLittleEndian(signedValue(-5))
        val expectedSum = signedValue(32)
        val expectedDifference = signedValue(42)

        val sum = left.add(right)
        val difference = left.subtract(right)

        sum.toLittleEndian().fill(0)
        difference.toLittleEndian().fill(0)
        assertContentEquals(expectedSum, sum.toLittleEndian())
        assertContentEquals(expectedDifference, difference.toLittleEndian())
    }

    @Test
    fun comparisonsMatchIndependentSignedWideVectors() {
        for ((index, record) in signedWideVectors.withIndex()) {
            val fields = record.split(':')
            val leftBytes = fields[0].hexToByteArray()
            val rightBytes = fields[1].hexToByteArray()
            val left = Int656.fromLittleEndian(leftBytes)
            val right = Int656.fromLittleEndian(rightBytes)

            val comparison = left.compareTo(right)

            assertEquals(fields[2].toInt(), comparison, "Comparison case $index")
            assertContentEquals(leftBytes, left.toLittleEndian(), "Left case $index")
            assertContentEquals(rightBytes, right.toLittleEndian(), "Right case $index")
        }
    }

    @Test
    fun sumsMatchIndependentSignedWideVectorsOrRejectOverflow() {
        for ((index, record) in signedWideVectors.withIndex()) {
            val fields = record.split(':')
            val leftBytes = fields[0].hexToByteArray()
            val rightBytes = fields[1].hexToByteArray()
            val left = Int656.fromLittleEndian(leftBytes)
            val right = Int656.fromLittleEndian(rightBytes)
            if (fields[3] == "overflow") {
                val error = assertFailsWith<ArithmeticException> { left.add(right) }
                assertEquals("Signed 656-bit addition overflow", error.message, "Case $index")
            } else {
                val sum = left.add(right)

                assertContentEquals(
                    fields[3].hexToByteArray(),
                    sum.toLittleEndian(),
                    "Sum case $index",
                )
            }
            assertContentEquals(leftBytes, left.toLittleEndian(), "Left case $index")
            assertContentEquals(rightBytes, right.toLittleEndian(), "Right case $index")
        }
    }

    @Test
    fun differencesMatchIndependentSignedWideVectorsOrRejectOverflow() {
        for ((index, record) in signedWideVectors.withIndex()) {
            val fields = record.split(':')
            val leftBytes = fields[0].hexToByteArray()
            val rightBytes = fields[1].hexToByteArray()
            val left = Int656.fromLittleEndian(leftBytes)
            val right = Int656.fromLittleEndian(rightBytes)
            if (fields[4] == "overflow") {
                val error = assertFailsWith<ArithmeticException> { left.subtract(right) }
                assertEquals("Signed 656-bit subtraction overflow", error.message, "Case $index")
            } else {
                val difference = left.subtract(right)

                assertContentEquals(
                    fields[4].hexToByteArray(),
                    difference.toLittleEndian(),
                    "Difference case $index",
                )
            }
            assertContentEquals(leftBytes, left.toLittleEndian(), "Left case $index")
            assertContentEquals(rightBytes, right.toLittleEndian(), "Right case $index")
        }
    }
}

private fun signedValue(value: Int): ByteArray =
    ByteArray(Int656.ENCODED_BYTES) { if (value < 0) -1 else 0 }.also { it[0] = value.toByte() }

private fun minimumBytes(): ByteArray =
    ByteArray(Int656.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }

private fun maximumBytes(): ByteArray =
    ByteArray(Int656.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
