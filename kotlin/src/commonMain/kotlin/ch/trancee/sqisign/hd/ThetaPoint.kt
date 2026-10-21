// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.hd

import ch.trancee.sqisign.field.QuadraticFieldElement

/** Level-two theta coordinates `(x : y : z : t)` on a dimension-two abelian surface. */
internal class ThetaPoint<Element : QuadraticFieldElement<Element>>(
    internal val x: Element,
    internal val y: Element,
    internal val z: Element,
    internal val t: Element,
) {
    internal val coordinates: List<Element>
        get() = listOf(x, y, z, t)

    internal fun hadamard(): ThetaPoint<Element> {
        val xy = x.add(y)
        val zt = z.add(t)
        val xMinusY = x.subtract(y)
        val zMinusT = z.subtract(t)
        return ThetaPoint(
            xy.add(zt),
            xMinusY.add(zMinusT),
            xy.subtract(zt),
            xMinusY.subtract(zMinusT),
        )
    }

    internal fun squares(): ThetaPoint<Element> =
        ThetaPoint(x.square(), y.square(), z.square(), t.square())

    internal fun times(other: ThetaPoint<Element>): ThetaPoint<Element> =
        ThetaPoint(
            x.multiply(other.x),
            y.multiply(other.y),
            z.multiply(other.z),
            t.multiply(other.t),
        )

    /** The squared theta coordinates `H(x^2 : y^2 : z^2 : t^2)`. */
    internal fun toSquared(): ThetaPoint<Element> = squares().hadamard()

    /** Projective coordinate-wise inverse `(1/x : 1/y : 1/z : 1/t)` without a field inversion. */
    internal fun inverse(): ThetaPoint<Element> {
        val xy = x.multiply(y)
        val zt = z.multiply(t)
        return ThetaPoint(y.multiply(zt), x.multiply(zt), t.multiply(xy), z.multiply(xy))
    }

    /** Whether `xt = yz`, the condition for a product of two elliptic theta points. */
    internal fun isProduct(): Boolean = x.multiply(t).isEqualTo(y.multiply(z))
}
