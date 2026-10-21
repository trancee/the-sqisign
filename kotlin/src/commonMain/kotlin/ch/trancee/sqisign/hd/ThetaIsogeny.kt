// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

/**
 * A theta structure prepared for algorithm 4.111 doubling: the inverse dual null point and the
 * inverse squared null point, both projective.
 */
internal class ThetaStructure<Element : QuadraticFieldElement<Element>>(
    internal val inverseDualNull: ThetaPoint<Element>,
    private val inverseSquaredNull: ThetaPoint<Element>,
) {
    internal fun double(point: ThetaPoint<Element>, times: Int): ThetaPoint<Element> {
        var result = point
        repeat(times) {
            result =
                result
                    .toSquared()
                    .squares()
                    .times(inverseSquaredNull)
                    .hadamard()
                    .times(inverseDualNull)
        }
        return result
    }

    internal companion object {
        /** The structure of a theta null point in regular coordinates, as after the gluing. */
        internal fun <Element : QuadraticFieldElement<Element>> fromNull(
            nullPoint: ThetaPoint<Element>,
        ): ThetaStructure<Element> =
            ThetaStructure(nullPoint.inverse(), nullPoint.toSquared().inverse())
    }
}

/**
 * Algorithm 4.112: the generic `(2,2)`-isogeny with kernel `[4](K1, K2)` from the eight-torsion
 * theta points [first] and [second], given in dual coordinates when [dualDomain] is set.
 */
internal class ThetaIsogeny<Element : QuadraticFieldElement<Element>>
private constructor(
    private val dualDomain: Boolean,
    internal val codomain: ThetaStructure<Element>,
    internal val dualNull: ThetaPoint<Element>,
) {
    internal fun evaluate(point: ThetaPoint<Element>): ThetaPoint<Element> =
        (if (dualDomain) point.hadamard() else point).toSquared().times(codomain.inverseDualNull)

    internal companion object {
        /**
         * [verify] adds the reference checks that the codomain constants are nonzero and that the
         * kernel is isotropic.
         */
        internal fun <Element : QuadraticFieldElement<Element>> compute(
            field: QuadraticField<Element>,
            first: ThetaPoint<Element>,
            second: ThetaPoint<Element>,
            dualDomain: Boolean,
            verify: Boolean,
        ): ThetaIsogeny<Element> {
            val tt1 = (if (dualDomain) first.hadamard() else first).toSquared()
            val tt2 = (if (dualDomain) second.hadamard() else second).toSquared()
            val zero = field.zero()
            val factors = tt2.coordinates + listOf(tt1.x, tt1.y)
            if (verify and factors.any { it.isEqualTo(zero) })
                throw ThetaChainRejection(ThetaChainFailure.GENERIC)
            val t1 = tt1.y.multiply(tt2.t)
            val t2 = tt1.x.multiply(tt2.z)
            val inverseDual =
                ThetaPoint(
                    t1.multiply(tt2.z),
                    t2.multiply(tt2.t),
                    t1.multiply(tt2.x),
                    t2.multiply(tt2.y),
                )
            val isotropic = tt1.z.multiply(inverseDual.z).isEqualTo(tt1.t.multiply(inverseDual.t))
            if (verify and !isotropic) throw ThetaChainRejection(ThetaChainFailure.GENERIC)
            val shared = tt2.x.multiply(tt2.y)
            val dualNull =
                ThetaPoint(
                    shared.multiply(tt1.x),
                    shared.multiply(tt1.y),
                    inverseDual.t,
                    inverseDual.z,
                )
            return ThetaIsogeny(
                dualDomain,
                ThetaStructure(inverseDual, dualNull.toSquared().inverse()),
                dualNull,
            )
        }
    }
}
