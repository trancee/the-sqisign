// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticFieldElement

/** Algorithms 4.97-4.98: an x-only four-isogeny and its evaluation constants; variable-time. */
internal class FourIsogeny<Element : QuadraticFieldElement<Element>>
private constructor(
    internal val codomain: MontgomeryCurve<Element>,
    private val quadrupledSquare: Element,
    private val difference: Element,
    private val sum: Element,
) {
    internal fun evaluate(point: MontgomeryPoint<Element>): MontgomeryPoint<Element> {
        val pointSum = point.x.add(point.z)
        val pointDifference = point.x.subtract(point.z)
        val left = pointSum.multiply(difference)
        val right = pointDifference.multiply(sum)
        val scaledProduct = pointSum.multiply(pointDifference).multiply(quadrupledSquare)
        val outerSquare = left.add(right).square()
        val innerSquare = left.subtract(right).square()
        return MontgomeryPoint.fromCoordinates(
            codomain.field,
            scaledProduct.add(outerSquare).multiply(outerSquare),
            innerSquare.multiply(scaledProduct.subtract(innerSquare)),
        )
    }

    internal companion object {
        /** Rejects kernels without exact order four and kernels above `(0:1)`. */
        internal fun <Element : QuadraticFieldElement<Element>> from(
            curve: MontgomeryCurve<Element>,
            kernel: MontgomeryPoint<Element>,
        ): FourIsogeny<Element> {
            curve.requireNonsingularFourTorsion(kernel)
            val xSquare = kernel.x.square()
            val zSquare = kernel.z.square()
            val doublingNumerator = zSquare.add(xSquare).multiply(zSquare.subtract(xSquare))
            val doublingDenominator = zSquare.square()
            val quadrupled = doublingNumerator.add(doublingNumerator).let { it.add(it) }
            val twiceDenominator = doublingDenominator.add(doublingDenominator)
            val twiceSquare = zSquare.add(zSquare)
            return FourIsogeny(
                MontgomeryCurve.fromCoefficients(
                    curve.field,
                    quadrupled.subtract(twiceDenominator),
                    doublingDenominator,
                ),
                twiceSquare.add(twiceSquare),
                kernel.x.subtract(kernel.z),
                kernel.x.add(kernel.z),
            )
        }
    }
}
