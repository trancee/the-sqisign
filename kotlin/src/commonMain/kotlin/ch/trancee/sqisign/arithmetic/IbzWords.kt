// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/** Variable-time unsigned little-endian word kernels behind [Ibz] magnitudes. */
internal object IbzWords {
    internal const val WORD_BITS = 64

    internal fun limbs(bits: Int): Int = (bits + WORD_BITS - 1) / WORD_BITS

    internal fun bitLength(words: LongArray): Int {
        for (index in words.size - 1 downTo 0) {
            if (words[index] != 0L) {
                return (index + 1) * WORD_BITS - words[index].countLeadingZeroBits()
            }
        }
        return 0
    }

    internal fun trailingZeros(words: LongArray): Int {
        for (index in words.indices) {
            if (words[index] != 0L) {
                return index * WORD_BITS + words[index].countTrailingZeroBits()
            }
        }
        return 0
    }

    internal fun isZero(words: LongArray): Boolean = words.all { it == 0L }

    /** Two's-complement negation at the same word count. */
    internal fun negate(words: LongArray): LongArray {
        val result = LongArray(words.size)
        var carry = 1L
        for (index in words.indices) {
            result[index] = words[index].inv() + carry
            carry = if (carry == 1L && result[index] == 0L) 1L else 0L
        }
        return result
    }

    internal fun compare(left: LongArray, right: LongArray): Int {
        for (index in maxOf(left.size, right.size) - 1 downTo 0) {
            val first = left.wordAt(index)
            val second = right.wordAt(index)
            if (first != second) {
                return first.toULong().compareTo(second.toULong())
            }
        }
        return 0
    }

    internal fun add(left: LongArray, right: LongArray): LongArray {
        val result = LongArray(maxOf(left.size, right.size) + 1)
        var carry = 0UL
        for (index in 0 until result.size - 1) {
            val first = left.wordAt(index).toULong()
            val sum = first + right.wordAt(index).toULong()
            val total = sum + carry
            carry = (if (sum < first) 1UL else 0UL) + (if (total < sum) 1UL else 0UL)
            result[index] = total.toLong()
        }
        result[result.size - 1] = carry.toLong()
        return result
    }

    /** Difference of magnitudes with `left >= right`. */
    internal fun subtract(left: LongArray, right: LongArray): LongArray {
        val result = LongArray(left.size)
        var borrow = 0UL
        for (index in left.indices) {
            val first = left[index].toULong()
            val second = right.wordAt(index).toULong()
            val difference = first - second
            val total = difference - borrow
            borrow = (if (first < second) 1UL else 0UL) + (if (difference < borrow) 1UL else 0UL)
            result[index] = total.toLong()
        }
        return result
    }

    internal fun multiply(left: LongArray, right: LongArray): LongArray {
        val first = IbzDigits.toDigits(left)
        val second = IbzDigits.toDigits(right)
        val product = LongArray(first.size + second.size)
        for (outer in first.indices) {
            var carry = 0L
            for (inner in second.indices) {
                val term = first[outer] * second[inner] + product[outer + inner] + carry
                product[outer + inner] = term and IbzDigits.DIGIT_MASK
                carry = term ushr IbzDigits.DIGIT_BITS
            }
            product[outer + second.size] = carry
        }
        return IbzDigits.fromDigits(product)
    }
}

/** The word at a non-negative [index], or zero beyond the stored words. */
internal fun LongArray.wordAt(index: Int): Long = if (index < size) this[index] else 0L
