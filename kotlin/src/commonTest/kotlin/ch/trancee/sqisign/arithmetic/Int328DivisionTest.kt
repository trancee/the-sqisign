// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Int328DivisionTest {
    @Test
    fun smallDivisionsTruncateTowardZeroAndKeepDividendRemainderSign() {
        for (dividend in -31..31) {
            for (divisor in -31..31) {
                if (divisor == 0) continue
                val leftBytes = signedSmall(dividend)
                val rightBytes = signedSmall(divisor)
                val left = Int328.fromLittleEndian(leftBytes)
                val right = Int328.fromLittleEndian(rightBytes)

                val division = left.divideAndRemainder(right)

                assertContentEquals(
                    signedSmall(dividend / divisor),
                    division.quotient.toLittleEndian(),
                    "$dividend / $divisor",
                )
                assertContentEquals(
                    signedSmall(dividend % divisor),
                    division.remainder.toLittleEndian(),
                    "$dividend % $divisor",
                )
                assertContentEquals(leftBytes, left.toLittleEndian())
                assertContentEquals(rightBytes, right.toLittleEndian())
            }
        }
    }

    @Test
    fun zeroDivisorsAreRejectedForBothSignsAndZero() {
        val zero = Int328.fromLittleEndian(signedSmall(0))
        for (encoded in listOf(signedMinimum(), signedSmall(-1), signedSmall(0), signedSmall(1))) {
            val dividend = Int328.fromLittleEndian(encoded)

            val error =
                assertFailsWith<IllegalArgumentException> { dividend.divideAndRemainder(zero) }

            assertEquals("Signed division requires a nonzero divisor", error.message)
            assertContentEquals(encoded, dividend.toLittleEndian())
            assertContentEquals(signedSmall(0), zero.toLittleEndian())
        }
    }

    @Test
    fun minimumByNegativeOneRejectsOverflowWithoutReturningWrappedResults() {
        val minimumBytes = signedMinimum()
        val minimum = Int328.fromLittleEndian(minimumBytes)
        val negativeOne = Int328.fromLittleEndian(signedSmall(-1))

        val error = assertFailsWith<ArithmeticException> { minimum.divideAndRemainder(negativeOne) }

        assertEquals("Signed 328-bit division overflow", error.message)
        assertContentEquals(minimumBytes, minimum.toLittleEndian())
        assertContentEquals(signedSmall(-1), negativeOne.toLittleEndian())
    }

    @Test
    fun minimumByOneRetainsTheSignedMinimum() {
        val minimumBytes = signedMinimum()
        val minimum = Int328.fromLittleEndian(minimumBytes)
        val one = Int328.fromLittleEndian(signedSmall(1))

        val division = minimum.divideAndRemainder(one)

        assertContentEquals(minimumBytes, division.quotient.toLittleEndian())
        assertContentEquals(signedSmall(0), division.remainder.toLittleEndian())
    }

    @Test
    fun sameMinimumInstanceDividesToOneAndZero() {
        val minimum = Int328.fromLittleEndian(signedMinimum())

        val division = minimum.divideAndRemainder(minimum)

        assertContentEquals(signedSmall(1), division.quotient.toLittleEndian())
        assertContentEquals(signedSmall(0), division.remainder.toLittleEndian())
    }

    @Test
    fun positiveMaximumByMinimumHasZeroQuotientAndUnchangedRemainder() {
        val maximumBytes =
            ByteArray(Int328.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
        val maximum = Int328.fromLittleEndian(maximumBytes)
        val minimum = Int328.fromLittleEndian(signedMinimum())

        val division = maximum.divideAndRemainder(minimum)

        assertContentEquals(signedSmall(0), division.quotient.toLittleEndian())
        assertContentEquals(maximumBytes, division.remainder.toLittleEndian())
    }

    @Test
    fun exportedResultBuffersDoNotAliasEitherResultOrOperand() {
        val dividend = Int328.fromLittleEndian(signedSmall(-17))
        val divisor = Int328.fromLittleEndian(signedSmall(5))
        val division = dividend.divideAndRemainder(divisor)

        division.quotient.toLittleEndian().fill(0)
        division.remainder.toLittleEndian().fill(0)

        assertContentEquals(signedSmall(-3), division.quotient.toLittleEndian())
        assertContentEquals(signedSmall(-2), division.remainder.toLittleEndian())
        assertContentEquals(signedSmall(-17), dividend.toLittleEndian())
        assertContentEquals(signedSmall(5), divisor.toLittleEndian())
    }
}

private fun signedSmall(value: Int): ByteArray =
    ByteArray(Int328.ENCODED_BYTES) { if (value < 0) -1 else 0 }.also { it[0] = value.toByte() }

private fun signedMinimum(): ByteArray =
    ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
