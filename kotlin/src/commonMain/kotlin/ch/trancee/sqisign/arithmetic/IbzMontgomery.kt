// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

private const val DIGIT_BITS = 16
private const val DIGIT_MASK = 0xFFFF
private const val DIGITS_PER_WORD = Long.SIZE_BITS / DIGIT_BITS
private const val NEWTON_STEPS = 3

/**
 * Montgomery multiplication `a b / R mod m` for an odd [modulus] and `R = 2^(16 k)`, by CIOS over
 * 16-bit digits held in `Int`. Each multiply-add `t + a b + carry` is at most `(2^16 - 1)^2 + 2
 * (2^16 - 1) = 2^32 - 1`, so 32-bit wrapping arithmetic keeps it exact on every target without the
 * boxed `Long` emulation of Kotlin/JS. Operands must be reduced natural words. Variable-time.
 */
internal class IbzMontgomery(modulus: LongArray) {
    private val size = (IbzWords.bitLength(modulus) + DIGIT_BITS - 1) / DIGIT_BITS
    private val digits = digits(modulus)
    private val inverse = negatedInverse(digits[0])
    private val radixSquared =
        IbzNatural.mod(
            IbzBits.shiftLeft(
                IbzNatural.ONE,
                2 * DIGIT_BITS * size,
                IbzWords.limbs(2 * DIGIT_BITS * size + 1),
            ),
            modulus,
        )

    internal fun enter(value: LongArray): LongArray = multiply(value, radixSquared)

    internal fun exit(value: LongArray): LongArray = multiply(value, IbzNatural.ONE)

    internal fun multiply(left: LongArray, right: LongArray): LongArray {
        val a = digits(left)
        val b = digits(right)
        val t = IntArray(size + 2)
        for (i in 0 until size) {
            accumulate(t, a[i], b)
            reduce(t)
        }
        return words(if (atLeastModulus(t)) subtractModulus(t) else t)
    }

    private fun accumulate(t: IntArray, factor: Int, b: IntArray) {
        var carry = 0
        for (j in 0 until size) {
            val sum = t[j] + factor * b[j] + carry
            t[j] = sum and DIGIT_MASK
            carry = sum ushr DIGIT_BITS
        }
        val sum = t[size] + carry
        t[size] = sum and DIGIT_MASK
        t[size + 1] = sum ushr DIGIT_BITS
    }

    /** Adds `u m` for the `u` that clears the low digit, then shifts one digit down. */
    private fun reduce(t: IntArray) {
        val u = (t[0] * inverse) and DIGIT_MASK
        var carry = (t[0] + u * digits[0]) ushr DIGIT_BITS
        for (j in 1 until size) {
            val sum = t[j] + u * digits[j] + carry
            t[j - 1] = sum and DIGIT_MASK
            carry = sum ushr DIGIT_BITS
        }
        val sum = t[size] + carry
        t[size - 1] = sum and DIGIT_MASK
        t[size] = t[size + 1] + (sum ushr DIGIT_BITS)
    }

    private fun atLeastModulus(t: IntArray): Boolean {
        if (t[size] != 0) {
            return true
        }
        var index = size - 1
        while (index > 0 && t[index] == digits[index]) {
            index--
        }
        return t[index] >= digits[index]
    }

    private fun subtractModulus(t: IntArray): IntArray {
        var borrow = 0
        for (j in 0 until size) {
            val difference = t[j] - digits[j] - borrow
            t[j] = difference and DIGIT_MASK
            borrow = (difference ushr DIGIT_BITS) and 1
        }
        return t
    }

    private fun digits(words: LongArray): IntArray =
        IntArray(size) { index ->
            val word = words.wordAt(index / DIGITS_PER_WORD)
            (word ushr (DIGIT_BITS * (index % DIGITS_PER_WORD))).toInt() and DIGIT_MASK
        }

    private fun words(t: IntArray): LongArray {
        val result = LongArray((size + DIGITS_PER_WORD - 1) / DIGITS_PER_WORD)
        for (index in 0 until size) {
            result[index / DIGITS_PER_WORD] =
                result[index / DIGITS_PER_WORD] or
                    (t[index].toLong() shl (DIGIT_BITS * (index % DIGITS_PER_WORD)))
        }
        return IbzNatural.trim(result)
    }

    /** `-m^-1 mod 2^16` by Newton's iteration, which doubles the correct low bits from three. */
    private fun negatedInverse(low: Int): Int {
        var x = low
        repeat(NEWTON_STEPS) { x *= 2 - low * x }
        return -x and DIGIT_MASK
    }
}
