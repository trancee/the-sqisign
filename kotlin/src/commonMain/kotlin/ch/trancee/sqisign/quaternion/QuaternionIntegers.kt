// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.abs
import ch.trancee.sqisign.arithmetic.compareTo
import ch.trancee.sqisign.arithmetic.divRem
import ch.trancee.sqisign.arithmetic.low32
import ch.trancee.sqisign.arithmetic.plus
import ch.trancee.sqisign.arithmetic.sqrtMinusOneModVerified

private const val RESIDUE_MASK = 3

/**
 * The reference `ibz_cornacchia_prime`: `(x, y)` with `x <= y` and `x^2 + y^2 = this`, or `null`
 * when this is not 1, 2, or `1 mod 4` with a square root of -1, or the reduced vector misses the
 * norm. Primes `1 mod 4` always succeed. Variable-time.
 */
internal fun Ibz.cornacchiaPrime(): TwoSquares? =
    when {
        compareTo(2) == 0 -> TwoSquares(Ibz.one(capacityBits), Ibz.one(capacityBits))
        compareTo(1) == 0 -> TwoSquares(Ibz.zero(capacityBits), Ibz.one(capacityBits))
        low32() and RESIDUE_MASK != 1 -> null
        else -> sqrtMinusOneModVerified()?.let { twoSquaresFromRoot(it) }
    }

/** The sorted reduced vector for a square [root] of -1, kept only when its norm is this modulus. */
internal fun Ibz.twoSquaresFromRoot(root: Ibz): TwoSquares? {
    val reduced = sumOfSquares(this, root)
    val sorted = if (reduced.x > reduced.y) TwoSquares(reduced.y, reduced.x) else reduced
    return sorted.takeIf { (it.x * it.x + it.y * it.y).compareTo(this) == 0 }
}

/**
 * The reference `ibz_rounded_div`: the quotient rounded to nearest, halves toward zero, at bit
 * length `max(quotient bitlen, 2) + 1`.
 */
internal fun Ibz.roundedDivide(divisor: Ibz): Ibz {
    val division = divRem(divisor)
    val twiceRemainder = division.remainder.abs() + division.remainder.abs()
    val adjustment =
        when {
            twiceRemainder <= divisor.abs() -> 0
            isNegative == divisor.isNegative -> 1
            else -> -1
        }
    return division.quotient + Ibz.of(capacityBits, adjustment, 2)
}

/** The reference `ibz_sum_two_squares`; throws [ArithmeticException] where the reference wraps. */
internal fun sumOfTwoSquares(a: Ibz, b: Ibz): Ibz = a * a + b * b
