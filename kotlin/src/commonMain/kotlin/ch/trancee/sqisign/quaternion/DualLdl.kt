// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.fixedPointReciprocal
import ch.trancee.sqisign.arithmetic.minusWithBound
import ch.trancee.sqisign.arithmetic.plusWithBound
import ch.trancee.sqisign.arithmetic.roundShiftWords
import ch.trancee.sqisign.arithmetic.scaledProduct
import ch.trancee.sqisign.arithmetic.shiftLeft
import ch.trancee.sqisign.arithmetic.shiftSigned
import ch.trancee.sqisign.arithmetic.timesWithBound
import ch.trancee.sqisign.arithmetic.unaryMinus

/** The reference `FP_NR_ROUNDS`: Newton steps of every fixed-point reciprocal. */
internal const val NEWTON_ROUNDS = 5

/** Dimension of the quaternion lattices. */
internal const val DIMENSION = 4

/** The block indices of one odd-even tour, in the reference order. */
private val TOUR_BLOCKS = intArrayOf(1, 0, 2)

/**
 * The HNF entries `2N`, `E`, `Dq`, `C` and `B` of an ideal of the maximal order `O0` with norm `N`,
 * the reference `qlapoty` quadruple, together with the prime [p] of the quaternion algebra.
 */
internal class IdealQuadruple(
    val twoN: Ibz,
    val e: Ibz,
    val dq: Ibz,
    val c: Ibz,
    val b: Ibz,
    val p: Ibz,
)

/**
 * The reference `fp_ldl_t` of a dual lattice: the positive [diagonal] `D`, the unit
 * lower-triangular [lower] `L` at fixed-point scale `2^P` and the accumulated inverse unimodular
 * transform [inverse]. The tours update all three in place.
 */
internal class DualLdl(
    val diagonal: Array<Ibz>,
    val lower: Array<Array<Ibz>>,
    val inverse: Array<Array<Ibz>>,
)

/**
 * The reference `fp_ldl_init_dual`: the closed-form LDL decomposition of the dual Gram of the
 * ideal, `D = (4N^2, 4N^2, p, p) / 2^FP_D_SHIFT` with the HNF ratios `-x / 2N` below the diagonal,
 * then size-reduced.
 */
internal fun LllConfig.initDual(input: IdealQuadruple): DualLdl {
    val capacity = input.p.capacityBits
    val zero = Ibz.zero(capacity)
    val one = Ibz.one(capacity)
    val unit = one.shiftLeft(precisionBits).withBound(lowerBits)
    val lower = Array(DIMENSION) { i -> Array(DIMENSION) { j -> if (i == j) unit else zero } }
    val square = (input.twoN * input.twoN).shiftSigned(-diagonalShift, diagonalShift, diagonalBits)
    val prime = input.p.shiftSigned(-diagonalShift, diagonalShift, diagonalBits)
    val reciprocal = input.twoN.fixedPointReciprocal(precisionBits, NEWTON_ROUNDS)
    val ratios = listOf(input.e, input.dq, input.c, input.b)
    for ((index, value) in ratios.withIndex()) {
        val ratio =
            value.scaledProduct(
                reciprocal.value,
                -reciprocal.scale,
                input.twoN.bitlen,
                diagonalBits,
            )
        lower[2 + index / 2][index % 2] = (-ratio).withBound(diagonalBits)
    }
    val inverse =
        Array(DIMENSION) { i ->
            Array(DIMENSION) { j -> (if (i == j) one else zero).withBound(transformBits) }
        }
    val state = DualLdl(arrayOf(square, square, prime, prime), lower, inverse)
    sizeReduceFrom(state, 0)
    return state
}

/**
 * The reference `fp_size_reduce_from`: size-reduce the rows from [k] on, subtracting the rounded
 * coefficients from the lower rows and adding them to the inverse transform columns.
 */
internal fun LllConfig.sizeReduceFrom(state: DualLdl, k: Int) {
    val lower = state.lower
    for (r in k until DIMENSION) {
        for (j in r - 1 downTo 0) {
            val q = lower[r][j].roundShiftWords(precisionBits, transformBits)
            for (m in 0 until j) {
                lower[j][m] = lower[j][m].withBound(precisionBits)
                lower[r][m] =
                    lower[r][m].minusWithBound(lower[j][m].timesWithBound(q, lowerBits), lowerBits)
            }
            val step = q.shiftLeft(precisionBits).withBound(lowerBits)
            lower[r][j] = lower[r][j].minusWithBound(step, lowerBits)
            for (row in state.inverse) {
                val product = row[r].timesWithBound(q, 2 * transformBits)
                row[j] = row[j].plusWithBound(product, transformBits)
            }
        }
    }
}

/**
 * The reference `ct_lll_tour4` repeated [LllConfig.tourCount] times: each tour Lehmer-reduces the
 * blocks 1, 0 and 2 in public rounds `3t`, `3t + 1` and `3t + 2`. Variable-time.
 */
internal fun LllConfig.tours(state: DualLdl) {
    for (tour in 0 until tourCount) {
        for ((offset, k) in TOUR_BLOCKS.withIndex()) {
            reduceBlock(state, k, TOUR_BLOCKS.size * tour + offset)
        }
    }
}

/** The reference `ct_lll_reduce_block`: Lagrange-Gauss reduce block [k] and propagate it. */
private fun LllConfig.reduceBlock(state: DualLdl, k: Int, round: Int) {
    val form =
        materialiseBlock(
            state.diagonal[k],
            state.diagonal[k + 1],
            state.lower[k + 1][k],
            precisionBits,
        )
    updateBlock(state, k, gramLehmer(form, outerIterations(round)).transform)
    sizeReduceFrom(state, k)
}
