// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Int328Test {
    @Test
    fun comparisonUsesSignedOrder() {
        val operands =
            listOf(
                minimumBytes(),
                smallBytes(-2),
                smallBytes(-1),
                smallBytes(0),
                smallBytes(1),
                maximumBytes(),
            )
        for ((leftIndex, leftBytes) in operands.withIndex()) {
            for ((rightIndex, rightBytes) in operands.withIndex()) {
                val left = Int328.fromLittleEndian(leftBytes)
                val right = Int328.fromLittleEndian(rightBytes)

                val comparison = left.compareTo(right)

                assertEquals(
                    leftIndex.compareTo(rightIndex),
                    comparison,
                    "$leftIndex vs $rightIndex",
                )
            }
        }
    }

    @Test
    fun smallSumsAreExact() {
        for (left in -16..16) {
            for (right in -16..16) {
                val first = Int328.fromLittleEndian(smallBytes(left))
                val second = Int328.fromLittleEndian(smallBytes(right))

                val sum = first.add(second)

                assertContentEquals(
                    smallBytes(left + right),
                    sum.toLittleEndian(),
                    "$left + $right",
                )
            }
        }
    }

    @Test
    fun smallDifferencesAreExact() {
        for (left in -16..16) {
            for (right in -16..16) {
                val first = Int328.fromLittleEndian(smallBytes(left))
                val second = Int328.fromLittleEndian(smallBytes(right))

                val difference = first.subtract(second)

                assertContentEquals(
                    smallBytes(left - right),
                    difference.toLittleEndian(),
                    "$left - $right",
                )
            }
        }
    }

    @Test
    fun additionRejectsBothOverflowDirectionsWithoutMutation() {
        for ((leftBytes, rightBytes) in
            listOf(
                maximumBytes() to smallBytes(1),
                minimumBytes() to smallBytes(-1),
                maximumBytes() to maximumBytes(),
                minimumBytes() to minimumBytes(),
            )) {
            val left = Int328.fromLittleEndian(leftBytes)
            val right = Int328.fromLittleEndian(rightBytes)

            val error = assertFailsWith<ArithmeticException> { left.add(right) }

            assertEquals("Signed 328-bit addition overflow", error.message)
            assertContentEquals(leftBytes, left.toLittleEndian())
            assertContentEquals(rightBytes, right.toLittleEndian())
        }
    }

    @Test
    fun subtractionRejectsBothOverflowDirectionsWithoutMutation() {
        for ((leftBytes, rightBytes) in
            listOf(
                maximumBytes() to smallBytes(-1),
                minimumBytes() to smallBytes(1),
                smallBytes(0) to minimumBytes(),
                maximumBytes() to minimumBytes(),
                minimumBytes() to maximumBytes(),
            )) {
            val left = Int328.fromLittleEndian(leftBytes)
            val right = Int328.fromLittleEndian(rightBytes)

            val error = assertFailsWith<ArithmeticException> { left.subtract(right) }

            assertEquals("Signed 328-bit subtraction overflow", error.message)
            assertContentEquals(leftBytes, left.toLittleEndian())
            assertContentEquals(rightBytes, right.toLittleEndian())
        }
    }

    @Test
    fun signedBoundaryCancellationIsExact() {
        val minimum = Int328.fromLittleEndian(minimumBytes())
        val maximum = Int328.fromLittleEndian(maximumBytes())

        val sum = minimum.add(maximum)

        assertContentEquals(smallBytes(-1), sum.toLittleEndian())
    }

    @Test
    fun sameInstanceSubtractionProducesTheOnlyZeroEncoding() {
        val value = Int328.fromLittleEndian(minimumBytes())

        val difference = value.subtract(value)

        assertContentEquals(smallBytes(0), difference.toLittleEndian())
        assertContentEquals(minimumBytes(), value.toLittleEndian())
    }

    @Test
    fun codecsRoundTripEveryBitAndEverySignByte() {
        val encodings =
            (0 until Int328.ENCODED_BYTES * Byte.SIZE_BITS).map { bit ->
                ByteArray(Int328.ENCODED_BYTES).also {
                    it[bit / Byte.SIZE_BITS] = (1 shl (bit % Byte.SIZE_BITS)).toByte()
                }
            } +
                (0..255).map { top ->
                    ByteArray(Int328.ENCODED_BYTES) { 0x55 }
                        .also { it[it.lastIndex] = top.toByte() }
                }
        for (encoded in encodings) {
            val value = Int328.fromLittleEndian(encoded)

            val output = value.toLittleEndian()

            assertContentEquals(encoded, output)
        }
    }

    @Test
    fun codecStorageIsOwnedOnImportAndEveryExport() {
        val encoded = minimumBytes()
        val expected = encoded.copyOf()
        val value = Int328.fromLittleEndian(encoded)
        encoded.fill(0)

        val exported = value.toLittleEndian()
        exported.fill(0)

        assertContentEquals(expected, value.toLittleEndian())
    }

    @Test
    fun nonExactWidthsAreRejected() {
        for (size in listOf(0, 1, Int328.ENCODED_BYTES - 1, Int328.ENCODED_BYTES + 1)) {
            val encoded = ByteArray(size)

            val error =
                assertFailsWith<IllegalArgumentException> { Int328.fromLittleEndian(encoded) }

            assertEquals("Signed 328-bit values require exactly 41 bytes", error.message)
        }
    }
}

private fun smallBytes(value: Int): ByteArray =
    ByteArray(Int328.ENCODED_BYTES) { if (value < 0) -1 else 0 }.also { it[0] = value.toByte() }

private fun minimumBytes(): ByteArray =
    ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }

private fun maximumBytes(): ByteArray =
    ByteArray(Int328.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
