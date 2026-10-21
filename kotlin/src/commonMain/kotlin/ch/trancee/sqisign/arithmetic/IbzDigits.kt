// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/** Conversions between 64-bit words and 32-bit digits, plus in-place single-digit kernels. */
internal object IbzDigits {
    internal const val DIGIT_BITS = 32
    internal const val DIGIT_MASK = 0xFFFF_FFFFL

    internal fun toDigits(words: LongArray): LongArray =
        LongArray(words.size * 2) { index ->
            (words[index / 2] ushr (DIGIT_BITS * (index % 2))) and DIGIT_MASK
        }

    internal fun fromDigits(digits: LongArray): LongArray {
        val words = LongArray((digits.size + 1) / 2)
        for (index in digits.indices) {
            words[index / 2] = words[index / 2] or (digits[index] shl (DIGIT_BITS * (index % 2)))
        }
        return words
    }

    /** Replaces 32-bit [digits] by `digits * factor + addend` modulo their width. */
    internal fun multiplyAdd(digits: LongArray, factor: Long, addend: Long) {
        var carry = addend
        for (index in digits.indices) {
            val term = digits[index] * factor + carry
            digits[index] = term and DIGIT_MASK
            carry = term ushr DIGIT_BITS
        }
    }

    /** Divides 32-bit [digits] in place by a divisor below `2^31`, returning the remainder. */
    internal fun divideSmall(digits: LongArray, divisor: Long): Long {
        var remainder = 0L
        for (index in digits.size - 1 downTo 0) {
            val numerator = (remainder shl DIGIT_BITS) or digits[index]
            digits[index] = numerator / divisor
            remainder = numerator % divisor
        }
        return remainder
    }
}
