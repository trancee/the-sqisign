// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.bitSize
import ch.trancee.sqisign.arithmetic.plusWithBound
import ch.trancee.sqisign.arithmetic.shiftLeft

/** The pair swap `(2, 3, 0, 1)` of the reference `QUAT_SIGMA` as an index mask. */
private const val SIGMA_MASK = 2

/**
 * A dual-reduced ideal lattice: the primal [basis] `hnf * transform`, the [gramDiagonal] of the
 * Minkowski-reduced dual in the same column order, scaled back by `2^FP_D_SHIFT`, and the
 * unimodular [transform].
 */
internal class DualReduction(
    val basis: List<List<Ibz>>,
    val gramDiagonal: List<Ibz>,
    val transform: List<List<Ibz>>,
)

/**
 * The reference `quat_lll_dual_reduce_ideal` on the numerator [hnf] of a lattice with denominator
 * 2: an `O0`-ideal of norm `N` in Hermite normal form `[[2N, 0, E, Dq], [0, 2N, C, B], [0, 0, 1,
 * 0], [0, 0, 0, 1]]` in the quaternion algebra ramified at [p]. LLL tours and a Minkowski step
 * reduce the dual lattice; the returned transform is its inverse transpose with the column order
 * reversed. Honest inputs reduce except with probability below `2^-lambda`. Variable-time.
 */
internal fun LllConfig.dualReduceIdeal(hnf: List<List<Ibz>>, p: Ibz): DualReduction {
    val last = DIMENSION - 1
    val twoN = hnf[0][0]
    require(!twoN.isNegative && !twoN.isZero) { "HNF norm entry must be positive" }
    require(hnf[1][1].compareTo(twoN) == 0) { "HNF diagonal entries must be equal" }
    val quadruple = listOf(twoN, hnf[0][2], hnf[0][last], hnf[1][2], hnf[1][last])
    require(quadruple.all { it.bitSize() < hnfBits }) { "HNF entries exceed the ideal bound" }
    val bounded = hnf.map { row -> row.map { it.withBound(hnfBits) } }
    val input =
        IdealQuadruple(
            bounded[0][0],
            bounded[0][2],
            bounded[0][last],
            bounded[1][2],
            bounded[1][last],
            p.withBound(hnfBits),
        )
    val state = initDual(input)
    tours(state)
    val reduction = minkowski(state)
    val transform =
        List(DIMENSION) { i ->
            List(DIMENSION) { c ->
                reduction.inverseTotal[i xor SIGMA_MASK][last - c].withBound(inverseBits)
            }
        }
    val zero = Ibz.zero(twoN.capacityBits).withBound(basisBits)
    val basis =
        List(DIMENSION) { r ->
            List(DIMENSION) { c ->
                (0 until DIMENSION).fold(zero) { sum, m ->
                    sum.plusWithBound(bounded[r][m] * transform[m][c], basisBits)
                }
            }
        }
    val diagonal =
        List(DIMENSION) { i ->
            val vector = reduction.vectors[last - i]
            form(reduction.gram, vector, vector).withBound(normBits).shiftLeft(diagonalShift)
        }
    return DualReduction(basis, diagonal, transform)
}
