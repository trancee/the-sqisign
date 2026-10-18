// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticFieldElement
import ch.trancee.sqisign.field.requireReferenceRoot

/** Algorithm 4.94 on maximal curves; the returned basis is independently checked. */
internal fun <Element : QuadraticFieldElement<Element>> generateTorsionBasis(
    curve: MontgomeryCurve<Element>,
    exponent: Int,
    reconstructionExponent: Int,
): Pair<TorsionBasis<Element>, BasisHint> {
    val field = curve.field
    require(exponent in 1..field.parameters.torsionExponent) {
        "Torsion exponent must be between one and the parameter maximum"
    }
    require(reconstructionExponent in 0 until exponent) {
        "Basis reconstruction exponent must be nonnegative and below the torsion exponent"
    }
    val coefficient = curve.montgomeryCoefficient()
    if (coefficient.isEqualTo(field.zero())) {
        val initial = TorsionBasis.canonicalE0(curve, exponent)
        val basis =
            if (reconstructionExponent == 0) initial
            else
                TorsionBasis.checked(
                    curve,
                    exponent,
                    initial.first,
                    initial.second,
                    orientReducedDifference(
                        curve,
                        initial.first,
                        initial.second,
                        initial.difference,
                        exponent - reconstructionExponent,
                    ),
                )
        return basis to BasisHint.fromEncoded(0)
    }
    val squareCoefficient = coefficient.isSquare()
    val index = ByteArray(field.parameters.fieldBytes)
    while (true) {
        incrementBasisSearchIndex(index)
        val integer =
            field.fromLittleEndian(
                ByteArray(field.parameters.extensionFieldBytes).also { index.copyInto(it) },
            )
        if (squareCoefficient && field.isSquareInBaseField(field.one().add(integer.square()))) {
            continue
        }
        val candidate = basisCandidate(curve, coefficient, integer, squareCoefficient)
        if (isOnMontgomeryCurve(candidate, coefficient)) {
            val basis =
                torsionBasisFromCandidate(curve, exponent, reconstructionExponent, candidate)
            return basis to BasisHint.fromSearchIndex(index, squareCoefficient)
        }
    }
}

internal fun <Element : QuadraticFieldElement<Element>> reconstructTorsionBasis(
    curve: MontgomeryCurve<Element>,
    exponent: Int,
    hint: BasisHint,
): TorsionBasis<Element> {
    val field = curve.field
    require(exponent in 1..field.parameters.torsionExponent) {
        "Torsion exponent must be between one and the parameter maximum"
    }
    val coefficient = curve.montgomeryCoefficient()
    if (coefficient.isEqualTo(field.zero()) || hint.requiresRegeneration) {
        return generateTorsionBasis(curve, exponent, 0).first
    }
    require(hint.searchIndex > 0) {
        "Basis hint must select a positive search index or request regeneration"
    }
    val integer =
        field.fromLittleEndian(
            ByteArray(field.parameters.extensionFieldBytes).also {
                it[0] = hint.searchIndex.toByte()
            },
        )
    val candidate = basisCandidate(curve, coefficient, integer, hint.claimsSquareCoefficient)
    require(isOnMontgomeryCurve(candidate, coefficient)) {
        "Basis hint does not select a point on the curve"
    }
    return torsionBasisFromCandidate(curve, exponent, 0, candidate)
}

private fun <Element : QuadraticFieldElement<Element>> basisCandidate(
    curve: MontgomeryCurve<Element>,
    coefficient: Element,
    integer: Element,
    squareCoefficient: Boolean,
): MontgomeryPoint<Element> {
    val field = curve.field
    return if (squareCoefficient) {
        val denominator =
            ByteArray(field.parameters.extensionFieldBytes).also {
                it[0] = 1
                integer
                    .toLittleEndian()
                    .copyInto(
                        it,
                        field.parameters.fieldBytes,
                        endIndex = field.parameters.fieldBytes,
                    )
            }
        MontgomeryPoint.fromCoordinates(
            field,
            coefficient.negate(),
            field.fromLittleEndian(denominator),
        )
    } else {
        MontgomeryPoint.fromCoordinates(field, coefficient.multiply(integer), field.one())
    }
}

private fun incrementBasisSearchIndex(index: ByteArray) {
    // The preceding canonical field decode proves h < p: h+1 cannot overflow this width.
    // Reaching p is rejected by the next strict decode, before any counter wrap.
    var carry = 1
    for (position in index.indices) {
        val sum = (index[position].toInt() and 0xff) + carry
        index[position] = sum.toByte()
        carry = sum ushr Byte.SIZE_BITS
    }
}

private fun <Element : QuadraticFieldElement<Element>> torsionBasisFromCandidate(
    curve: MontgomeryCurve<Element>,
    exponent: Int,
    reconstructionExponent: Int,
    candidate: MontgomeryPoint<Element>,
): TorsionBasis<Element> {
    val field = curve.field
    val partner =
        MontgomeryPoint.fromCoordinates(
            field,
            candidate.x.add(curve.montgomeryCoefficient().multiply(candidate.z)).negate(),
            candidate.z,
        )
    val cofactor =
        ByteArray(field.parameters.fieldBytes).also {
            it[0] = field.parameters.cofactor.toByte()
        }
    val discardedBits = field.parameters.torsionExponent - exponent
    val first = repeatedDouble(curve, curve.multiply(candidate, cofactor), discardedBits)
    val other = repeatedDouble(curve, curve.multiply(partner, cofactor), discardedBits)
    val initialDifference = curve.deterministicDifference(first, other)
    val difference =
        if (reconstructionExponent == 0) initialDifference
        else
            orientReducedDifference(
                curve,
                first,
                other,
                initialDifference,
                exponent - reconstructionExponent,
            )
    return TorsionBasis.checked(curve, exponent, first, difference, other)
}

private fun <Element : QuadraticFieldElement<Element>> orientReducedDifference(
    curve: MontgomeryCurve<Element>,
    first: MontgomeryPoint<Element>,
    other: MontgomeryPoint<Element>,
    difference: MontgomeryPoint<Element>,
    discardedBits: Int,
): MontgomeryPoint<Element> {
    val sum = MontgomeryPoint.differentialAdd(first, other, difference)
    val lowDifference = repeatedDouble(curve, difference, discardedBits)
    val lowSum = repeatedDouble(curve, sum, discardedBits)
    val product = lowDifference.z.multiply(lowSum.z)
    val delta = lowDifference.x.multiply(lowSum.z).subtract(lowDifference.z.multiply(lowSum.x))
    val signedValue = delta.multiply(product).multiply(curve.field.conjugate(product).square())
    val referenceRoot =
        curve.field.requireReferenceRoot(
            signedValue.square(),
            "Basis orientation requires a square discriminant",
        )
    return if (referenceRoot.isEqualTo(signedValue)) sum else difference
}
