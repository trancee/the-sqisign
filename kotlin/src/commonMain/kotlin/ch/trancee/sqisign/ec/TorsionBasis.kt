// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticFieldElement

/** An immutable, checked differential basis of E[2^exponent], not a canonical orientation. */
internal class TorsionBasis<Element : QuadraticFieldElement<Element>>
private constructor(
    internal val exponent: Int,
    internal val first: MontgomeryPoint<Element>,
    internal val second: MontgomeryPoint<Element>,
    internal val difference: MontgomeryPoint<Element>,
    private val coefficient: Element,
) {
    internal fun requireCurve(curve: MontgomeryCurve<Element>) {
        require(coefficient.isEqualTo(curve.montgomeryCoefficient())) {
            "Torsion basis must be bound to the supplied curve"
        }
    }

    /** Algorithm 3.7, with column action and one shared differential orientation. */
    internal fun applyMatrix(
        curve: MontgomeryCurve<Element>,
        matrix: TorsionMatrix,
    ): TorsionBasis<Element> {
        requireCurve(curve)
        require(matrix.exponent == exponent) {
            "Basis action requires matching basis and matrix exponents"
        }
        require(matrix.determinant().isOdd()) {
            "Basis action requires a matrix with odd determinant"
        }
        fun combination(left: TorsionScalar, right: TorsionScalar): MontgomeryPoint<Element> =
            curve.biscalarMultiply(
                first,
                second,
                difference,
                left.toLadderScalar(curve.field.parameters),
                right.toLadderScalar(curve.field.parameters),
            )
        return TorsionBasis(
            exponent,
            combination(matrix.topLeft, matrix.bottomLeft),
            combination(matrix.topRight, matrix.bottomRight),
            combination(
                matrix.topLeft.subtract(matrix.topRight),
                matrix.bottomLeft.subtract(matrix.bottomRight),
            ),
            coefficient,
        )
    }

    internal companion object {
        internal fun <Element : QuadraticFieldElement<Element>> canonical(
            curve: MontgomeryCurve<Element>,
            exponent: Int,
            reconstructionExponent: Int = 0,
        ): Pair<TorsionBasis<Element>, BasisHint> =
            generateTorsionBasis(curve, exponent, reconstructionExponent)

        internal fun <Element : QuadraticFieldElement<Element>> fromHint(
            curve: MontgomeryCurve<Element>,
            exponent: Int,
            hint: BasisHint,
        ): TorsionBasis<Element> = reconstructTorsionBasis(curve, exponent, hint)

        /** The fixed E0 generators, reduced in order before selecting the reference difference. */
        internal fun <Element : QuadraticFieldElement<Element>> canonicalE0(
            curve: MontgomeryCurve<Element>,
            exponent: Int,
        ): TorsionBasis<Element> {
            val field = curve.field
            require(exponent in 1..field.parameters.torsionExponent) {
                "Torsion exponent must be between one and the parameter maximum"
            }
            require(curve.montgomeryCoefficient().isEqualTo(field.zero())) {
                "Canonical E0 basis requires Montgomery coefficient zero"
            }
            val coordinates = e0BasisCoordinates(field.parameters)
            val points = coordinates.map {
                val point =
                    MontgomeryPoint.fromCoordinates(
                        field,
                        field.fromLittleEndian(it.hexToByteArray()),
                        field.one(),
                    )
                repeatedDouble(curve, point, field.parameters.torsionExponent - exponent)
            }
            val difference = curve.deterministicDifference(points[0], points[1])
            return checked(curve, exponent, points[0], points[1], difference)
        }

        internal fun <Element : QuadraticFieldElement<Element>> checked(
            curve: MontgomeryCurve<Element>,
            exponent: Int,
            first: MontgomeryPoint<Element>,
            second: MontgomeryPoint<Element>,
            difference: MontgomeryPoint<Element>,
        ): TorsionBasis<Element> {
            val field = first.field
            require(exponent in 1..field.parameters.torsionExponent) {
                "Torsion exponent must be between one and the parameter maximum"
            }
            val points = listOf(first, second, difference)
            require(points.none { it.isInfinity() }) { "Basis points must be finite" }
            val coefficient = curve.montgomeryCoefficient()
            require(points.all { isOnMontgomeryCurve(it, coefficient) }) {
                "Basis points must lie on the curve"
            }
            val firstTop = orderTwoPoint(curve, first, exponent)
            val secondTop = orderTwoPoint(curve, second, exponent)
            require(!firstTop.isEqualTo(secondTop)) { "Basis generators must be independent" }
            val biquadratics = montgomeryBiquadratics(coefficient, field.one(), first, second)
            val diagonal =
                biquadratics.zz
                    .multiply(difference.x.square())
                    .add(biquadratics.xx.multiply(difference.z.square()))
            val cross = biquadratics.xz.multiply(difference.x).multiply(difference.z)
            require(diagonal.isEqualTo(cross.add(cross))) {
                "Basis points must form a consistent differential triple"
            }
            return TorsionBasis(exponent, first, second, difference, coefficient)
        }
    }
}

internal fun <Element : QuadraticFieldElement<Element>> isOnMontgomeryCurve(
    point: MontgomeryPoint<Element>,
    coefficient: Element,
): Boolean {
    val product = point.x.multiply(point.z)
    return product
        .multiply(point.x.square().add(coefficient.multiply(product)).add(point.z.square()))
        .isSquare()
}

private fun <Element : QuadraticFieldElement<Element>> orderTwoPoint(
    curve: MontgomeryCurve<Element>,
    point: MontgomeryPoint<Element>,
    exponent: Int,
): MontgomeryPoint<Element> {
    val top = repeatedDouble(curve, point, exponent - 1)
    require(!top.isInfinity()) { "Basis generators must have the exact requested torsion order" }
    require(curve.double(top).isInfinity()) {
        "Basis generators must have the exact requested torsion order"
    }
    return top
}

internal fun <Element : QuadraticFieldElement<Element>> repeatedDouble(
    curve: MontgomeryCurve<Element>,
    point: MontgomeryPoint<Element>,
    exponent: Int,
): MontgomeryPoint<Element> {
    var result = point
    repeat(exponent) { result = curve.double(result) }
    return result
}
