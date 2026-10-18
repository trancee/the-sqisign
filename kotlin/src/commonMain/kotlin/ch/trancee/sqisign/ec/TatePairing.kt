// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticFieldElement

/** Pairing domain witnessed by a checked full torsion basis; not constant-time. */
internal class TatePairing<Element : QuadraticFieldElement<Element>>
private constructor(
    private val curve: MontgomeryCurve<Element>,
    fullBasis: TorsionBasis<Element>,
) {
    private val witness = listOf(fullBasis.first, fullBasis.second, fullBasis.difference)
    private val field = curve.field
    private val coefficient = curve.montgomeryCoefficient()
    private val two = field.one().add(field.one())
    private val a24 = coefficient.add(two).multiply(two.add(two).inverse())

    /** Algorithm 4.104; supplying P-Q instead of P+Q inverts the result. */
    internal fun evaluate(
        exponent: Int,
        first: MontgomeryPoint<Element>,
        second: MontgomeryPoint<Element>,
        sum: MontgomeryPoint<Element>,
    ): Element {
        require(exponent in 1..field.parameters.torsionExponent) {
            "Tate exponent must be between one and the parameter maximum"
        }
        require(
            listOf(first, second, sum).all {
                hasWitness(it) || isOnMontgomeryCurve(it, coefficient)
            },
        ) {
            "Tate operands must lie on the curve"
        }
        require(
            (exponent == field.parameters.torsionExponent && hasWitness(first)) ||
                isKilledByPowerOfTwo(first, exponent),
        ) {
            "Tate first operand must be killed by the requested power of two"
        }
        require(isConsistentSum(first, second, sum)) {
            "Tate operands must form a consistent sum or difference triple"
        }
        return when {
            first.isInfinity() || second.isInfinity() || first.isEqualTo(second) -> field.one()
            first.x.isEqualTo(field.zero()) -> quadraticCharacter(second)
            second.x.isEqualTo(field.zero()) ->
                if (exponent < field.parameters.torsionExponent) field.one()
                else quadraticCharacter(first)
            else -> evaluateCubical(exponent, first, second, sum)
        }
    }

    private fun hasWitness(point: MontgomeryPoint<Element>): Boolean = witness.any {
        it.isEqualTo(point)
    }

    private fun isKilledByPowerOfTwo(point: MontgomeryPoint<Element>, exponent: Int): Boolean {
        var multiple = point
        var remaining = exponent
        while (remaining > 0 && !multiple.isInfinity()) {
            multiple = cubicalDouble(multiple)
            remaining--
        }
        return multiple.isInfinity()
    }

    private fun quadraticCharacter(point: MontgomeryPoint<Element>): Element =
        if (point.x.multiply(point.z).isSquare()) field.one() else field.one().negate()

    private fun evaluateCubical(
        exponent: Int,
        first: MontgomeryPoint<Element>,
        second: MontgomeryPoint<Element>,
        sum: MontgomeryPoint<Element>,
    ): Element {
        val normalizedFirst = normalize(first)
        val normalizedSecond = normalize(second)
        var current = normalizedFirst
        var translated = normalize(sum)
        if (exponent > 1) {
            val inverseSecond = normalizedSecond.x.inverse()
            repeat(exponent - 1) {
                translated = cubicalAdd(translated, current, inverseSecond)
                current = cubicalDouble(current)
            }
        }
        val numerator = translate(translated, current).z
        val denominator = translate(current, current).x
        val normOne =
            field
                .conjugate(numerator)
                .multiply(denominator)
                .multiply(numerator.multiply(field.conjugate(denominator)).inverse())
        var result = clearOddCofactor(normOne)
        repeat(field.parameters.torsionExponent - exponent) { result = result.square() }
        return result
    }

    private fun isConsistentSum(
        first: MontgomeryPoint<Element>,
        second: MontgomeryPoint<Element>,
        sum: MontgomeryPoint<Element>,
    ): Boolean =
        when {
            first.isInfinity() -> sum.isEqualTo(second)
            second.isInfinity() -> sum.isEqualTo(first)
            first.isEqualTo(second) -> sum.isInfinity() || sum.isEqualTo(curve.double(first))
            sum.isInfinity() -> false
            else -> {
                val biquadratics = montgomeryBiquadratics(coefficient, field.one(), first, second)
                val diagonal =
                    biquadratics.zz
                        .multiply(sum.x.square())
                        .add(biquadratics.xx.multiply(sum.z.square()))
                val cross = biquadratics.xz.multiply(sum.x).multiply(sum.z)
                diagonal.isEqualTo(cross.add(cross))
            }
        }

    private fun normalize(point: MontgomeryPoint<Element>): MontgomeryPoint<Element> =
        MontgomeryPoint.fromCoordinates(field, point.x.multiply(point.z.inverse()), field.one())

    private fun cubicalDouble(point: MontgomeryPoint<Element>): MontgomeryPoint<Element> {
        val plus = point.x.add(point.z).square()
        val minus = point.x.subtract(point.z).square()
        val delta = plus.subtract(minus)
        return MontgomeryPoint.fromCoordinates(
            field,
            plus.multiply(minus),
            delta.multiply(minus.add(a24.multiply(delta))),
        )
    }

    private fun cubicalAdd(
        left: MontgomeryPoint<Element>,
        right: MontgomeryPoint<Element>,
        inverseDifference: Element,
    ): MontgomeryPoint<Element> {
        val first = left.x.add(left.z).multiply(right.x.subtract(right.z))
        val second = left.x.subtract(left.z).multiply(right.x.add(right.z))
        return MontgomeryPoint.fromCoordinates(
            field,
            first.add(second).square().multiply(inverseDifference),
            first.subtract(second).square(),
        )
    }

    private fun translate(
        point: MontgomeryPoint<Element>,
        torsion: MontgomeryPoint<Element>,
    ): MontgomeryPoint<Element> =
        when {
            torsion.isInfinity() -> point
            torsion.x.isEqualTo(field.zero()) ->
                MontgomeryPoint.fromCoordinates(field, point.z, point.x)
            else ->
                MontgomeryPoint.fromCoordinates(
                    field,
                    torsion.x.multiply(point.x).subtract(torsion.z.multiply(point.z)),
                    torsion.z.multiply(point.x).subtract(torsion.x.multiply(point.z)),
                )
        }

    private fun clearOddCofactor(value: Element): Element {
        var result = field.one()
        var power = value
        var remaining = field.parameters.cofactor
        while (remaining > 0) {
            if (remaining and 1 == 1) result = result.multiply(power)
            power = power.square()
            remaining = remaining ushr 1
        }
        return result
    }

    internal companion object {
        internal fun <Element : QuadraticFieldElement<Element>> prepare(
            curve: MontgomeryCurve<Element>,
            fullBasis: TorsionBasis<Element>,
        ): TatePairing<Element> {
            fullBasis.requireCurve(curve)
            require(fullBasis.exponent == curve.field.parameters.torsionExponent) {
                "Tate pairing requires a full parameter-order basis"
            }
            return TatePairing(curve, fullBasis)
        }
    }
}
