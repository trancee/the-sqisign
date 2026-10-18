// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticFieldElement
import ch.trancee.sqisign.field.requireReferenceRoot

/** Complete algorithm 4.106 on a curve-bound full reference basis; variable-time. */
internal class BasisChange<Element : QuadraticFieldElement<Element>>
private constructor(
    private val curve: MontgomeryCurve<Element>,
    private val full: TorsionBasis<Element>,
    private val pairing: TatePairing<Element>,
) {
    private val field = curve.field
    private val coefficient = curve.montgomeryCoefficient()
    private val reference = liftBasis(curve, full)
    private val root = pairing.evaluate(full.exponent, full.first, full.second, full.difference)

    /** Returns the single-global-sign-normalized matrix from the reduced reference to target. */
    internal fun between(target: TorsionBasis<Element>): TorsionMatrix {
        target.requireCurve(curve)
        val lifted = liftBasis(curve, target)
        val values =
            listOf(
                pairing.evaluate(
                    target.exponent,
                    target.first,
                    full.second,
                    difference(lifted.first, reference.second),
                ),
                pairing.evaluate(
                    target.exponent,
                    target.second,
                    full.second,
                    difference(lifted.second, reference.second),
                ),
                pairing
                    .evaluate(
                        target.exponent,
                        target.first,
                        full.first,
                        difference(lifted.first, reference.first),
                    )
                    .inverse(),
                pairing
                    .evaluate(
                        target.exponent,
                        target.second,
                        full.first,
                        difference(lifted.second, reference.first),
                    )
                    .inverse(),
            )
        var generator = root
        repeat(full.exponent - target.exponent) { generator = generator.square() }
        val logarithms = PowerOfTwoDiscreteLog.prepare(field, target.exponent, generator)
        val entries = values.map { logarithms.solve(it) }
        return TorsionMatrix.of(entries[0], entries[1], entries[2], entries.last()).normalized()
    }

    private fun difference(
        left: AffinePoint<Element>,
        right: AffinePoint<Element>,
    ): MontgomeryPoint<Element> {
        if (left.x.isEqualTo(right.x)) {
            return if (left.y.isEqualTo(right.y)) MontgomeryPoint.infinity(field)
            else curve.double(MontgomeryPoint.fromCoordinates(field, left.x, field.one()))
        }
        val slope = left.y.add(right.y).multiply(left.x.subtract(right.x).inverse())
        val coordinate = slope.square().subtract(coefficient).subtract(left.x).subtract(right.x)
        return MontgomeryPoint.fromCoordinates(field, coordinate, field.one())
    }

    internal companion object {
        internal fun <Element : QuadraticFieldElement<Element>> prepare(
            curve: MontgomeryCurve<Element>,
            full: TorsionBasis<Element>,
        ): BasisChange<Element> = BasisChange(curve, full, TatePairing.prepare(curve, full))
    }
}

private class AffinePoint<Element>(val x: Element, val y: Element)

private class AffineBasis<Element>(
    val first: AffinePoint<Element>,
    val second: AffinePoint<Element>,
)

private fun <Element : QuadraticFieldElement<Element>> liftBasis(
    curve: MontgomeryCurve<Element>,
    basis: TorsionBasis<Element>,
): AffineBasis<Element> {
    val field = curve.field
    val first = basis.first.x.multiply(basis.first.z.inverse())
    val second = basis.second.x.multiply(basis.second.z.inverse())
    if (basis.exponent == 1) {
        return AffineBasis(AffinePoint(first, field.zero()), AffinePoint(second, field.zero()))
    }
    val difference = basis.difference.x.multiply(basis.difference.z.inverse())
    val coefficient = curve.montgomeryCoefficient()
    fun rightHandSide(x: Element): Element =
        x.multiply(x.square().add(coefficient.multiply(x)).add(field.one()))
    val firstRhs = rightHandSide(first)
    val secondRhs = rightHandSide(second)
    val firstY =
        field.requireReferenceRoot(firstRhs, "Basis generators require verified affine lifts")
    val numerator =
        difference
            .add(coefficient)
            .add(first)
            .add(second)
            .multiply(first.subtract(second).square())
            .subtract(firstRhs)
            .subtract(secondRhs)
    val secondY = numerator.multiply(firstY.add(firstY).inverse())
    return AffineBasis(AffinePoint(first, firstY), AffinePoint(second, secondY))
}
