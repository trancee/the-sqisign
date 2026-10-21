// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzWords.WORD_BITS
import ch.trancee.sqisign.arithmetic.IbzWords.limbs

/** Checked sum; throws [ArithmeticException] where the reference wraps at capacity. */
internal operator fun Ibz.plus(other: Ibz): Ibz = sum(other, subtract = false, checked = true)

/** Checked difference; throws [ArithmeticException] where the reference wraps at capacity. */
internal operator fun Ibz.minus(other: Ibz): Ibz = sum(other, subtract = true, checked = true)

internal operator fun Ibz.unaryMinus(): Ibz =
    Ibz.create(capacityBits, bitlen, !isNegative, magnitude())

internal fun Ibz.abs(): Ibz = Ibz.create(capacityBits, bitlen, false, magnitude())

internal operator fun Ibz.compareTo(other: Int): Int {
    require(other != Int.MIN_VALUE) { "Comparand must exceed Int.MIN_VALUE" }
    return compareTo(Ibz.of(capacityBits, other, Int.SIZE_BITS))
}

internal fun Ibz.pow(exponent: Int): Ibz {
    require(exponent >= 0) { "Exponent must be non-negative" }
    var result = Ibz.one(capacityBits)
    var base = this
    var remaining = exponent
    while (remaining != 0) {
        if (remaining and 1 == 1) {
            result *= base
        }
        remaining = remaining ushr 1
        if (remaining != 0) {
            base *= base
        }
    }
    return result
}

internal fun Ibz.plusWithBound(other: Ibz, bound: Int): Ibz =
    sum(other, subtract = false, checked = false).withBound(bound)

/** The unchecked difference re-bounded to [bound], as the reference `ibz_sub` and bound. */
internal fun Ibz.minusWithBound(other: Ibz, bound: Int): Ibz =
    sum(other, subtract = true, checked = false).withBound(bound)

/** The product re-bounded to [bound], as the reference `ibz_mul` and bound. */
internal fun Ibz.timesWithBound(other: Ibz, bound: Int): Ibz = times(other).withBound(bound)

internal fun Ibz.plusIntWithBound(value: Int, bound: Int): Ibz =
    plusWithBound(Ibz.of(capacityBits, value, Int.SIZE_BITS), bound)

/** Product with a machine integer computed in `64 * limbs(bound)` bits, then re-bounded. */
internal fun Ibz.timesIntWithBound(factor: Int, bound: Int): Ibz {
    require(bound in 1..capacityBits) { "Bound outside capacity" }
    val count = limbs(bound)
    val factorMagnitude = longArrayOf(kotlin.math.abs(factor.toLong()))
    val product = IbzWords.multiply(magnitude().copyOf(count), factorMagnitude).copyOf(count)
    return Ibz.create(capacityBits, count * WORD_BITS, isNegative != (factor < 0), product)
        .withBound(bound)
}
