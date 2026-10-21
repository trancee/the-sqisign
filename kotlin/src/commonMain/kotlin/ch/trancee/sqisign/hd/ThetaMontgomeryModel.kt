// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.batchInverse
import ch.trancee.sqisign.ec.imaginaryUnit
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

/**
 * A normalized Montgomery coefficient with the x-only isomorphism `(x : z) -> (Ma x + Mb z : Mc x +
 * Md z)` from an elliptic theta structure; [matrix] is `[Ma, Mb, Mc, Md]`.
 */
internal class ThetaMontgomeryModel<Element : QuadraticFieldElement<Element>>(
    internal val coefficient: Element,
    internal val matrix: List<Element>,
) {
    internal fun map(x: Element, z: Element): Pair<Element, Element> {
        val (top, bottom) =
            matrix.chunked(2).map { (left, right) -> x.multiply(left).add(z.multiply(right)) }
        return top to bottom
    }
}

/**
 * Algorithm 4.124 in the reference candidate order: the maximal Montgomery coefficient among the
 * six models of the elliptic theta null point `(a : b)`, or `null` when a model is singular.
 */
internal fun <Element : QuadraticFieldElement<Element>> thetaToMontgomery(
    field: QuadraticField<Element>,
    a: Element,
    b: Element,
): ThetaMontgomeryModel<Element>? {
    val minusI = imaginaryUnit(field).negate()
    val ia = a.multiply(minusI)
    val ib = b.multiply(minusI)
    val numerators = mutableListOf<Element>()
    val denominators = mutableListOf<Element>()
    for ((x, z) in listOf(a to b, a.add(b) to a.subtract(b), ia.add(b) to a.add(ib))) {
        val xFourth = x.square().square()
        val zFourth = z.square().square()
        val sum = xFourth.add(zFourth)
        numerators += sum.add(sum)
        denominators += xFourth.subtract(zFourth)
    }
    if (denominators.any { it.isEqualTo(field.zero()) }) return null
    val coefficients =
        numerators.zip(batchInverse(denominators)) { numerator, inverse ->
            numerator.multiply(inverse)
        }
    val candidates = coefficients + coefficients.map { it.negate() }
    var index = 0
    for (candidate in 1 until candidates.size) {
        if (candidates[index].compareTo(candidates[candidate]) < 0) index = candidate
    }
    val minusA = a.negate()
    val minusB = b.negate()
    return ThetaMontgomeryModel(
        candidates[index],
        listOf(
            listOf(b, minusA, ia, b, minusA, ia)[index],
            listOf(a, b, ib, a, b, ib)[index],
            listOf(b, b, minusB, minusB, minusB, b)[index],
            listOf(minusA, minusA, a, a, a, minusA)[index],
        ),
    )
}
