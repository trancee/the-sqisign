// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.divRem

/**
 * `quat_lattice_t`: the lattice spanned by the columns of [basis] divided by [denominator].
 * Variable-time.
 */
internal class QuaternionLattice(
    private val denominator: Ibz,
    private val basis: List<List<Ibz>>,
) {
    /**
     * `quat_lattice_contains`: the coordinates of [element] in the basis by back-substitution, with
     * quotients rounded toward zero, and whether they are all exact. Requires an upper triangular
     * basis of full rank.
     */
    internal fun coordinatesOf(element: QuaternionElement): LatticeCoordinates {
        require(
            (0 until DIMENSION).all { i ->
                !basis[i][i].isZero && (0 until i).all { basis[i][it].isZero }
            },
        ) {
            "Lattice basis must be upper triangular of full rank"
        }
        var remaining = element.coordinates.scaledBy(denominator)
        val scaled = basis.matrixScaledBy(element.denominator)
        var isMember = true
        val reversed = ArrayList<Ibz>(DIMENSION)
        for (i in DIMENSION - 1 downTo 0) {
            val division = remaining[i].divRem(scaled[i][i])
            isMember = isMember && division.remainder.isZero
            reversed += division.quotient
            remaining = remaining.vectorMinus(scaled.map { it[i] }.scaledBy(division.quotient))
        }
        return LatticeCoordinates(reversed.asReversed(), isMember)
    }

    private companion object {
        const val DIMENSION = 4
    }
}

/** The output of `quat_lattice_contains`. */
internal class LatticeCoordinates(
    internal val coordinates: List<Ibz>,
    internal val isMember: Boolean,
)

/** The outputs of `quat_alg_make_primitive`: `x = content * coordinates` in the order basis. */
internal class PrimitiveElement(internal val coordinates: List<Ibz>, internal val content: Ibz)

/** `quat_alg_make_primitive`: the primitive coordinates of this element of [order]. */
internal fun QuaternionElement.primitiveIn(order: QuaternionLattice): PrimitiveElement {
    val membership = order.coordinatesOf(this)
    require(membership.isMember) { "Element must lie in the order" }
    val content = membership.coordinates.content()
    val coordinates =
        if (content.isZero) membership.coordinates else membership.coordinates.dividedBy(content)
    return PrimitiveElement(coordinates, content)
}
