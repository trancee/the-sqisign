// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzDigits.DIGIT_BITS
import ch.trancee.sqisign.arithmetic.IbzDigits.DIGIT_MASK
import ch.trancee.sqisign.arithmetic.IbzDigits.fromDigits
import ch.trancee.sqisign.arithmetic.IbzDigits.toDigits

/**
 * Truncated magnitude division by Knuth's Algorithm D on 32-bit digits, after Hacker's Delight
 * `divmnu`. Variable-time.
 */
internal object IbzKnuthDivision {
    private const val DIGIT_BASE = 0x1_0000_0000UL

    /** Returns the quotient in `dividend.size` words and the remainder in `divisor.size`. */
    internal fun divide(dividend: LongArray, divisor: LongArray): Pair<LongArray, LongArray> {
        val numerator = toDigits(dividend)
        val denominator = toDigits(divisor)
        val n = significantDigits(denominator)
        val m = significantDigits(numerator)
        require(n > 0) { "Division by zero" }
        val quotient = LongArray(numerator.size)
        val remainder =
            when {
                m < n -> numerator
                n == 1 -> longArrayOf(divideShort(numerator, m, denominator[0], quotient))
                else -> divideLong(numerator, m, denominator, n, quotient)
            }
        return Pair(
            fromDigits(quotient).copyOf(dividend.size),
            fromDigits(remainder).copyOf(divisor.size),
        )
    }

    private fun significantDigits(digits: LongArray): Int {
        var count = digits.size
        while (count > 0 && digits[count - 1] == 0L) {
            count--
        }
        return count
    }

    private fun divideShort(
        numerator: LongArray,
        m: Int,
        divisor: Long,
        quotient: LongArray,
    ): Long {
        var remainder = 0UL
        for (index in m - 1 downTo 0) {
            val value = (remainder shl DIGIT_BITS) or numerator[index].toULong()
            quotient[index] = (value / divisor.toULong()).toLong()
            remainder = value % divisor.toULong()
        }
        return remainder.toLong()
    }

    private fun divideLong(
        numerator: LongArray,
        m: Int,
        denominator: LongArray,
        n: Int,
        quotient: LongArray,
    ): LongArray {
        val shift = denominator[n - 1].countLeadingZeroBits() - DIGIT_BITS
        val divisor = normalised(denominator, n, shift)
        val work = normalised(numerator, m, shift).copyOf(m + 1)
        work[m] = numerator[m - 1] ushr (DIGIT_BITS - shift)
        for (j in m - n downTo 0) {
            quotient[j] = estimateAndSubtract(work, divisor, j)
        }
        return LongArray(n) { index ->
            ((work[index] ushr shift) or (work[index + 1] shl (DIGIT_BITS - shift))) and DIGIT_MASK
        }
    }

    /** The low [count] digits shifted left by [shift] bits, dropping the carry out of the top. */
    private fun normalised(digits: LongArray, count: Int, shift: Int): LongArray {
        val result = LongArray(count)
        result[0] = (digits[0] shl shift) and DIGIT_MASK
        for (index in 1 until count) {
            result[index] =
                ((digits[index] shl shift) or (digits[index - 1] ushr (DIGIT_BITS - shift))) and
                    DIGIT_MASK
        }
        return result
    }

    private fun estimateAndSubtract(work: LongArray, divisor: LongArray, j: Int): Long {
        val n = divisor.size
        val top = divisor[n - 1].toULong()
        val head = (work[j + n].toULong() shl DIGIT_BITS) or work[j + n - 1].toULong()
        var estimate = head / top
        var rest = head - estimate * top
        while (
            estimate >= DIGIT_BASE ||
                estimate * divisor[n - 2].toULong() >
                    (rest shl DIGIT_BITS) + work[j + n - 2].toULong()
        ) {
            estimate--
            rest += top
            if (rest >= DIGIT_BASE) {
                break
            }
        }
        var borrow = 0L
        for (index in 0 until n) {
            val product = estimate * divisor[index].toULong()
            val term = work[index + j] - borrow - (product.toLong() and DIGIT_MASK)
            work[index + j] = term and DIGIT_MASK
            borrow = (product shr DIGIT_BITS).toLong() - (term shr DIGIT_BITS)
        }
        val leading = work[j + n] - borrow
        work[j + n] = leading and DIGIT_MASK
        if (leading >= 0) {
            return estimate.toLong()
        }
        var carry = 0L
        for (index in 0 until n) {
            val term = work[index + j] + divisor[index] + carry
            work[index + j] = term and DIGIT_MASK
            carry = term ushr DIGIT_BITS
        }
        work[j + n] = (work[j + n] + carry) and DIGIT_MASK
        return estimate.toLong() - 1
    }
}
