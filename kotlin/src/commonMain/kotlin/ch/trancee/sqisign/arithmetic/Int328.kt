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

    /** Returns a nonnegative unsigned GCD and signed coefficients satisfying Bezout's identity. */
    internal fun extendedGcd(other: Int328): ExtendedGcdResult = extendedGcd(this, other)

    /** Immutable exact signed quotient and remainder. */
    internal class Division
    internal constructor(internal val quotient: Int328, internal val remainder: Int328)

    /** Immutable exact unsigned GCD and signed Bezout coefficients. */
    internal class ExtendedGcdResult
    internal constructor(
        internal val gcd: UInt328,
        internal val leftCoefficient: Int328,
        internal val rightCoefficient: Int328,
    )

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
        private val ONE = UInt328.fromLittleEndian(ByteArray(ENCODED_BYTES).also { it[0] = 1 })
        private val SIGNED_ONE = Int328(ONE)
        private val SIGNED_ZERO = Int328(ZERO)

        internal fun fromLittleEndian(encoded: ByteArray): Int328 {
            require(encoded.size == ENCODED_BYTES) {
                "Signed 328-bit values require exactly 41 bytes"
            }

            return Int328(UInt328.fromLittleEndian(encoded))
        }

        private fun extendedGcd(left: Int328, right: Int328): ExtendedGcdResult {
            val leftMagnitude = left.magnitude()
            val rightMagnitude = right.magnitude()
            if (leftMagnitude.compareTo(ZERO) == 0 || rightMagnitude.compareTo(ZERO) == 0) {
                return zeroOperandExtendedGcd(left, right, leftMagnitude, rightMagnitude)
            }
            return nonzeroExtendedGcd(left, right, leftMagnitude, rightMagnitude)
        }

        private fun zeroOperandExtendedGcd(
            left: Int328,
            right: Int328,
            leftMagnitude: UInt328,
            rightMagnitude: UInt328,
        ): ExtendedGcdResult =
            when {
                leftMagnitude.compareTo(ZERO) == 0 && rightMagnitude.compareTo(ZERO) == 0 ->
                    ExtendedGcdResult(ZERO, SIGNED_ZERO, SIGNED_ZERO)
                leftMagnitude.compareTo(ZERO) == 0 ->
                    ExtendedGcdResult(
                        rightMagnitude,
                        SIGNED_ZERO,
                        if (right.negative) negate(SIGNED_ONE) else SIGNED_ONE,
                    )
                else ->
                    ExtendedGcdResult(
                        leftMagnitude,
                        if (left.negative) negate(SIGNED_ONE) else SIGNED_ONE,
                        SIGNED_ZERO,
                    )
            }

        private fun nonzeroExtendedGcd(
            left: Int328,
            right: Int328,
            leftMagnitude: UInt328,
            rightMagnitude: UInt328,
        ): ExtendedGcdResult {
            var previousRemainder = leftMagnitude
            var remainder = rightMagnitude
            var previousLeftCoefficient = SIGNED_ONE
            var leftCoefficient = SIGNED_ZERO
            var previousRightCoefficient = SIGNED_ZERO
            var rightCoefficient = SIGNED_ONE
            while (remainder.compareTo(ZERO) != 0) {
                val division = previousRemainder.divideAndRemainder(remainder)
                if (division.remainder.compareTo(ZERO) == 0) {
                    // The unused coefficient for the zero remainder can exceed signed 328 bits.
                    previousRemainder = remainder
                    previousLeftCoefficient = leftCoefficient
                    previousRightCoefficient = rightCoefficient
                    remainder = ZERO
                } else {
                    val nextLeft =
                        narrowCoefficient(
                            toWide(previousLeftCoefficient)
                                .subtract(
                                    multiplyUnsignedBySigned(division.quotient, leftCoefficient),
                                ),
                        )
                    val nextRight =
                        narrowCoefficient(
                            toWide(previousRightCoefficient)
                                .subtract(
                                    multiplyUnsignedBySigned(division.quotient, rightCoefficient),
                                ),
                        )
                    previousRemainder = remainder
                    remainder = division.remainder
                    previousLeftCoefficient = leftCoefficient
                    leftCoefficient = nextLeft
                    previousRightCoefficient = rightCoefficient
                    rightCoefficient = nextRight
                }
            }
            if (left.negative) previousLeftCoefficient = negate(previousLeftCoefficient)
            if (right.negative) previousRightCoefficient = negate(previousRightCoefficient)
            return ExtendedGcdResult(
                previousRemainder,
                previousLeftCoefficient,
                previousRightCoefficient,
            )
        }

        private fun multiplyUnsignedBySigned(value: UInt328, factor: Int328): Int656 {
            val product = value.multiply(factor.magnitude()).toLittleEndian()
            if (factor.negative) negateBytes(product)
            return Int656.fromLittleEndian(product)
        }

        private fun toWide(value: Int328): Int656 {
            val encoded = value.toLittleEndian()
            return Int656.fromLittleEndian(
                ByteArray(Int656.ENCODED_BYTES) { index ->
                    if (index < encoded.size) encoded[index]
                    else if (value.negative) 0xff.toByte() else 0
                },
            )
        }

        /** Narrows an extended-GCD coefficient or rejects a signed-width overflow. */
        internal fun narrowCoefficient(value: Int656): Int328 {
            val encoded = value.toLittleEndian()
            val signExtension =
                if ((encoded[ENCODED_BYTES - 1].toInt() and BYTE_MASK) >= 0x80) 0xff.toByte() else 0
            if ((ENCODED_BYTES until encoded.size).any { encoded[it] != signExtension }) {
                throw ArithmeticException("Extended-GCD coefficient exceeds signed 328-bit range")
            }
            return fromLittleEndian(encoded.copyOf(ENCODED_BYTES))
        }

        private fun negate(value: Int328): Int328 {
            val encoded = value.toLittleEndian()
            negateBytes(encoded)
            return fromLittleEndian(encoded)
        }

        private fun negateBytes(encoded: ByteArray) {
            var carry = 1
            for (index in encoded.indices) {
                val negated = (encoded[index].toInt().inv() and BYTE_MASK) + carry
                encoded[index] = negated.toByte()
                carry = negated ushr Byte.SIZE_BITS
            }
        }
    }
}

private const val BYTE_MASK = 0xff
