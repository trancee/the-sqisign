// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

/** Row-major coefficients acting on basis columns, with only one permitted global sign. */
internal class TorsionMatrix
private constructor(
    internal val topLeft: TorsionScalar,
    internal val topRight: TorsionScalar,
    internal val bottomLeft: TorsionScalar,
    internal val bottomRight: TorsionScalar,
) {
    internal val exponent: Int
        get() = topLeft.exponent

    internal fun rowMajor(): List<TorsionScalar> =
        listOf(topLeft, topRight, bottomLeft, bottomRight)

    internal fun determinant(): TorsionScalar =
        topLeft.multiply(bottomRight).subtract(topRight.multiply(bottomLeft))

    internal fun inverse(): TorsionMatrix {
        val determinant = determinant()
        require(determinant.isOdd()) { "Torsion matrix must have odd determinant" }
        val factor = determinant.inverse()
        return of(
            bottomRight.multiply(factor),
            topRight.negate().multiply(factor),
            bottomLeft.negate().multiply(factor),
            topLeft.multiply(factor),
        )
    }

    internal fun normalized(): TorsionMatrix {
        var comparison = 0
        for (entry in rowMajor()) {
            comparison = entry.compareTo(entry.negate())
            if (comparison != 0) break
        }
        return if (comparison > 0) {
            of(topLeft.negate(), topRight.negate(), bottomLeft.negate(), bottomRight.negate())
        } else this
    }

    internal companion object {
        internal fun of(
            topLeft: TorsionScalar,
            topRight: TorsionScalar,
            bottomLeft: TorsionScalar,
            bottomRight: TorsionScalar,
        ): TorsionMatrix {
            require(
                topRight.exponent == topLeft.exponent &&
                    bottomLeft.exponent == topLeft.exponent &&
                    bottomRight.exponent == topLeft.exponent,
            ) {
                "Torsion matrix entries must have the same exponent"
            }
            return TorsionMatrix(topLeft, topRight, bottomLeft, bottomRight)
        }
    }
}
