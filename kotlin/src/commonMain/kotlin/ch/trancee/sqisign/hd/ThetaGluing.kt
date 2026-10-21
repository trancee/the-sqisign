// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

/** Cross-addition components `(u, v, w)` of `x(P)`, `x(Q)` and `x(P - Q)`. */
internal class BaryCoordinates<Element : QuadraticFieldElement<Element>>(
    internal val u: Element,
    internal val v: Element,
    internal val w: Element,
) {
    internal companion object {
        internal fun <Element : QuadraticFieldElement<Element>> of(
            p: MontgomeryPoint<Element>,
            q: MontgomeryPoint<Element>,
            difference: MontgomeryPoint<Element>,
        ): BaryCoordinates<Element> {
            val first = p.x.add(p.z).multiply(q.x.subtract(q.z))
            val second = p.x.subtract(p.z).multiply(q.x.add(q.z))
            val lambda = second.add(first)
            val mu = second.subtract(first)
            val lambdaZ = difference.z.multiply(lambda).square()
            val muX = mu.multiply(difference.x)
            val muXSquare = muX.square()
            val w = muX.multiply(mu.multiply(difference.z))
            return BaryCoordinates(muXSquare.add(lambdaZ), muXSquare.subtract(lambdaZ), w.add(w))
        }
    }
}

/**
 * Algorithms 4.117-4.119: the gluing `(2,2)`-isogeny `E1 x E2 -> A` with kernel `[4](K1, K2)`.
 *
 * [codomain] is the theta null point of `A` in regular (not dual) coordinates.
 */
internal class ThetaGluing<Element : QuadraticFieldElement<Element>>
private constructor(
    private val field: QuadraticField<Element>,
    private val matrix: List<List<Element>>,
    internal val codomain: ThetaPoint<Element>,
    private val inverseDualNull: ThetaPoint<Element>,
    internal val inverseImages: List<ThetaPoint<Element>>,
) {
    /** Algorithm 4.119 for `(P, 0)` or `(0, P)`, whose squared theta point has `t = 0`. */
    internal fun evaluateSpecial(point: CouplePoint<Element>): ThetaPoint<Element> {
        val squared = coupleTheta(matrix, point).toSquared()
        if (!squared.t.isEqualTo(field.zero()))
            throw ThetaChainRejection(ThetaChainFailure.GLUING_EVALUATION)
        return squared.times(inverseDualNull).hadamard()
    }

    /**
     * Algorithm 4.118: the image of the couple point with cross-addition components [first] and
     * [second], divided by an auxiliary image through [auxiliaryInverse]. The result is in dual
     * coordinates when [dual] is set.
     */
    internal fun evaluateBary(
        first: BaryCoordinates<Element>,
        second: BaryCoordinates<Element>,
        auxiliaryInverse: ThetaPoint<Element>,
        dual: Boolean,
    ): ThetaPoint<Element> {
        val sumX = first.u.multiply(second.u).add(first.v.multiply(second.v))
        val sum =
            ThetaPoint(
                sumX,
                first.u.multiply(second.w),
                first.w.multiply(second.u),
                first.w.multiply(second.w),
            )
        val difference =
            ThetaPoint(
                first.u.add(first.v).multiply(second.u.add(second.v)).subtract(sumX),
                first.v.multiply(second.w),
                first.w.multiply(second.v),
                field.zero(),
            )
        val left = applyMatrix(matrix, sum).squares()
        val right = applyMatrix(matrix, difference).squares()
        val image =
            ThetaPoint(
                    left.x.subtract(right.x),
                    left.y.subtract(right.y),
                    left.z.subtract(right.z),
                    left.t.subtract(right.t),
                )
                .hadamard()
                .times(auxiliaryInverse)
        return if (dual) image else image.hadamard()
    }

    internal companion object {
        /**
         * Algorithm 4.117 from the eight-torsion couple points [first] and [second]. [verify] adds
         * the reference check that both kernels double to independent two-torsion points.
         */
        internal fun <Element : QuadraticFieldElement<Element>> compute(
            curves: List<MontgomeryCurve<Element>>,
            first: CouplePoint<Element>,
            second: CouplePoint<Element>,
            verify: Boolean,
        ): ThetaGluing<Element> {
            val field = curves[0].field
            val firstFour = double(curves, first)
            val secondFour = double(curves, second)
            if (verify)
                verifyTwoTorsion(curves, double(curves, firstFour), double(curves, secondFour))
            val matrix = gluingMatrix(field, firstFour, secondFour)
            return fromSquared(
                field,
                matrix,
                coupleTheta(matrix, first).toSquared(),
                coupleTheta(matrix, second).toSquared(),
            )
        }

        /** The codomain checks and constants from the squared theta kernel images. */
        internal fun <Element : QuadraticFieldElement<Element>> fromSquared(
            field: QuadraticField<Element>,
            matrix: List<List<Element>>,
            first: ThetaPoint<Element>,
            second: ThetaPoint<Element>,
        ): ThetaGluing<Element> {
            val zero = field.zero()
            if (!(first.t.isEqualTo(zero) and second.t.isEqualTo(zero))) {
                throw ThetaChainRejection(ThetaChainFailure.GLUING_THETA)
            }
            val factors = listOf(first.x, second.x, first.y, second.z, first.z)
            if (factors.any { it.isEqualTo(zero) })
                throw ThetaChainRejection(ThetaChainFailure.GLUING_ZERO)
            val dual =
                ThetaPoint(
                    first.x.multiply(second.x),
                    first.y.multiply(second.x),
                    first.x.multiply(second.z),
                    zero,
                )
            val inverseDual = ThetaPoint(first.y.multiply(second.z), dual.z, dual.y, zero)
            val firstImage = first.z.multiply(inverseDual.z)
            val firstOther = first.x.multiply(inverseDual.x)
            val secondImage = second.y.multiply(inverseDual.y)
            val secondOther = second.x.multiply(inverseDual.x)
            return ThetaGluing(
                field,
                matrix,
                dual.hadamard(),
                inverseDual,
                listOf(
                    ThetaPoint(firstImage, firstImage, firstOther, firstOther),
                    ThetaPoint(secondImage, secondOther, secondImage, secondOther),
                ),
            )
        }

        private fun <Element : QuadraticFieldElement<Element>> double(
            curves: List<MontgomeryCurve<Element>>,
            point: CouplePoint<Element>,
        ): CouplePoint<Element> =
            CouplePoint(curves[0].double(point.first), curves[1].double(point.second))

        private fun <Element : QuadraticFieldElement<Element>> verifyTwoTorsion(
            curves: List<MontgomeryCurve<Element>>,
            first: CouplePoint<Element>,
            second: CouplePoint<Element>,
        ) {
            val torsion =
                isTwoTorsion(curves[0], first.first) and
                    isTwoTorsion(curves[1], first.second) and
                    isTwoTorsion(curves[0], second.first) and
                    isTwoTorsion(curves[1], second.second)
            val dependent = sameX(first.first, second.first) or sameX(first.second, second.second)
            if (!torsion or dependent) throw ThetaChainRejection(ThetaChainFailure.GLUING_TORSION)
        }

        /** `x (x^2 + A x z + z^2) = 0`, which excludes infinity because then `x^3 != 0`. */
        private fun <Element : QuadraticFieldElement<Element>> isTwoTorsion(
            curve: MontgomeryCurve<Element>,
            point: MontgomeryPoint<Element>,
        ): Boolean {
            val linear = curve.montgomeryCoefficient().multiply(point.x).multiply(point.z)
            val quadratic = point.x.square().add(linear).add(point.z.square())
            return point.x.multiply(quadratic).isEqualTo(curve.field.zero())
        }

        /** Projective x-equality of two finite points. */
        private fun <Element : QuadraticFieldElement<Element>> sameX(
            left: MontgomeryPoint<Element>,
            right: MontgomeryPoint<Element>,
        ): Boolean = left.x.multiply(right.z).isEqualTo(left.z.multiply(right.x))

        private fun <Element : QuadraticFieldElement<Element>> coupleTheta(
            matrix: List<List<Element>>,
            point: CouplePoint<Element>,
        ): ThetaPoint<Element> {
            val (first, second) = point.first to point.second
            return applyMatrix(
                matrix,
                ThetaPoint(
                    first.x.multiply(second.x),
                    first.x.multiply(second.z),
                    first.z.multiply(second.x),
                    first.z.multiply(second.z),
                ),
            )
        }

        private fun <Element : QuadraticFieldElement<Element>> applyMatrix(
            matrix: List<List<Element>>,
            point: ThetaPoint<Element>,
        ): ThetaPoint<Element> {
            val (top, bottom) =
                matrix.chunked(2).map { rows ->
                    rows.map { row ->
                        row.zip(point.coordinates, QuadraticFieldElement<Element>::multiply)
                            .reduce { sum, term ->
                                sum.add(term)
                            }
                    }
                }
            return ThetaPoint(top[0], top[1], bottom[0], bottom[1])
        }
    }
}
