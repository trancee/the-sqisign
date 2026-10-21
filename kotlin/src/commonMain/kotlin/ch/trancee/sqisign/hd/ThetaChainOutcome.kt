// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.field.QuadraticFieldElement

/** Which codomain factors to compute; [reference] is the C `mode` argument. */
internal enum class ThetaChainMode(internal val reference: Int) {
    /** Both factors, without the verification checks. */
    BOTH(0),
    /** Only the first factor `E1`, without the verification checks. */
    FIRST(1),
    /** Only the second factor `E2`, without the verification checks. */
    SECOND(2),
    /** Only the first factor, with every verification check of the reference. */
    VERIFY(-1),
}

/** Why a chain was rejected; [reason] names the reference diagnostic it corresponds to. */
internal enum class ThetaChainFailure(internal val reason: String) {
    GLUING_TORSION("gluing-torsion"),
    GLUING_THETA("gluing-theta"),
    GLUING_ZERO("gluing-zero"),
    GLUING_EVALUATION("gluing-evaluation"),
    GENERIC("generic"),
    SPLITTING_ZERO("splitting-zero"),
    SPLITTING_PRODUCT("splitting-product"),
    POINT_PRODUCT("point-product"),
    SPLITTING_INVALID("splitting-invalid"),
    FIRST_CURVE("first-curve"),
    SECOND_CURVE("second-curve"),
    DEGENERATE_IMAGE("degenerate-image"),
}

/** One normalized codomain factor and the images of the pushed points on it. */
internal class ThetaChainSide<Element : QuadraticFieldElement<Element>>(
    internal val curve: MontgomeryCurve<Element>,
    internal val images: List<MontgomeryPoint<Element>>,
)

internal sealed interface ThetaChainOutcome<Element : QuadraticFieldElement<Element>> {
    companion object {
        internal inline fun <Element : QuadraticFieldElement<Element>> of(
            block: () -> ThetaChainCodomain<Element>,
        ): ThetaChainOutcome<Element> =
            try {
                block()
            } catch (rejection: ThetaChainRejection) {
                ThetaChainRejected(rejection.failure)
            }
    }
}

/** The codomain `E3 x E4`; a factor is `null` when the mode does not compute it. */
internal class ThetaChainCodomain<Element : QuadraticFieldElement<Element>>(
    internal val first: ThetaChainSide<Element>?,
    internal val second: ThetaChainSide<Element>?,
) : ThetaChainOutcome<Element>

internal class ThetaChainRejected<Element : QuadraticFieldElement<Element>>(
    internal val failure: ThetaChainFailure,
) : ThetaChainOutcome<Element>

/** Throws a [ThetaChainRejection] for [failure] unless [condition] holds. */
internal fun ensure(condition: Boolean, failure: ThetaChainFailure) {
    if (!condition) throw ThetaChainRejection(failure)
}

/** Internal control flow from a failed check to [ThetaChainOutcome.of]. */
internal class ThetaChainRejection(internal val failure: ThetaChainFailure) :
    Exception(failure.reason)
