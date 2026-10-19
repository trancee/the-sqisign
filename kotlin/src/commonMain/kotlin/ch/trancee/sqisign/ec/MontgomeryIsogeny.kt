// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

/**
 * Algorithm 4.96: the maximal coefficient among this curve's six isomorphic Montgomery models.
 *
 * Requires an exact order-four x-coordinate with `[2]P != (0:1)`; only the curve is returned, not a
 * point map. Variable-time.
 */
internal fun <Element : QuadraticFieldElement<Element>> MontgomeryCurve<Element>.normalize(
    fourTorsion: MontgomeryPoint<Element>,
): MontgomeryCurve<Element> {
    requireNonsingularFourTorsion(fourTorsion)
    val sum = fourTorsion.x.add(fourTorsion.z)
    val difference = fourTorsion.x.subtract(fourTorsion.z)
    val rotated = difference.multiply(imaginaryUnit(field))
    val numerators = mutableListOf<Element>()
    val denominators = mutableListOf<Element>()
    for ((first, second) in
        listOf(
            sum to difference,
            sum.add(difference) to sum.subtract(difference),
            sum.add(rotated) to sum.subtract(rotated),
        )) {
        val firstFourth = first.square().square()
        val secondFourth = second.square().square()
        val total = firstFourth.add(secondFourth)
        numerators += total.add(total)
        // Nonzero: a fourth-root-of-unity theta ratio needs x in {inf, 0, +-1, +-i}, all rejected.
        denominators += secondFourth.subtract(firstFourth)
    }
    val inverses = batchInverse(denominators)
    val coefficients =
        numerators.zip(inverses) { numerator, inverse -> numerator.multiply(inverse) }
    var maximum = coefficients[0]
    for (candidate in coefficients + coefficients.map { it.negate() }) {
        if (maximum.compareTo(candidate) < 0) maximum = candidate
    }
    return MontgomeryCurve.fromCoefficients(field, maximum, field.one())
}

/**
 * Algorithm 4.99 with an empty evaluation list: the normalized codomain of the `2^exponent`-isogeny
 * with kernel `<[4]kernel>`, where [kernel] must have exact order `2^(exponent + 2)`.
 *
 * Every four-isogeny step validates its kernel; for valid inputs only the first can fail. Spec and
 * reference callers use only the curve, which is normalized; intermediate point images are not
 * exposed because they lie on the unnormalized model. Variable-time.
 */
internal fun <Element : QuadraticFieldElement<Element>> MontgomeryCurve<Element>.twoIsogenyChain(
    kernel: MontgomeryPoint<Element>,
    exponent: Int,
): MontgomeryCurve<Element> {
    require(exponent > 0 && exponent % 2 == 0 && exponent <= field.parameters.torsionExponent) {
        "Two-isogeny chains require a positive even length at most the torsion exponent"
    }
    var curve = this
    val points = mutableListOf(kernel)
    val orders = mutableListOf(exponent + 2)
    repeat(exponent / 2) {
        while (orders.last() != 2) {
            val previous = orders.last()
            val doublings = previous / 4 * 2 + previous % 2
            var point = points.last()
            repeat(doublings) { point = curve.double(point) }
            points += point
            orders += previous - doublings
        }
        val step = FourIsogeny.from(curve, points.removeAt(points.lastIndex))
        orders.removeAt(orders.lastIndex)
        curve = step.codomain
        for (index in points.indices) {
            points[index] = step.evaluate(points[index])
            orders[index] -= 2
        }
    }
    return curve.normalize(points.single())
}

internal fun <Element : QuadraticFieldElement<Element>> MontgomeryCurve<Element>
    .requireNonsingularFourTorsion(point: MontgomeryPoint<Element>) {
    val twice = double(point)
    require(!twice.isInfinity() && double(twice).isInfinity()) {
        "Four-isogeny kernels require exact order four"
    }
    require(!twice.x.isEqualTo(field.zero())) {
        "Four-isogeny kernels must not lie above (0:1)"
    }
}

internal fun <Element : QuadraticFieldElement<Element>> batchInverse(
    values: List<Element>,
): List<Element> {
    val (first, second, third) = values
    val firstPair = first.multiply(second)
    val inverse = firstPair.multiply(third).inverse()
    val pairInverse = inverse.multiply(third)
    return listOf(
        pairInverse.multiply(second),
        pairInverse.multiply(first),
        inverse.multiply(firstPair),
    )
}

internal fun <Element : QuadraticFieldElement<Element>> imaginaryUnit(
    field: QuadraticField<Element>,
): Element =
    field.fromLittleEndian(
        ByteArray(field.parameters.extensionFieldBytes).also {
            it[field.parameters.fieldBytes] = 1
        },
    )
