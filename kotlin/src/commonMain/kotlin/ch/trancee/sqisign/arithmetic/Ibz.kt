// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzWords.WORD_BITS
import ch.trancee.sqisign.arithmetic.IbzWords.limbs

/**
 * Immutable bounded signed integer with the observable semantics of the reference `ibz_t`.
 *
 * The value is the two's-complement value of `limbs(bitlen)` words. Each operation chooses its
 * result bit length as the reference does, clamped to [capacityBits]. Where the reference silently
 * wraps a checked result at capacity, this port throws [ArithmeticException] instead. Operations
 * are variable-time.
 */
internal class Ibz
private constructor(
    internal val capacityBits: Int,
    internal val bitlen: Int,
    private val words: LongArray,
) : Comparable<Ibz> {
    internal val isNegative: Boolean
        get() = words.last() < 0

    internal val isZero: Boolean
        get() = IbzWords.isZero(words)

    internal val isOne: Boolean
        get() = words[0] == 1L && IbzWords.isZero(words.copyOfRange(1, words.size))

    internal val isEven: Boolean
        get() = words[0] and 1L == 0L

    internal val isOdd: Boolean
        get() = !isEven

    /** The stored two's-complement words, least significant first. */
    internal fun toWords(): LongArray = words.copyOf()

    /** The absolute value as unsigned words; the minimum value maps to its exact magnitude. */
    internal fun magnitude(): LongArray = if (isNegative) IbzWords.negate(words) else words.copyOf()

    /** Sum at bit length `max(bitlen) + 1`; checked sums reject any wrapped result. */
    internal fun sum(other: Ibz, subtract: Boolean, checked: Boolean): Ibz {
        requireSameCapacity(other)
        val first = magnitude()
        val second = other.magnitude()
        val otherNegative = other.isNegative != subtract
        val bits = minOf(maxOf(bitlen, other.bitlen) + 1, capacityBits)
        val negative: Boolean
        val total: LongArray
        if (isNegative == otherNegative) {
            negative = isNegative
            total = IbzWords.add(first, second)
        } else if (IbzWords.compare(first, second) >= 0) {
            negative = isNegative
            total = IbzWords.subtract(first, second)
        } else {
            negative = otherNegative
            total = IbzWords.subtract(second, first)
        }
        if (checked && !fits(negative, total, limbs(bits) * WORD_BITS)) {
            throw ArithmeticException("ibz sum exceeds $capacityBits bits")
        }
        return create(capacityBits, bits, negative, total)
    }

    /** Product at bit length `bitlen + other.bitlen - 1`, keeping the reference sign quirk. */
    internal operator fun times(other: Ibz): Ibz {
        requireSameCapacity(other)
        val requested = bitlen + other.bitlen - 1
        val product = IbzWords.multiply(magnitude(), other.magnitude())
        val productBits = IbzWords.bitLength(product)
        if (
            productBits > capacityBits || (requested > capacityBits && productBits == capacityBits)
        ) {
            throw ArithmeticException("ibz product exceeds $capacityBits bits")
        }
        return create(
            capacityBits,
            minOf(requested, capacityBits),
            isNegative != other.isNegative,
            product,
        )
    }

    override fun compareTo(other: Ibz): Int {
        requireSameCapacity(other)
        if (isNegative != other.isNegative) {
            return if (isNegative) -1 else 1
        }
        val order = IbzWords.compare(magnitude(), other.magnitude())
        return if (isNegative) -order else order
    }

    /** Re-bounds the value: extends exactly, or sign-truncates to `max(bound, 1)` bits. */
    internal fun withBound(bound: Int): Ibz {
        require(bound <= capacityBits) { "Bound exceeds capacity" }
        if (bound >= bitlen) {
            return Ibz(capacityBits, bound, extended(limbs(bound)))
        }
        val bits = maxOf(bound, 1)
        return Ibz(capacityBits, bits, IbzBits.signExtend(words, bits))
    }

    /** The stored words sign-extended to [count] words. */
    internal fun extended(count: Int): LongArray {
        val fill = if (isNegative) -1L else 0L
        return LongArray(count) { index -> if (index < words.size) words[index] else fill }
    }

    private fun requireSameCapacity(other: Ibz) =
        require(capacityBits == other.capacityBits) { "Capacity mismatch" }

    override fun equals(other: Any?): Boolean =
        other is Ibz &&
            capacityBits == other.capacityBits &&
            bitlen == other.bitlen &&
            words.contentEquals(other.words)

    override fun hashCode(): Int =
        (capacityBits * HASH_FACTOR + bitlen) * HASH_FACTOR + words.contentHashCode()

    override fun toString(): String = toString(DECIMAL)

    internal companion object {
        private const val HASH_FACTOR = 31
        private const val DECIMAL = 10

        internal fun zero(capacityBits: Int): Ibz = of(capacityBits, 0, 1)

        internal fun one(capacityBits: Int): Ibz = of(capacityBits, 1, 2)

        /** The reference `ibz_set`: `value` sign-truncated to `max(bitlen, 1)` bits. */
        internal fun of(capacityBits: Int, value: Int, bitlen: Int): Ibz {
            requireCapacity(capacityBits)
            require(bitlen in 0..Int.SIZE_BITS) { "Small bit length outside 0..32" }
            val bits = maxOf(bitlen, 1)
            val unused = Long.SIZE_BITS - bits
            val truncated = (value.toLong() shl unused) shr unused
            require(truncated != -1L shl (bits - 1)) { "Value is the excluded minimum" }
            return Ibz(capacityBits, bits, longArrayOf(truncated))
        }

        internal fun fromWords(capacityBits: Int, bitlen: Int, words: LongArray): Ibz {
            requireCapacity(capacityBits)
            require(bitlen in 1..capacityBits && words.size == limbs(bitlen)) {
                "Words do not match bit length"
            }
            return Ibz(capacityBits, bitlen, words.copyOf())
        }

        /** The low `bits` bits of unsigned [source] at bit length `bits + 1`. */
        internal fun copyBits(capacityBits: Int, source: LongArray, bits: Int): Ibz {
            requireCapacity(capacityBits)
            require(bits in 0 until capacityBits) { "Bit count outside capacity" }
            return create(capacityBits, bits + 1, false, IbzBits.truncate(source, bits))
        }

        /** Truncates a signed magnitude to `limbs(bitlen)` two's-complement words. */
        internal fun create(
            capacityBits: Int,
            bitlen: Int,
            negative: Boolean,
            magnitude: LongArray,
        ): Ibz {
            val truncated = magnitude.copyOf(limbs(bitlen))
            return Ibz(
                capacityBits,
                bitlen,
                if (negative) IbzWords.negate(truncated) else truncated,
            )
        }

        /** Whether the signed magnitude is representable in `width` two's-complement bits. */
        internal fun fits(negative: Boolean, magnitude: LongArray, width: Int): Boolean {
            val bits = IbzWords.bitLength(magnitude)
            return bits < width ||
                (negative && bits == width && IbzWords.trailingZeros(magnitude) == width - 1)
        }

        private fun requireCapacity(capacityBits: Int) =
            require(capacityBits > 0 && capacityBits % WORD_BITS == 0) {
                "Capacity must be whole words"
            }
    }
}
