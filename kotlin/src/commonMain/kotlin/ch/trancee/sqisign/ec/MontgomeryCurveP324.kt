// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.arithmetic.UInt328
import ch.trancee.sqisign.field.Fp2P324

/** A nonsingular projective Montgomery curve; arithmetic is variable-time. */
internal class MontgomeryCurveP324
private constructor(
    private val numerator: Fp2P324,
    private val denominator: Fp2P324,
) {
    private val twiceDenominator: Fp2P324 = denominator.add(denominator)
    private val doublingNumerator: Fp2P324 = numerator.add(twiceDenominator)
    private val doublingDenominator: Fp2P324 = twiceDenominator.add(twiceDenominator)

    internal fun montgomeryCoefficient(): Fp2P324 = numerator.multiply(denominator.inverse())

    internal fun double(point: MontgomeryPointP324): MontgomeryPointP324 {
        val sumSquare = point.x.add(point.z).square()
        val differenceSquare = point.x.subtract(point.z).square()
        val delta = sumSquare.subtract(differenceSquare)
        val scaledDifference = differenceSquare.multiply(doublingDenominator)
        return MontgomeryPointP324.fromCoordinates(
            sumSquare.multiply(scaledDifference),
            delta.multiply(scaledDifference.add(delta.multiply(doublingNumerator))),
        )
    }

    /** Montgomery ladder for an unsigned 328-bit scalar, including zero; not constant-time. */
    internal fun multiply(point: MontgomeryPointP324, scalar: UInt328): MontgomeryPointP324 {
        val encoded = scalar.toLittleEndian()
        // xADD excludes infinity and (0:1); parity suffices for both of these points.
        if (point.isInfinity() || point.x.isEqualTo(Fp2P324.zero())) {
            return if (encoded[0].toInt() and 1 == 0) MontgomeryPointP324.infinity() else point
        }
        var lower = MontgomeryPointP324.infinity()
        var upper = point
        var started = false
        for (byteIndex in encoded.lastIndex downTo 0) {
            for (bitIndex in Byte.SIZE_BITS - 1 downTo 0) {
                val bit = encoded[byteIndex].toInt() ushr bitIndex and 1
                if (!started && bit == 0) continue
                started = true
                val sum = MontgomeryPointP324.differentialAdd(lower, upper, point)
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

    internal companion object {
        internal fun fromCoefficients(
            numerator: Fp2P324,
            denominator: Fp2P324,
        ): MontgomeryCurveP324 {
            require(!denominator.isEqualTo(Fp2P324.zero())) {
                "Montgomery curve denominator must be nonzero"
            }
            val twiceDenominator = denominator.add(denominator)
            require(!numerator.square().isEqualTo(twiceDenominator.square())) {
                "Montgomery curve must be nonsingular"
            }
            return MontgomeryCurveP324(numerator, denominator)
        }
    }
}
