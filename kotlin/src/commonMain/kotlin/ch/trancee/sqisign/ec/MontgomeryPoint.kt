// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

/** Immutable x-only coordinates; this type alone does not establish curve membership or order. */
internal class MontgomeryPoint<Element : QuadraticFieldElement<Element>>
private constructor(
    internal val field: QuadraticField<Element>,
    internal val x: Element,
    internal val z: Element,
) {
    internal fun isInfinity(): Boolean = z.isEqualTo(field.zero())

    internal fun isEqualTo(other: MontgomeryPoint<Element>): Boolean =
        x.multiply(other.z).isEqualTo(z.multiply(other.x))

    /** Infinity has no affine x-coordinate. */
    internal fun affineX(): Element? = if (isInfinity()) null else x.multiply(z.inverse())

    internal companion object {
        /**
         * Inputs must share a curve/twist and [difference] must represent their actual difference.
         */
        internal fun <Element : QuadraticFieldElement<Element>> differentialAdd(
            first: MontgomeryPoint<Element>,
            second: MontgomeryPoint<Element>,
            difference: MontgomeryPoint<Element>,
        ): MontgomeryPoint<Element> {
            require(!difference.isInfinity() && !difference.x.isEqualTo(difference.field.zero())) {
                "Differential addition requires a finite nonzero difference"
            }
            val left = first.x.add(first.z).multiply(second.x.subtract(second.z))
            val right = first.x.subtract(first.z).multiply(second.x.add(second.z))
            return fromCoordinates(
                first.field,
                difference.z.multiply(left.add(right).square()),
                difference.x.multiply(left.subtract(right).square()),
            )
        }

        internal fun <Element : QuadraticFieldElement<Element>> infinity(
            field: QuadraticField<Element>,
        ): MontgomeryPoint<Element> = MontgomeryPoint(field, field.one(), field.zero())

        internal fun <Element : QuadraticFieldElement<Element>> fromCoordinates(
            field: QuadraticField<Element>,
            x: Element,
            z: Element,
        ): MontgomeryPoint<Element> {
            require(!x.isEqualTo(field.zero()) || !z.isEqualTo(field.zero())) {
                "Projective point coordinates must not both be zero"
            }
            return MontgomeryPoint(field, x, z)
        }
    }
}
