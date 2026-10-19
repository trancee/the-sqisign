// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzWords.limbs

/** Logical right shift of the stored words, as the reference `ibz_div_2exp`. */
internal fun Ibz.shiftRight(exponent: Int): Ibz {
    require(exponent >= 0) { "Shift must be non-negative" }
    if (exponent == 0) {
        return this
    }
    val bits = if (exponent < bitlen) bitlen - exponent else 1
    return Ibz.create(capacityBits, bits, false, IbzBits.shiftRight(toWords(), exponent))
}

internal fun Ibz.shiftLeft(exponent: Int): Ibz {
    require(exponent >= 0) { "Shift must be non-negative" }
    if (exponent == 0) {
        return this
    }
    val requested = bitlen.toLong() + exponent
    val bits = minOf(requested, capacityBits.toLong()).toInt()
    val source = magnitude()
    val clamped = requested > capacityBits
    if (clamped && IbzWords.bitLength(source).toLong() + exponent > capacityBits) {
        throw ArithmeticException("ibz shift exceeds $capacityBits bits")
    }
    val shifted = IbzBits.shiftLeft(source, exponent, limbs(bits))
    if (clamped && !Ibz.fits(isNegative, shifted, capacityBits)) {
        throw ArithmeticException("ibz shift exceeds $capacityBits bits")
    }
    return Ibz.create(capacityBits, bits, isNegative, shifted)
}

/** The non-negative residue modulo `2^exponent` at bit length `exponent + 1`. */
internal fun Ibz.mod2exp(exponent: Int): Ibz {
    require(exponent >= 0 && exponent < capacityBits) { "Exponent outside capacity" }
    val residue = IbzBits.truncate(extended(limbs(exponent)), exponent)
    return Ibz.create(capacityBits, exponent + 1, false, residue)
}

internal fun Ibz.twoAdic(): Int = IbzWords.trailingZeros(toWords())

internal fun Ibz.bitSize(): Int = IbzWords.bitLength(magnitude())

/** The low 32 bits of the stored two's-complement words. */
internal fun Ibz.low32(): Int = extended(1)[0].toInt()
