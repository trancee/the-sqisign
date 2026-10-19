// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * Variable-time unbounded natural numbers on trimmed little-endian words. Number theory works here
 * because intermediate products such as `n^2` may exceed an [Ibz] capacity.
 */
internal object IbzNatural {
    internal val ONE: LongArray = longArrayOf(1L)

    internal fun of(value: Long): LongArray = trim(longArrayOf(value))

    internal fun trim(words: LongArray): LongArray =
        words.copyOf(IbzWords.limbs(IbzWords.bitLength(words)))

    internal fun add(left: LongArray, right: LongArray): LongArray = trim(IbzWords.add(left, right))

    /** Difference with `left >= right`. */
    internal fun subtract(left: LongArray, right: LongArray): LongArray =
        trim(IbzWords.subtract(left, right))

    /** The product of non-zero naturals. */
    internal fun multiply(left: LongArray, right: LongArray): LongArray =
        trim(IbzWords.multiply(left, right))

    /** Truncated quotient and remainder of a division by a non-zero [divisor]. */
    internal fun divide(dividend: LongArray, divisor: LongArray): Pair<LongArray, LongArray> {
        if (IbzWords.compare(dividend, divisor) < 0) {
            return Pair(LongArray(0), dividend)
        }
        val (quotient, remainder) = IbzKnuthDivision.divide(dividend, divisor)
        return Pair(trim(quotient), trim(remainder))
    }

    internal fun mod(value: LongArray, modulus: LongArray): LongArray =
        divide(value, modulus).second

    internal fun shiftRight(words: LongArray, exponent: Int): LongArray =
        trim(IbzBits.shiftRight(words, exponent))

    internal fun bit(words: LongArray, index: Int): Boolean =
        (words.wordAt(index / IbzWords.WORD_BITS) ushr (index % IbzWords.WORD_BITS)) and 1L == 1L

    /** The floor of the square root by Newton's iteration from a power-of-two upper bound. */
    internal fun sqrtFloor(value: LongArray): LongArray {
        if (value.isEmpty()) {
            return value
        }
        val half = (IbzWords.bitLength(value) + 1) / 2
        var root = IbzBits.shiftLeft(ONE, half, IbzWords.limbs(half + 1))
        while (true) {
            val next = shiftRight(add(root, divide(value, root).first), 1)
            if (IbzWords.compare(next, root) >= 0) {
                return trim(root)
            }
            root = next
        }
    }
}
