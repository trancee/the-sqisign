// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

/**
 * Odd byte-encoded prime with precomputed Montgomery constants over 16-bit digits.
 *
 * Digits stay in `Int` so every partial product `a*b + t + carry < 2^32` is exact as an unsigned
 * 32-bit value on JVM, JS, and Native alike. Arithmetic is variable-time.
 */
internal class FieldModulus(encoded: ByteArray) {
    internal val bytes: ByteArray = encoded.copyOf()
    private val digitCount: Int = (bytes.size + 1) / 2
    private val digits: IntArray = toDigits(bytes)
    private val inverseNegated: Int = negatedInverseDigit(digits[0])
    private val montgomerySquare: IntArray = toDigits(montgomerySquareBytes())
    internal val inverseExponent: ByteArray = fieldInverseExponent(bytes)
    internal val squareRootExponent: ByteArray = fieldSquareRootExponent(bytes)

    internal val size: Int
        get() = bytes.size

    internal fun multiply(left: ByteArray, right: ByteArray): ByteArray =
        toBytes(montgomery(montgomery(toDigits(left), toDigits(right)), montgomerySquare))

    internal fun exponentiate(value: ByteArray, exponent: ByteArray): ByteArray {
        val base = montgomery(toDigits(value), montgomerySquare)
        var result = montgomery(toDigits(ONE_BYTE), montgomerySquare)
        for (bit in exponent.size * Byte.SIZE_BITS - 1 downTo 0) {
            result = montgomery(result, result)
            if (((exponent[bit / Byte.SIZE_BITS].toInt() ushr (bit % Byte.SIZE_BITS)) and 1) != 0) {
                result = montgomery(result, base)
            }
        }
        return toBytes(montgomery(result, toDigits(ONE_BYTE)))
    }

    /** CIOS Montgomery product `left*right/R mod p` for canonical digit inputs. */
    private fun montgomery(left: IntArray, right: IntArray): IntArray {
        val count = digitCount
        val modulusDigits = digits
        val inverse = inverseNegated
        val total = IntArray(count + 2)
        for (outer in 0 until count) {
            val leftDigit = left[outer]
            var carry = 0
            for (inner in 0 until count) {
                val sum = leftDigit * right[inner] + total[inner] + carry
                total[inner] = sum and DIGIT_MASK
                carry = sum ushr DIGIT_BITS
            }
            var sum = total[count] + carry
            total[count] = sum and DIGIT_MASK
            total[count + 1] = sum ushr DIGIT_BITS
            val factor = (total[0] * inverse) and DIGIT_MASK
            carry = (factor * modulusDigits[0] + total[0]) ushr DIGIT_BITS
            for (inner in 1 until count) {
                sum = factor * modulusDigits[inner] + total[inner] + carry
                total[inner - 1] = sum and DIGIT_MASK
                carry = sum ushr DIGIT_BITS
            }
            sum = total[count] + carry
            total[count - 1] = sum and DIGIT_MASK
            total[count] = total[count + 1] + (sum ushr DIGIT_BITS)
        }
        return reduceOnce(total)
    }

    private fun reduceOnce(total: IntArray): IntArray {
        val difference = IntArray(digitCount)
        var borrow = 0
        for (index in 0 until digitCount) {
            val value = total[index] - digits[index] - borrow
            difference[index] = value and DIGIT_MASK
            borrow = (value ushr Int.SIZE_BITS - 1) and 1
        }
        return if (total[digitCount] - borrow == 0) difference else total.copyOf(digitCount)
    }

    private fun montgomerySquareBytes(): ByteArray {
        var value = ByteArray(bytes.size).also { it[0] = 1 }
        repeat(2 * DIGIT_BITS * digitCount) { value = addFieldModulo(value, value, this) }
        return value
    }

    private fun toDigits(value: ByteArray): IntArray {
        val result = IntArray(digitCount)
        for (index in value.indices) {
            val shift = (index % 2) * Byte.SIZE_BITS
            result[index / 2] =
                result[index / 2] or ((value[index].toInt() and BYTE_MASK) shl shift)
        }
        return result
    }

    private fun toBytes(value: IntArray): ByteArray =
        ByteArray(bytes.size) { index ->
            (value[index / 2] ushr ((index % 2) * Byte.SIZE_BITS)).toByte()
        }

    private companion object {
        private const val DIGIT_BITS = 16
        private const val DIGIT_MASK = 0xffff
        private const val BYTE_MASK = 0xff
        private const val NEWTON_STEPS = 4
        private val ONE_BYTE = byteArrayOf(1)

        /** Returns `-m^-1 mod 2^16` for odd `m` by Newton lifting from three correct bits. */
        private fun negatedInverseDigit(lowDigit: Int): Int {
            var inverse = lowDigit
            repeat(NEWTON_STEPS) { inverse = inverse * (2 - lowDigit * inverse) }
            return -inverse and DIGIT_MASK
        }
    }
}
