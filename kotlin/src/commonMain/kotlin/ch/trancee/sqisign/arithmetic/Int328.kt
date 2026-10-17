// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/** Immutable signed integer in [-2^327, 2^327), with checked, variable-time arithmetic. */
internal class Int328 private constructor(private val bits: UInt328) {
    private val negative: Boolean = bits >= SIGN_BIT

    /** Exports an owned, exact-width two's-complement little-endian encoding. */
    internal fun toLittleEndian(): ByteArray = bits.toLittleEndian()

    /** Returns -1, 0 or 1 according to signed mathematical order. */
    internal operator fun compareTo(other: Int328): Int =
        if (negative != other.negative) {
            if (negative) -1 else 1
        } else bits.compareTo(other.bits)

    /** Returns the exact sum; throws ArithmeticException if it exceeds the signed range. */
    internal fun add(other: Int328): Int328 {
        // Low unsigned bits are the two's-complement sum; signed overflow is checked separately.
        val result = Int328(bits.add(other.bits).value)
        if (negative == other.negative && result.negative != negative) {
            throw ArithmeticException("Signed 328-bit addition overflow")
        }
        return result
    }

    /** Returns the exact difference; throws ArithmeticException if it exceeds the signed range. */
    internal fun subtract(other: Int328): Int328 {
        // Subtract directly: negating the minimum signed value would itself overflow.
        val result = Int328(bits.subtract(other.bits).value)
        if (negative != other.negative && result.negative != negative) {
            throw ArithmeticException("Signed 328-bit subtraction overflow")
        }
        return result
    }

    /** Returns the exact signed 656-bit product, without narrowing or overflow. */
    internal fun multiply(other: Int328): Int656 {
        val encoded = magnitude().multiply(other.magnitude()).toLittleEndian()
        if (negative != other.negative) {
            var carry = 1
            for (index in encoded.indices) {
                val negated = (encoded[index].toInt().inv() and BYTE_MASK) + carry
                encoded[index] = negated.toByte()
                carry = negated ushr Byte.SIZE_BITS
            }
        }
        return Int656.fromLittleEndian(encoded)
    }

    /**
     * Returns a quotient truncated toward zero and a remainder with the dividend's sign.
     *
     * Variable-time; rejects zero divisors and the unrepresentable minimum / -1 quotient.
     */
    internal fun divideAndRemainder(divisor: Int328): Division {
        require(divisor.bits.compareTo(ZERO) != 0) { "Signed division requires a nonzero divisor" }
        val division = magnitude().divideAndRemainder(divisor.magnitude())
        val quotientNegative = negative != divisor.negative
        // Only minimum / -1 can produce a nonnegative magnitude of 2^327.
        if (!quotientNegative && division.quotient >= SIGN_BIT) {
            throw ArithmeticException("Signed 328-bit division overflow")
        }
        val quotientBits =
            if (quotientNegative) ZERO.subtract(division.quotient).value else division.quotient
        val remainderBits =
            if (negative) ZERO.subtract(division.remainder).value else division.remainder
        return Division(Int328(quotientBits), Int328(remainderBits))
    }

    /** Immutable exact signed quotient and remainder. */
    internal class Division
    internal constructor(internal val quotient: Int328, internal val remainder: Int328)

    private fun magnitude(): UInt328 =
        // Modular negation yields the exact unsigned magnitude, including 2^327 for minimum.
        if (negative) ZERO.subtract(bits).value else bits

    internal companion object {
        internal const val ENCODED_BYTES: Int = UInt328.ENCODED_BYTES

        private val ZERO = UInt328.fromLittleEndian(ByteArray(ENCODED_BYTES))
        private val SIGN_BIT =
            UInt328.fromLittleEndian(
                ByteArray(ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE },
            )

        internal fun fromLittleEndian(encoded: ByteArray): Int328 {
            require(encoded.size == ENCODED_BYTES) {
                "Signed 328-bit values require exactly 41 bytes"
            }

            return Int328(UInt328.fromLittleEndian(encoded))
        }
    }
}

private const val BYTE_MASK = 0xff
