// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticFieldElement

/** Algorithm 3.9's normalized correspondence and its two canonical-basis hints. */
internal class BasisCorrespondence(
    internal val matrix: TorsionMatrix,
    internal val firstHint: BasisHint,
    internal val secondHint: BasisHint,
)

/** Both curves satisfy the canonical generator's maximality precondition. */
internal fun <Element : QuadraticFieldElement<Element>> setChangeOfBasisMatrix(
    firstCurve: MontgomeryCurve<Element>,
    firstBasis: TorsionBasis<Element>,
    secondCurve: MontgomeryCurve<Element>,
    secondBasis: TorsionBasis<Element>,
): BasisCorrespondence {
    firstBasis.requireCurve(firstCurve)
    secondBasis.requireCurve(secondCurve)
    require(firstBasis.exponent == secondBasis.exponent) {
        "Basis correspondence requires matching torsion exponents"
    }
    val exponent = firstBasis.exponent
    val firstMaximum = firstCurve.field.parameters.torsionExponent
    val secondMaximum = secondCurve.field.parameters.torsionExponent
    val (firstCanonical, firstHint) =
        TorsionBasis.canonical(
            firstCurve,
            firstMaximum,
            if (exponent < firstMaximum) exponent else 0,
        )
    val (secondCanonical, secondHint) =
        TorsionBasis.canonical(
            secondCurve,
            secondMaximum,
            if (exponent < secondMaximum) exponent else 0,
        )
    val firstMatrix = BasisChange.prepare(firstCurve, firstCanonical).between(firstBasis)
    val aligned = secondBasis.applyMatrix(secondCurve, firstMatrix.inverse())
    val secondMatrix = BasisChange.prepare(secondCurve, secondCanonical).between(aligned)
    return BasisCorrespondence(secondMatrix, firstHint, secondHint)
}
