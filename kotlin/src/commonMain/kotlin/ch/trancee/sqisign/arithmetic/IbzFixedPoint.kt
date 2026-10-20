// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzWords.WORD_BITS
import ch.trancee.sqisign.arithmetic.IbzWords.limbs

/** Guard bits above the precision that hold the reciprocal iterate, as the reference `WBITS`. */
private const val RECIPROCAL_GUARD_BITS = 4

/** Extra bits of the scaled two, `2^(precision + 1)`, above the iterate width. */
private const val TWO_HEADROOM_BITS = 2

/** A fixed-point reciprocal `value ~ 2^(precision + scale) / d` with `scale = bitSize(d)`. */
internal class IbzReciprocal(internal val value: Ibz, internal val scale: Int)

/**
 * The reference `ibz_ct_shift` (and, for non-negative shifts, `ibz_ct_shl`): the exact left shift,
 * or arithmetic right shift by `-shift`, sign-truncated to [bound] bits. [maxShift] is the public
 * shift bound the reference ladder covers. Variable-time.
 */
internal fun Ibz.shiftSigned(shift: Int, maxShift: Int, bound: Int): Ibz {
    require(shift in -maxShift..maxShift) { "Shift exceeds its bound" }
    require(bound in 1..capacityBits) { "Bound outside capacity" }
    return narrowed(shifted(extended(limbs(maxOf(bitlen, bound))), shift), bound)
}

/**
 * The reference `ibz_ct_highmul_p`: `floor(this * other / 2^precision)` sign-truncated to [bound].
 */
internal fun Ibz.highProduct(other: Ibz, precision: Int, bound: Int): Ibz {
    require(precision >= 0) { "Precision must be non-negative" }
    return narrowed(shiftedRight(signedProduct(other, bound), precision), bound)
}

/** The reference `ibz_ct_highmul_s`: the exact product shifted as [shiftSigned], then narrowed. */
internal fun Ibz.scaledProduct(other: Ibz, shift: Int, maxShift: Int, bound: Int): Ibz {
    require(shift in -maxShift..maxShift) { "Shift exceeds its bound" }
    return narrowed(shifted(signedProduct(other, bound), shift), bound)
}

/**
 * The reference `ibz_ct_fp_recip`: [rounds] Newton steps `w <- w (2 - Dn w)` at scale [precision]
 * from the 64-bit seed `floor((2^127 - 1) / top64(Dn))`, where `Dn` is this positive value
 * normalised to exactly [precision] bits.
 */
internal fun Ibz.fixedPointReciprocal(precision: Int, rounds: Int): IbzReciprocal {
    require(!isNegative && !isZero) { "Reciprocal requires a positive value" }
    require(
        precision >= WORD_BITS &&
            precision + RECIPROCAL_GUARD_BITS + TWO_HEADROOM_BITS <= capacityBits,
    ) {
        "Precision outside capacity"
    }
    val scale = bitSize()
    val width = precision + RECIPROCAL_GUARD_BITS
    val normalised = shiftSigned(precision - scale, maxOf(precision, bitlen), width)
    val top = normalised.extractUnsigned64(precision - WORD_BITS)
    val seed = IbzKnuthDivision.divide(longArrayOf(-1L, Long.MAX_VALUE), longArrayOf(top)).first
    val seedShift = precision - WORD_BITS + 1
    var iterate =
        Ibz.copyBits(capacityBits, seed, WORD_BITS).shiftSigned(seedShift, seedShift, width)
    val two = Ibz.one(capacityBits).shiftLeft(precision + 1).withBound(width + TWO_HEADROOM_BITS)
    repeat(rounds) {
        val error =
            normalised.highProduct(iterate, precision, width).withBound(width + TWO_HEADROOM_BITS)
        iterate = iterate.highProduct(two - error, precision, width)
    }
    return IbzReciprocal(iterate, scale)
}

/**
 * The reference `ibz_ct_round_shift_limb`: `round(this / 2^precision)` with ties toward positive
 * infinity, both arguments whole words, keeping [bound] bits without re-masking.
 */
internal fun Ibz.roundShiftWords(precision: Int, bound: Int): Ibz {
    require(precision >= 0 && precision % WORD_BITS == 0) { "Precision must be whole words" }
    require(bound > 0 && bound % WORD_BITS == 0) { "Bound must be whole words" }
    require(precision + bound <= capacityBits) { "Bound outside capacity" }
    val words = withBound(precision + bound).toWords()
    val first = precision / WORD_BITS
    val round = if (first == 0) 0L else words[first - 1] ushr (WORD_BITS - 1)
    val sum = IbzWords.add(words.copyOfRange(first, words.size), longArrayOf(round))
    return Ibz.fromWords(capacityBits, bound, sum.copyOf(limbs(bound)))
}

/** The exact product in `limbs(bitlen) + limbs(other.bitlen) + 1` two's-complement words. */
private fun Ibz.signedProduct(other: Ibz, bound: Int): LongArray {
    require(capacityBits == other.capacityBits) { "Capacity mismatch" }
    val count = limbs(bitlen) + limbs(other.bitlen) + 1
    require(bound in 1..capacityBits && limbs(bound) <= count) { "Bound outside product" }
    val magnitude = IbzWords.multiply(magnitude(), other.magnitude()).copyOf(count)
    return if (isNegative != other.isNegative) IbzWords.negate(magnitude) else magnitude
}

private fun shifted(words: LongArray, shift: Int): LongArray =
    if (shift >= 0) IbzBits.shiftLeft(words, shift, words.size) else shiftedRight(words, -shift)

/** Arithmetic right shift of two's-complement words, keeping the word count. */
private fun shiftedRight(words: LongArray, exponent: Int): LongArray {
    val fill = if (words.last() < 0) -1L else 0L
    val extended =
        LongArray(words.size + exponent / WORD_BITS + 1) { index ->
            if (index < words.size) words[index] else fill
        }
    return IbzBits.shiftRight(extended, exponent).copyOf(words.size)
}

private fun Ibz.narrowed(words: LongArray, bound: Int): Ibz =
    Ibz.fromWords(capacityBits, bound, IbzBits.signExtend(words, bound))
