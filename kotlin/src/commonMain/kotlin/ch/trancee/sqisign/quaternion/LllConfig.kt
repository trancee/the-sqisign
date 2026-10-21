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

/** Tours beyond `log2` of the public gap. */
private const val EXTRA_TOURS = 4

/** Headroom of the HNF entries above the ideal norm bound. */
private const val HNF_HEADROOM = 2

/** Headroom of the accumulated inverse transform above the block transform bound. */
private const val INVERSE_HEADROOM = 32

/** Headroom of the reduced basis above the HNF and inverse transform product. */
private const val BASIS_HEADROOM = 2

/** Headroom of the Minkowski norms above the LDL diagonal bound. */
private const val NORM_HEADROOM = 40

/** Headroom of the size-reduction coefficient above the coefficient head. */
private const val COEFFICIENT_HEADROOM = 16

/**
 * The reference `lll_config.h` bit budgets of the dimension-4 reduction at one level, all public
 * functions of the security parameter and the prime size.
 */
internal class LllConfig(val parameters: ParameterSet) {
    private val lambda = parameters.securityBits
    /** `QUAT_P_BITS`: the bit length of the prime. */
    val primeBits: Int =
        parameters.torsionExponent + Int.SIZE_BITS - parameters.cofactor.countLeadingZeroBits()
    private val idealNormBits = primeBits + lambda
    private val gapBits = 2 * idealNormBits - primeBits

    /** `FP_D_SHIFT`: the common right shift of the LDL diagonal. */
    val diagonalShift: Int = primeBits - lambda - lambda / 2

    /** `FP_U_BITS`: bound of the unimodular transform entries. */
    val transformBits: Int = limbAlign(gapBits / 4 + lambda / 2)

    /** `FP_P_BITS`: the fixed-point scale of the LDL coefficients. */
    val precisionBits: Int = limbAlign(2 * transformBits + lambda)

    /** `FP_E_BITS`: bound of the LDL diagonal. */
    val diagonalBits: Int = limbAlign(2 * idealNormBits) - diagonalShift

    /**
     * `FP_L_BITS`: bound of the LDL coefficients, the scale plus the `FP_HEAD` coefficient head.
     */
    val lowerBits: Int = limbAlign(precisionBits + transformBits)

    /** `CT_LLL_TOURS`: the public number of odd-even block tours. */
    val tourCount: Int = Int.SIZE_BITS - 1 - gapBits.countLeadingZeroBits() + EXTRA_TOURS

    /** `QUAT_HNF_BITS`: bound of the input HNF entries. */
    val hnfBits: Int = idealNormBits + HNF_HEADROOM

    /** `QUAT_AINV_BITS`: bound of the accumulated inverse transform. */
    val inverseBits: Int = transformBits + INVERSE_HEADROOM

    /** `QUAT_BASIS_BITS`: bound of the reduced basis entries. */
    val basisBits: Int = limbAlign(hnfBits + inverseBits + BASIS_HEADROOM)

    /** `QUAT_GRAM_BITS`: bound of the Minkowski Gram entries and norms. */
    val normBits: Int = diagonalBits + NORM_HEADROOM

    /** `MK_T0_BITS`: bound of the first size-reduction coefficient. */
    val coefficientBits: Int = lowerBits - precisionBits + COEFFICIENT_HEADROOM

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
