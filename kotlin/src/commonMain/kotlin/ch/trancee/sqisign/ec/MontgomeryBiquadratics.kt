// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticFieldElement

internal class MontgomeryBiquadratics<Element : QuadraticFieldElement<Element>>(
    internal val xx: Element,
    internal val xz: Element,
    internal val zz: Element,
)

/** Equation (8): the roots are the x-coordinates of P-Q and P+Q. */
internal fun <Element : QuadraticFieldElement<Element>> montgomeryBiquadratics(
    numerator: Element,
    denominator: Element,
    left: MontgomeryPoint<Element>,
    right: MontgomeryPoint<Element>,
): MontgomeryBiquadratics<Element> {
    val productX = left.x.multiply(right.x)
    val productZ = left.z.multiply(right.z)
    val crossLeft = left.x.multiply(right.z)
    val crossRight = left.z.multiply(right.x)
    val curveTerm = numerator.multiply(productX).multiply(productZ)
    return MontgomeryBiquadratics(
        productX.subtract(productZ).square().multiply(denominator),
        productX
            .add(productZ)
            .multiply(crossLeft.add(crossRight))
            .multiply(denominator)
            .add(curveTerm.add(curveTerm)),
        crossLeft.subtract(crossRight).square().multiply(denominator),
    )
}
