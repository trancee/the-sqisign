// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/** Truncated quotient at the dividend bit length and remainder at the divisor bit length. */
internal class IbzDivision(internal val quotient: Ibz, internal val remainder: Ibz)

/**
 * Division rounding toward zero; the remainder takes the dividend sign, as the reference `ibz_div`.
 */
internal fun Ibz.divRem(divisor: Ibz): IbzDivision {
    require(capacityBits == divisor.capacityBits) { "Capacity mismatch" }
    if (divisor.isZero) {
        throw ArithmeticException("ibz division by zero")
    }
    val (quotient, remainder) = IbzKnuthDivision.divide(magnitude(), divisor.magnitude())
    return IbzDivision(
        Ibz.create(capacityBits, bitlen, isNegative != divisor.isNegative, quotient),
        Ibz.create(capacityBits, divisor.bitlen, isNegative, remainder),
    )
}

/** The residue in `[0, |modulus|)`; a corrected residue has bit length `modulus.bitlen + 1`. */
internal fun Ibz.mod(modulus: Ibz): Ibz {
    val remainder = divRem(modulus).remainder
    return if (remainder.isNegative) remainder.sum(modulus, modulus.isNegative, checked = false)
    else remainder
}

/** Whether [divisor] divides this value. */
internal fun Ibz.divides(divisor: Ibz): Boolean = divRem(divisor).remainder.isZero

/** The residue modulo an unsigned machine word. */
internal fun Ibz.modUnsigned(divisor: ULong): ULong {
    if (divisor == 0UL) {
        throw ArithmeticException("ibz division by zero")
    }
    return mod(Ibz.copyBits(capacityBits, longArrayOf(divisor.toLong()), ULong.SIZE_BITS))
        .toWords()[0]
        .toULong()
}
