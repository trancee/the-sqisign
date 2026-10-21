// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.fixedPointReciprocal
import ch.trancee.sqisign.arithmetic.highProduct
import ch.trancee.sqisign.arithmetic.minusWithBound
import ch.trancee.sqisign.arithmetic.plusWithBound
import ch.trancee.sqisign.arithmetic.scaledProduct
import ch.trancee.sqisign.arithmetic.shiftLeft
import ch.trancee.sqisign.arithmetic.timesWithBound
import ch.trancee.sqisign.arithmetic.unaryMinus

/** Bound of the block transform determinant, `+-1` for every unimodular transform. */
private const val DETERMINANT_BITS = 32

/** Entries of the block transform `[u00, u01, u10, u11]`. */
private const val U00 = 0
private const val U01 = 1
private const val U10 = 2
private const val U11 = 3

/**
 * The block transform [u] with its old and new LDL coefficients and whether its determinant is
 * `-1`, which negates the updated columns.
 */
private class BlockStep(val u: List<Ibz>, val oldMu: Ibz, val newMu: Ibz, val negate: Boolean)

/**
 * The reference `ct_lll_update_block`: apply the unimodular [transform] of block [k] to the LDL
 * diagonal and coefficients and to the inverse transform columns, without recomputing the LDL.
 */
internal fun LllConfig.updateBlock(state: DualLdl, k: Int, transform: List<Ibz>) {
    val oldMu = state.lower[k + 1][k].withBound(lowerBits)
    val u = transform.map { it.withBound(transformBits) }
    val determinant =
        u[U00]
            .timesWithBound(u[U11], 2 * transformBits)
            .minusWithBound(u[U01].timesWithBound(u[U10], 2 * transformBits), DETERMINANT_BITS)
    val step = BlockStep(u, oldMu, blockGram(state, k, u, oldMu), determinant.isNegative)
    leftTail(state, k, u)
    rightTail(state, k, step)
    inverseColumns(state, k, step)
}

/** The block's local Gram in orthogonalised space, giving the new `D_k`, `D_{k+1}` and `mu`. */
private fun LllConfig.blockGram(state: DualLdl, k: Int, u: List<Ibz>, oldMu: Ibz): Ibz {
    val p = precisionBits
    val e = diagonalBits
    val d = state.diagonal
    val eta0 = u[U00].shiftLeft(p).plusWithBound(oldMu * u[U10], p + 2)
    val eta1 = u[U01].shiftLeft(p).withBound(lowerBits).plusWithBound(oldMu * u[U11], lowerBits)
    val tail = { left: Ibz, right: Ibz ->
        d[k + 1].timesWithBound(left, e).timesWithBound(right, e)
    }
    val g00 =
        eta0
            .highProduct(eta0, p, p + 2)
            .highProduct(d[k], p, e)
            .plusWithBound(tail(u[U10], u[U10]), e)
    val g01 =
        eta0.highProduct(eta1, p, e).highProduct(d[k], p, e).plusWithBound(tail(u[U10], u[U11]), e)
    val reciprocal = g00.fixedPointReciprocal(p, NEWTON_ROUNDS)
    val newMu = g01.scaledProduct(reciprocal.value, -reciprocal.scale, lowerBits, lowerBits)
    val ratio = d[k + 1].scaledProduct(reciprocal.value, -reciprocal.scale, e, e)
    d[k + 1] = ratio.highProduct(d[k], p, e)
    d[k] = g00.withBound(e)
    state.lower[k + 1][k] = newMu.withBound(lowerBits)
    return newMu
}

/** The columns `j < k` of rows `k` and `k + 1`. */
private fun LllConfig.leftTail(state: DualLdl, k: Int, u: List<Ibz>) {
    val l = lowerBits
    val lower = state.lower
    for (j in 0 until k) {
        val oldK = lower[k][j]
        val oldK1 = lower[k + 1][j]
        lower[k][j] =
            oldK.timesWithBound(u[U00], l).plusWithBound(oldK1.timesWithBound(u[U10], l), l)
        lower[k + 1][j] =
            oldK.timesWithBound(u[U01], l).plusWithBound(oldK1.timesWithBound(u[U11], l), l)
    }
}

/** The rows `i > k + 1` of columns `k` and `k + 1`. */
private fun LllConfig.rightTail(state: DualLdl, k: Int, step: BlockStep) {
    val l = lowerBits
    val u = step.u
    val lower = state.lower
    for (i in k + 2 until DIMENSION) {
        val oldK = lower[i][k]
        val oldK1 = lower[i][k + 1]
        val projected = step.oldMu.highProduct(oldK1, precisionBits, l)
        val eta0 =
            oldK1.timesWithBound(u[U00], l).plusWithBound((projected * u[U10]).withBound(l), l)
        val eta1 =
            oldK1.timesWithBound(u[U01], l).plusWithBound(projected.timesWithBound(u[U11], l), l)
        val nextK1 = signed(eta0.minusWithBound(oldK.timesWithBound(u[U10], l), l), step.negate)
        val term = signed(oldK.timesWithBound(u[U11], l).minusWithBound(eta1, l), step.negate)
        lower[i][k] = term.plusWithBound(step.newMu.highProduct(nextK1, precisionBits, l), l)
        lower[i][k + 1] = nextK1.withBound(l)
    }
}

/** The inverse transform columns `k` and `k + 1`, multiplied by the adjugate of the block. */
private fun LllConfig.inverseColumns(state: DualLdl, k: Int, step: BlockStep) {
    val t = transformBits
    val u = step.u
    for (row in state.inverse) {
        val x = row[k]
        val y = row[k + 1]
        val first = (u[U11] * x).withBound(2 * t).minusWithBound(u[U01] * y, t)
        val second = (u[U00] * y).minusWithBound(u[U10] * x, t)
        row[k] = signed(first, step.negate)
        row[k + 1] = signed(second, step.negate)
    }
}

private fun signed(value: Ibz, negate: Boolean): Ibz = if (negate) -value else value
