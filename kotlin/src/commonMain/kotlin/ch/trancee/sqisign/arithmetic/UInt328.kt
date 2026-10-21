// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * Immutable unsigned integer in [0, 2^328), occupying the p324_3 encoding width.
 *
 * The 22 little-endian radix-2^15 limbs are normalized; the top limb has 13 bits. This is not a
 * field element: values at or above the field prime remain representable. Comparison is
 * variable-time; no timing or secure-erasure guarantee is provided.
 */
internal class UInt328 private constructor(private val limbs: IntArray) {
    /** Exports an owned, exact-width, unsigned little-endian encoding. */
    internal fun toLittleEndian(): ByteArray = encodeLittleEndian(limbs, ENCODED_BYTES)

    /** Returns -1, 0, or 1 according to the unsigned mathematical order. */
    internal operator fun compareTo(other: UInt328): Int {
        for (index in LAST_LIMB downTo 0) {
            if (limbs[index] != other.limbs[index]) {
                return limbs[index].compareTo(other.limbs[index])
            }
        }
        return 0
    }

    /** Returns the low 328 bits and carry, so no overflow is silently discarded. */
    internal fun add(other: UInt328): Addition {
        val output = IntArray(LIMB_COUNT)
        var carry = 0
        for (index in 0 until LAST_LIMB) {
            val sum = limbs[index] + other.limbs[index] + carry
            output[index] = sum and LIMB_MASK
            carry = sum ushr LIMB_BITS
        }
        val topSum = limbs[LAST_LIMB] + other.limbs[LAST_LIMB] + carry
        output[LAST_LIMB] = topSum and TOP_MASK
        return Addition(UInt328(output), topSum ushr TOP_BITS)
    }

    /** Returns all 656 product bits, without truncation or modular reduction. */
    internal fun multiply(other: UInt328): UInt656 {
        val output = IntArray(2 * LIMB_COUNT)
        for (leftIndex in 0 until LIMB_COUNT) {
            var carry = 0
            for (rightIndex in 0 until LIMB_COUNT) {
                val position = leftIndex + rightIndex
                // Normalizing each multiply-add bounds the accumulator by 2^30 - 1.
                val accumulated =
                    limbs[leftIndex] * other.limbs[rightIndex] + output[position] + carry
                output[position] = accumulated and LIMB_MASK
                carry = accumulated ushr LIMB_BITS
            }
            output[leftIndex + LIMB_COUNT] = carry
        }
        return UInt656.fromLittleEndian(encodeLittleEndian(output, UInt656.ENCODED_BYTES))
    }

    /** Returns the low 328 bits and borrow; value = this - other + borrow * 2^328. */
    internal fun subtract(other: UInt328): Subtraction {
        val output = IntArray(LIMB_COUNT)
        val borrow = subtractLimbs(limbs, other.limbs, output)
        return Subtraction(UInt328(output), borrow)
    }

    /**
     * Returns quotient and remainder with this = quotient * divisor + remainder.
     *
     * The remainder is below the divisor. Division is variable-time and rejects a zero divisor.
     */
    internal fun divideAndRemainder(divisor: UInt328): Division {
        require(divisor.limbs.any { it != 0 }) { "Unsigned division requires a nonzero divisor" }
        val quotient = IntArray(LIMB_COUNT)
        var remainder = IntArray(LIMB_COUNT)
        var difference = IntArray(LIMB_COUNT)
        for (bit in ENCODED_BYTES * BYTE_BITS - 1 downTo 0) {
            var carry = (limbs[bit / LIMB_BITS] ushr (bit % LIMB_BITS)) and 1
            // The processed dividend prefix is at most 328 bits, including this next bit.
            for (index in 0 until LIMB_COUNT) {
                val shifted = (remainder[index] shl 1) or carry
                remainder[index] = shifted and LIMB_MASK
                carry = shifted ushr LIMB_BITS
            }
            if (subtractLimbs(remainder, divisor.limbs, difference) == 0) {
                val previous = remainder
                remainder = difference
                difference = previous
                quotient[bit / LIMB_BITS] = quotient[bit / LIMB_BITS] or (1 shl (bit % LIMB_BITS))
            }
        }
        return Division(UInt328(quotient), UInt328(remainder))
    }

    /** Variable-time greatest common divisor; gcd(0, x) = x and gcd(0, 0) = 0. */
    internal fun gcd(other: UInt328): UInt328 {
        var left = this
        var right = other
        while (right.limbs.any { it != 0 }) {
            val remainder = left.divideAndRemainder(right).remainder
            left = right
            right = remainder
        }
        return left
    }

    /**
     * Returns the canonical inverse below modulus; rejects modulus < 2 and non-coprime inputs.
     *
     * Variable-time: not suitable for a constant-time or secret-input guarantee.
     */
    internal fun modInverse(modulus: UInt328): UInt328 {
        val one = UInt328(IntArray(LIMB_COUNT).also { it[0] = 1 })
        require(modulus > one) { "Unsigned modular inverse requires a modulus of at least two" }
        var previousRemainder = modulus
        var remainder = divideAndRemainder(modulus).remainder
        var previousCoefficient = UInt328(IntArray(LIMB_COUNT))
        var coefficient = one
        while (remainder.limbs.any { it != 0 }) {
            val division = previousRemainder.divideAndRemainder(remainder)
            val product =
                division.quotient.multiply(coefficient).divideAndRemainder(modulus).remainder
            // Both residues are below modulus; subtract without relying on wrapped arithmetic.
            val nextCoefficient =
                if (previousCoefficient >= product) previousCoefficient.subtract(product).value
                else modulus.subtract(product.subtract(previousCoefficient).value).value
            previousRemainder = remainder
            remainder = division.remainder
            previousCoefficient = coefficient
            coefficient = nextCoefficient
        }
        require(previousRemainder.compareTo(one) == 0) {
            "Unsigned modular inverse requires coprime operands"
        }
        return previousCoefficient
    }

    /** Returns floor(sqrt(this)); iteration count and division are variable-time. */
    internal fun sqrtFloor(): UInt328 = SquareRoot.floor(this)

    private fun subtractLimbs(left: IntArray, right: IntArray, output: IntArray): Int {
        var borrow = 0
        for (index in 0 until LAST_LIMB) {
            val difference = left[index] - right[index] - borrow
            output[index] = difference and LIMB_MASK
            borrow = difference ushr (Int.SIZE_BITS - 1)
        }
        val topDifference = left[LAST_LIMB] - right[LAST_LIMB] - borrow
        output[LAST_LIMB] = topDifference and TOP_MASK
        return topDifference ushr (Int.SIZE_BITS - 1)
    }

    private fun encodeLittleEndian(limbs: IntArray, encodedBytes: Int): ByteArray =
        ByteArray(encodedBytes) { index ->
            val bit = index * BYTE_BITS
            val limb = bit / LIMB_BITS
            val shift = bit % LIMB_BITS
            var value = limbs[limb] ushr shift
            if (shift + BYTE_BITS > LIMB_BITS) {
                value = value or (limbs[limb + 1] shl (LIMB_BITS - shift))
            }
            value.toByte()
        }

    /** Exact sum = value + carry * 2^328, with carry in {0, 1}. */
    internal class Addition
    internal constructor(internal val value: UInt328, internal val carry: Int)

    /** Exact difference = value - borrow * 2^328, with borrow in {0, 1}. */
    internal class Subtraction
    internal constructor(internal val value: UInt328, internal val borrow: Int)

    /** Exact unsigned quotient and remainder, each independently owned. */
    internal class Division
    internal constructor(internal val quotient: UInt328, internal val remainder: UInt328)

    private object SquareRoot {
        fun floor(value: UInt328): UInt328 {
            val bitLength = bitLength(value)
            if (bitLength == 0) return value

            val estimateBit = (bitLength + 1) / 2
            val estimateLimbs = IntArray(LIMB_COUNT)
            estimateLimbs[estimateBit / LIMB_BITS] = 1 shl (estimateBit % LIMB_BITS)
            var estimate = UInt328(estimateLimbs)
            while (true) {
                val quotient = value.divideAndRemainder(estimate).quotient
                val sum = estimate.add(quotient)
                val next = halfWithCarry(sum.value, sum.carry)
                if (next >= estimate) return estimate
                estimate = next
            }
        }

        private fun bitLength(value: UInt328): Int {
            for (index in LAST_LIMB downTo 0) {
                var limb = value.limbs[index]
                if (limb != 0) {
                    var bitLength = index * LIMB_BITS
                    while (limb != 0) {
                        bitLength++
                        limb = limb ushr 1
                    }
                    return bitLength
                }
            }
            return 0
        }

        private fun halfWithCarry(value: UInt328, carry: Int): UInt328 {
            val output = IntArray(LIMB_COUNT)
            var lowerCarry = 0
            for (index in LAST_LIMB downTo 0) {
                val limb = value.limbs[index]
                output[index] = (limb ushr 1) or (lowerCarry shl (LIMB_BITS - 1))
                lowerCarry = limb and 1
            }
            output[LAST_LIMB] = output[LAST_LIMB] or (carry shl (TOP_BITS - 1))
            return UInt328(output)
        }
    }

    internal companion object {
        internal const val ENCODED_BYTES: Int = 41

        internal fun fromLittleEndian(encoded: ByteArray): UInt328 {
            require(encoded.size == ENCODED_BYTES) {
                "Unsigned 328-bit values require exactly 41 bytes"
            }
            val limbs = IntArray(LIMB_COUNT)
            for (index in encoded.indices) {
                val byte = encoded[index].toInt() and BYTE_MASK
                val bit = index * BYTE_BITS
                val limb = bit / LIMB_BITS
                val shift = bit % LIMB_BITS
                limbs[limb] = limbs[limb] or ((byte shl shift) and LIMB_MASK)
                if (shift + BYTE_BITS > LIMB_BITS) {
                    limbs[limb + 1] = limbs[limb + 1] or (byte ushr (LIMB_BITS - shift))
                }
            }
            return UInt328(limbs)
        }
    }
}

// Addition fits in [0, 65535]; subtraction fits in [-32768, 32767].
private const val LIMB_BITS = 15
private const val LIMB_COUNT = 22
private const val LAST_LIMB = LIMB_COUNT - 1
private const val LIMB_MASK = (1 shl LIMB_BITS) - 1
private const val TOP_BITS = 13
private const val TOP_MASK = (1 shl TOP_BITS) - 1
private const val BYTE_BITS = 8
private const val BYTE_MASK = (1 shl BYTE_BITS) - 1
