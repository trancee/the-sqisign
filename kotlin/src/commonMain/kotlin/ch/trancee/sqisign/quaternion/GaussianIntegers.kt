// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.minus
import ch.trancee.sqisign.arithmetic.plus
import ch.trancee.sqisign.arithmetic.unaryMinus

/*
 * The Gaussian integers `x0 + x1 i` of the reference `dim2.c` as two-entry `List<Ibz>`. Every
 * operation keeps the reference operation order, so result bit lengths match `ibz_t`.
 * Variable-time.
 */

/** `ibz_vec_2_gaussian_mul`, with three products. */
internal fun gaussianProduct(a: List<Ibz>, b: List<Ibz>): List<Ibz> {
    val r = (a[0] + a[1]) * b[0]
    val s = (a[0] - a[1]) * b[1]
    val t = (b[0] + b[1]) * a[1]
    return listOf(r - t, t + s)
}

/** `ibz_vec_2_gaussian_gcd`: the Euclidean algorithm with remainders of rounded quotients. */
internal fun gaussianGcd(a: List<Ibz>, b: List<Ibz>): List<Ibz> {
    var (q, r) = if (a.isZeroVector()) b to a else a to b
    while (!r.isZeroVector()) {
        val remainder = gaussianRemainder(q, r)
        q = r
        r = remainder
    }
    return q
}

/**
 * The remainder of `ibz_vec_2_gaussian_euclidean_division`, re-bounded like the reference to the
 * bit lengths of [b]. Honest operands with equal coordinate bit lengths keep their values.
 */
private fun gaussianRemainder(a: List<Ibz>, b: List<Ibz>): List<Ibz> {
    val norm = sumOfTwoSquares(b[0], b[1])
    val numerator = gaussianProduct(listOf(b[0], -b[1]), a)
    val quotient = numerator.map { it.roundedDivide(norm) }
    val product = gaussianProduct(quotient, b)
    return listOf(
        (a[0] - product[0]).withBound(b[0].bitlen),
        (a[1] - product[1]).withBound(b[1].bitlen),
    )
}
