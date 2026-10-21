// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class IbzWordsTest {
    @Test
    fun quotientEstimateAtDigitBaseIsCorrected() =
        assertDivision(
            dividend = words("100000002", "fffffffe00000000"),
            divisor = words("7ffffffffffffffe", "fffffffe"),
            quotient = words("ffffffff", "0"),
            remainder = words("8000000300000000", "7ffffffe"),
        )

    @Test
    fun quotientCorrectionStopsWhenRemainderEstimateOverflows() =
        assertDivision(
            dividend = words("280000000", "17fffffff", "ffffffff"),
            divisor = words("800000007fffffff", "fffffffe"),
            quotient = words("80000001", "1", "0"),
            remainder = words("4000000280000001", "c0000001"),
        )

    @Test
    fun oversizedQuotientDigitIsAddedBack() =
        assertDivision(
            dividend = words("180000000", "1ffffffff", "1"),
            divisor = words("ffffffffffffffff", "1"),
            quotient = words("80000000ffffffff", "0", "0"),
            remainder = words("800000027fffffff", "1"),
        )

    @Test
    fun zeroDivisorIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            IbzKnuthDivision.divide(words("1"), words("0", "0"))
        }
    }

    @Test
    fun subtractionAcceptsShorterSubtrahend() {
        val result = IbzWords.subtract(words("0", "1"), words("1"))

        assertContentEquals(words("ffffffffffffffff", "0"), result)
    }

    private fun assertDivision(
        dividend: LongArray,
        divisor: LongArray,
        quotient: LongArray,
        remainder: LongArray,
    ) {
        val (actualQuotient, actualRemainder) = IbzKnuthDivision.divide(dividend, divisor)

        assertContentEquals(quotient, actualQuotient)
        assertContentEquals(remainder, actualRemainder)
    }

    private fun words(vararg values: String): LongArray =
        LongArray(values.size) { values[it].toULong(16).toLong() }
}
