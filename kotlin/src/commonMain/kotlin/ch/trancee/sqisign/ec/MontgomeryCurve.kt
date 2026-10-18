// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import ch.trancee.sqisign.field.requireReferenceRoot

/** A nonsingular projective Montgomery curve; arithmetic is variable-time. */
internal class MontgomeryCurve<Element : QuadraticFieldElement<Element>>
private constructor(
    internal val field: QuadraticField<Element>,
    private val numerator: Element,
    private val denominator: Element,
) {
    private val twiceDenominator: Element = denominator.add(denominator)
    private val doublingNumerator: Element = numerator.add(twiceDenominator)
    private val doublingDenominator: Element = twiceDenominator.add(twiceDenominator)

    internal fun montgomeryCoefficient(): Element = numerator.multiply(denominator.inverse())

    internal fun double(point: MontgomeryPoint<Element>): MontgomeryPoint<Element> {
        val sumSquare = point.x.add(point.z).square()
        val differenceSquare = point.x.subtract(point.z).square()
        val delta = sumSquare.subtract(differenceSquare)
        val scaledDifference = differenceSquare.multiply(doublingDenominator)
        return MontgomeryPoint.fromCoordinates(
            field,
            sumSquare.multiply(scaledDifference),
            delta.multiply(scaledDifference.add(delta.multiply(doublingNumerator))),
        )
    }

    /** Unsigned parameter-width little-endian scalar, without reduction; not constant-time. */
    internal fun multiply(
        point: MontgomeryPoint<Element>,
        scalar: ByteArray,
    ): MontgomeryPoint<Element> {
        validateScalar(scalar)
        // xADD excludes infinity and (0:1); parity suffices for both of these points.
        if (point.isInfinity() || point.x.isEqualTo(field.zero())) {
            return if (scalar[0].toInt() and 1 == 0) MontgomeryPoint.infinity(field) else point
        }
        var lower = MontgomeryPoint.infinity(field)
        var upper = point
        var started = false
        for (byteIndex in scalar.lastIndex downTo 0) {
            for (bitIndex in Byte.SIZE_BITS - 1 downTo 0) {
                val bit = scalar[byteIndex].toInt() ushr bitIndex and 1
                if (!started && bit == 0) continue
                started = true
                val sum = MontgomeryPoint.differentialAdd(lower, upper, point)
                if (bit == 0) {
                    lower = double(lower)
                    upper = sum
                } else {
                    upper = double(upper)
                    lower = sum
                }
            }
        }
        return lower
    }

    /** Algorithm 4.88: returns P + [scalar]Q for a consistent differential triple. */
    internal fun threePointMultiply(
        left: MontgomeryPoint<Element>,
        right: MontgomeryPoint<Element>,
        difference: MontgomeryPoint<Element>,
        scalar: ByteArray,
    ): MontgomeryPoint<Element> {
        validateScalar(scalar)
        require(listOf(left, right, difference).none { hasZeroCoordinate(it) }) {
            "Three-point ladder requires finite nonzero operands"
        }
        var result = left
        var multiple = right
        var other = difference
        for (byte in scalar) {
            for (bitIndex in 0 until Byte.SIZE_BITS) {
                if (byte.toInt() ushr bitIndex and 1 == 1) {
                    result = MontgomeryPoint.differentialAdd(multiple, result, other)
                } else {
                    other = MontgomeryPoint.differentialAdd(multiple, other, result)
                }
                multiple = double(multiple)
            }
        }
        return result
    }

    /** Algorithm 4.89 for positive scalars; zero coefficients reduce to the single ladder. */
    internal fun biscalarMultiply(
        left: MontgomeryPoint<Element>,
        right: MontgomeryPoint<Element>,
        difference: MontgomeryPoint<Element>,
        scalarLeft: ByteArray,
        scalarRight: ByteArray,
    ): MontgomeryPoint<Element> {
        validateScalar(scalarLeft)
        validateScalar(scalarRight)
        val operands = listOf(left, right, difference)
        require(operands.none { it.isInfinity() }) { "Biscalar ladder requires finite operands" }
        return when {
            operands.any { it.x.isEqualTo(field.zero()) } ->
                multiplyTwoTorsionBasis(left, right, difference, scalarLeft, scalarRight)
            scalarLeft.all { it == 0.toByte() } -> multiply(right, scalarRight)
            scalarRight.all { it == 0.toByte() } -> multiply(left, scalarLeft)
            else -> positiveBiscalarMultiply(left, right, difference, scalarLeft, scalarRight)
        }
    }

    private fun positiveBiscalarMultiply(
        left: MontgomeryPoint<Element>,
        right: MontgomeryPoint<Element>,
        difference: MontgomeryPoint<Element>,
        scalarLeft: ByteArray,
        scalarRight: ByteArray,
    ): MontgomeryPoint<Element> {
        val sum = MontgomeryPoint.differentialAdd(left, right, difference)
        require(!hasZeroCoordinate(sum)) { "Biscalar ladder requires a finite nonzero sum" }
        val (digits, swapInitial) = recodeBiscalar(scalarLeft, scalarRight)
        var lower = MontgomeryPoint.infinity(field)
        var middle = if (swapInitial) right else left
        var upper = sum
        var firstDifference = middle
        var otherDifference = if (swapInitial) left else right
        var sumDifference = upper
        var otherSumDifference = difference
        for (index in scalarLeft.size * Byte.SIZE_BITS - 1 downTo 0) {
            val secondDigit = digits[2 * index + 1]
            val weight = digits[2 * index] + secondDigit
            val doubled =
                double(
                    when (weight) {
                        0 -> lower
                        1 -> middle
                        else -> upper
                    },
                )
            if (secondDigit == 1) {
                val temporary = firstDifference
                firstDifference = otherDifference
                otherDifference = temporary
            }
            val nextMiddle =
                if (secondDigit == 0) {
                    MontgomeryPoint.differentialAdd(lower, middle, firstDifference)
                } else {
                    MontgomeryPoint.differentialAdd(middle, upper, firstDifference)
                }
            val nextUpper = MontgomeryPoint.differentialAdd(lower, upper, sumDifference)
            if (weight and 1 == 1) {
                val temporary = sumDifference
                sumDifference = otherSumDifference
                otherSumDifference = temporary
            }
            lower = doubled
            middle = nextMiddle
            upper = nextUpper
        }
        val paritySum = (scalarLeft[0].toInt() and 1) + (scalarRight[0].toInt() and 1)
        return when (paritySum) {
            0 -> lower
            1 -> middle
            else -> upper
        }
    }

    /** Algorithm 4.92: selects one of P-Q and P+Q independently of projective scaling. */
    internal fun deterministicDifference(
        left: MontgomeryPoint<Element>,
        right: MontgomeryPoint<Element>,
    ): MontgomeryPoint<Element> {
        val coefficients = montgomeryBiquadratics(numerator, denominator, left, right)
        val bzz = coefficients.zz
        require(!bzz.isEqualTo(field.zero())) {
            "Deterministic difference requires distinct x-coordinates"
        }
        val scale =
            denominator.multiply(field.conjugate(denominator)).multiply(field.conjugate(bzz))
        val scaledMiddle = coefficients.xz.multiply(scale)
        val scaledDenominator = bzz.multiply(scale)
        val discriminant =
            scaledMiddle
                .square()
                .subtract(coefficients.xx.multiply(scale).multiply(scaledDenominator))
        val root =
            field.requireReferenceRoot(
                discriminant,
                "Deterministic difference requires a square discriminant",
            )
        return MontgomeryPoint.fromCoordinates(
            field,
            scaledMiddle.subtract(root),
            scaledDenominator,
        )
    }

    /**
     * Algorithm 4.93; consistent translates and a nondegenerate common-root elimination required.
     */
    internal fun sharedDifference(
        left: MontgomeryPoint<Element>,
        right: MontgomeryPoint<Element>,
        translatedLeft: MontgomeryPoint<Element>,
        translatedRight: MontgomeryPoint<Element>,
    ): MontgomeryPoint<Element> {
        val original = montgomeryBiquadratics(numerator, denominator, left, right)
        val translated =
            montgomeryBiquadratics(numerator, denominator, translatedLeft, translatedRight)
        val numerator =
            translated.xz.multiply(original.xx).subtract(original.xz.multiply(translated.xx))
        val denominator =
            translated.zz.multiply(original.xx).subtract(original.zz.multiply(translated.xx))
        return MontgomeryPoint.fromCoordinates(field, numerator.add(numerator), denominator)
    }

    private fun hasZeroCoordinate(point: MontgomeryPoint<Element>): Boolean =
        point.isInfinity() || point.x.isEqualTo(field.zero())

    private fun validateScalar(scalar: ByteArray) {
        require(scalar.size == field.parameters.fieldBytes) {
            "Montgomery scalars require exactly ${field.parameters.fieldBytes} bytes"
        }
    }

    private fun multiplyTwoTorsionBasis(
        left: MontgomeryPoint<Element>,
        right: MontgomeryPoint<Element>,
        difference: MontgomeryPoint<Element>,
        scalarLeft: ByteArray,
        scalarRight: ByteArray,
    ): MontgomeryPoint<Element> {
        require(
            listOf(left, right, difference).all { double(it).isInfinity() } &&
                !left.isEqualTo(right) &&
                !left.isEqualTo(difference) &&
                !right.isEqualTo(difference),
        ) {
            "Exceptional biscalar operands must form a distinct two-torsion basis"
        }
        val leftOdd = scalarLeft[0].toInt() and 1 == 1
        val rightOdd = scalarRight[0].toInt() and 1 == 1
        return if (leftOdd && rightOdd) difference
        else if (leftOdd) left else if (rightOdd) right else MontgomeryPoint.infinity(field)
    }

    internal companion object {
        internal fun <Element : QuadraticFieldElement<Element>> fromCoefficients(
            field: QuadraticField<Element>,
            numerator: Element,
            denominator: Element,
        ): MontgomeryCurve<Element> {
            require(!denominator.isEqualTo(field.zero())) {
                "Montgomery curve denominator must be nonzero"
            }
            val twiceDenominator = denominator.add(denominator)
            require(!numerator.square().isEqualTo(twiceDenominator.square())) {
                "Montgomery curve must be nonsingular"
            }
            return MontgomeryCurve(field, numerator, denominator)
        }
    }
}
