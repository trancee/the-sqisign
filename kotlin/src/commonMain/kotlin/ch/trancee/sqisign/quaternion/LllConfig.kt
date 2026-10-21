// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.ParameterSet

/** Fewest Lagrange-Gauss outer passes in any block-reduction round. */
private const val OUTER_MIN = 4

/** Rounds between halvings of the Lagrange-Gauss outer passes. */
private const val OUTER_HALVE_ROUNDS = 3

/** Largest halving shift of the outer passes. */
private const val OUTER_SHIFT_CAP = 24

/** Outer passes per bit of the block transform bound. */
private const val OUTER_BITS_PER_PASS = 8

/** Headroom of the Gram working bound for the int32 coefficient products. */
private const val GRAM_HEADROOM = 32

/** Headroom of the untruncated LDL block entries above the diagonal bound. */
private const val LDL_HEADROOM = 16

/**
 * The reference `lll_config.h` bit budgets of the dimension-4 reduction at one level, all public
 * functions of the security parameter and the prime size.
 */
internal class LllConfig(parameters: ParameterSet) {
    private val lambda = parameters.securityBits
    private val primeBits =
        parameters.torsionExponent + Int.SIZE_BITS - parameters.cofactor.countLeadingZeroBits()
    private val normBits = primeBits + lambda
    private val gapBits = 2 * normBits - primeBits

    /** `FP_U_BITS`: bound of the unimodular transform entries. */
    val transformBits: Int = limbAlign(gapBits / 4 + lambda / 2)

    /** `FP_P_BITS`: the fixed-point scale of the LDL coefficients. */
    val precisionBits: Int = limbAlign(2 * transformBits + lambda)

    /** `FP_E_BITS`: bound of the LDL diagonal. */
    val diagonalBits: Int = limbAlign(2 * normBits) - (primeBits - lambda - lambda / 2)

    /** `GRAM_TOT_BITS`: bound of a materialised block form. */
    val gramBits: Int = limbAlign(gapBits + lambda)

    /** `GRAM_WORK_BITS`: bound of the block form during the int32 coefficient updates. */
    val gramWorkBits: Int = gramBits + GRAM_HEADROOM

    /** `LG_LDL_WBITS`: bound of the untruncated block entries. */
    val ldlWorkBits: Int = diagonalBits + LDL_HEADROOM

    private val outerPasses = transformBits / OUTER_BITS_PER_PASS

    /**
     * The reference `lg_outer_its_for_round`: halve the outer passes every three rounds from round
     * 1, down to four. A function of the public round index alone.
     */
    fun outerIterations(round: Int): Int {
        val shift = maxOf(round - 1, 0) / OUTER_HALVE_ROUNDS
        return maxOf(outerPasses shr minOf(shift, OUTER_SHIFT_CAP), OUTER_MIN)
    }

    private fun limbAlign(bits: Int): Int =
        (bits + Long.SIZE_BITS - 1) / Long.SIZE_BITS * Long.SIZE_BITS
}
