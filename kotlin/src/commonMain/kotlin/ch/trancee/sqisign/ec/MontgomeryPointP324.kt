// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324

/** Immutable x-only coordinates; this type alone does not establish curve membership or order. */
internal class MontgomeryPointP324
private constructor(
    internal val x: Fp2P324,
    internal val z: Fp2P324,
) {
    internal fun isInfinity(): Boolean = z.isEqualTo(Fp2P324.zero())

    internal fun isEqualTo(other: MontgomeryPointP324): Boolean =
        x.multiply(other.z).isEqualTo(z.multiply(other.x))

    /** Infinity has no affine x-coordinate. */
    internal fun affineX(): Fp2P324? = if (isInfinity()) null else x.multiply(z.inverse())

    internal companion object {
        /**
         * Inputs must share a curve/twist and [difference] must represent their actual difference.
         */
        internal fun differentialAdd(
            first: MontgomeryPointP324,
            second: MontgomeryPointP324,
            difference: MontgomeryPointP324,
        ): MontgomeryPointP324 {
            require(!difference.isInfinity() && !difference.x.isEqualTo(Fp2P324.zero())) {
                "Differential addition requires a finite nonzero difference"
            }
            val left = first.x.add(first.z).multiply(second.x.subtract(second.z))
            val right = first.x.subtract(first.z).multiply(second.x.add(second.z))
            return fromCoordinates(
                difference.z.multiply(left.add(right).square()),
                difference.x.multiply(left.subtract(right).square()),
            )
        }

        internal fun infinity(): MontgomeryPointP324 =
            MontgomeryPointP324(Fp2P324.one(), Fp2P324.zero())

        internal fun fromCoordinates(x: Fp2P324, z: Fp2P324): MontgomeryPointP324 {
            require(!x.isEqualTo(Fp2P324.zero()) || !z.isEqualTo(Fp2P324.zero())) {
                "Projective point coordinates must not both be zero"
            }
            return MontgomeryPointP324(x, z)
        }
    }
}
