// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.ec.MontgomeryCurve
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

/**
 * Algorithms 4.125-4.127 with the reference factor order: after the change of coordinates `(x+z,
 * y+t, y-t, x-z)`, the first factor is `(x : z)` and the second `(x : y)`.
 *
 * Checks run in the reference order. [ThetaChainFailure.DEGENERATE_IMAGE] rejects a pushed point
 * whose factor is `(0 : 0)`, where the reference returns success.
 */
internal fun <Element : QuadraticFieldElement<Element>> splitThetaProduct(
    field: QuadraticField<Element>,
    dualNull: ThetaPoint<Element>,
    points: List<ThetaPoint<Element>>,
    mode: ThetaChainMode,
): ThetaChainCodomain<Element> {
    val nullPoint = productCoordinates(dualNull)
    ensure(
        nullPoint.coordinates.none { it.isEqualTo(field.zero()) },
        ThetaChainFailure.SPLITTING_ZERO,
    )
    ensure(nullPoint.isProduct(), ThetaChainFailure.SPLITTING_PRODUCT)
    val images = points.map(::productCoordinates)
    ensure(images.all { it.isProduct() }, ThetaChainFailure.POINT_PRODUCT)
    if (mode == ThetaChainMode.VERIFY) {
        val x = nullPoint.x.square().square()
        ensure(!x.isEqualTo(nullPoint.y.square().square()), ThetaChainFailure.SPLITTING_INVALID)
    }
    val first =
        if (mode != ThetaChainMode.SECOND) {
            side(
                field,
                nullPoint.x,
                nullPoint.z,
                images.map { it.x to it.z },
                ThetaChainFailure.FIRST_CURVE,
            )
        } else null
    val second =
        if ((mode == ThetaChainMode.BOTH) or (mode == ThetaChainMode.SECOND)) {
            side(
                field,
                nullPoint.x,
                nullPoint.y,
                images.map { it.x to it.y },
                ThetaChainFailure.SECOND_CURVE,
            )
        } else null
    return ThetaChainCodomain(first, second)
}

private fun <Element : QuadraticFieldElement<Element>> productCoordinates(
    point: ThetaPoint<Element>,
): ThetaPoint<Element> =
    ThetaPoint(
        point.x.add(point.z),
        point.y.add(point.t),
        point.y.subtract(point.t),
        point.x.subtract(point.z),
    )

private fun <Element : QuadraticFieldElement<Element>> side(
    field: QuadraticField<Element>,
    a: Element,
    b: Element,
    images: List<Pair<Element, Element>>,
    failure: ThetaChainFailure,
): ThetaChainSide<Element> {
    val model = thetaToMontgomery(field, a, b) ?: throw ThetaChainRejection(failure)
    if (images.any { (x, z) -> x.isEqualTo(field.zero()) and z.isEqualTo(field.zero()) }) {
        throw ThetaChainRejection(ThetaChainFailure.DEGENERATE_IMAGE)
    }
    return ThetaChainSide(
        MontgomeryCurve.fromCoefficients(field, model.coefficient, field.one()),
        images.map { (x, z) ->
            val (mappedX, mappedZ) = model.map(x, z)
            MontgomeryPoint.fromCoordinates(field, mappedX, mappedZ)
        },
    )
}
