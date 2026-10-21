// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.bitSize
import ch.trancee.sqisign.arithmetic.highProduct
import ch.trancee.sqisign.arithmetic.plusWithBound
import ch.trancee.sqisign.arithmetic.shiftSigned
import ch.trancee.sqisign.arithmetic.timesIntWithBound

/** The reference `THRESHOLD_PARAM`: bits of progress per machine-word transform. */
private const val THRESHOLD = 29

/** The reference `EXTRACT_W_PARAM`: width of the leading machine-word windows. */
private const val WINDOW = 2 * THRESHOLD + 3

/** Machine-word steps per outer pass. */
private const val MSB_STEPS = THRESHOLD / 2 + 2

/** Windows with at least this many leading zeros have lost too much precision. */
private const val PRECISION_ZEROS = Long.SIZE_BITS - WINDOW + THRESHOLD

/** Row-major entries of the int32 transform `[[m00, m01], [m10, m11]]`. */
private const val M00 = 0
private const val M01 = 1
private const val M10 = 2
private const val M11 = 3

/** The positive-definite binary form `[[a, b], [b, c]]`, truncated by `2^-scale`. */
internal class GramForm(val a: Ibz, val b: Ibz, val c: Ibz, val scale: Int)

/**
 * A Lagrange-Gauss reduced [form] and its unimodular [transform] `[u00, u01, u10, u11]`, in the
 * column convention `b_k' = u00 b_k + u10 b_{k+1}` and `b_{k+1}' = u01 b_k + u11 b_{k+1}`.
 */
internal class GramReduction(val form: GramForm, val transform: List<Ibz>)

/**
 * The reference `quat_lll_materialise_block`: the block form `(D_k, mu D_k, D_{k+1} + mu^2 D_k)` of
 * an LDL decomposition, with [mu] at fixed-point scale `2^precision`, truncated to
 * [LllConfig.gramBits] by a common scale. The precondition `|mu| <= 1/2` keeps both diagonal
 * entries positive.
 */
internal fun LllConfig.materialiseBlock(dk: Ibz, dk1: Ibz, mu: Ibz, precision: Int): GramForm {
    val bound = ldlWorkBits
    val offDiagonal = mu.highProduct(dk, precision, bound)
    val diagonal = dk1.plusWithBound(mu.highProduct(offDiagonal, precision, bound), bound)
    val scale = maxOf(dk.plusWithBound(diagonal, bound + 1).bitSize() - (gramBits - 1), 0)
    val (a, b, c) =
        listOf(dk, offDiagonal, diagonal).map { it.shiftSigned(-scale, bound + 1, gramBits) }
    require(!a.isNegative && !a.isZero && !c.isNegative && !c.isZero) {
        "Block form is not positive"
    }
    return GramForm(a, b, c, scale)
}

/**
 * The reference `quat_lll_gram_lehmer`: [iterations] Lehmer Lagrange-Gauss passes, each reducing
 * leading machine-word windows and applying the int32 transform to the full form. Large size gaps
 * pre-scale the first basis vector by `2^extra`. The form scale is left untouched. Variable-time.
 */
internal fun LllConfig.gramLehmer(form: GramForm, iterations: Int): GramReduction {
    val capacity = form.a.capacityBits
    val one = Ibz.one(capacity).withBound(transformBits)
    val zero = Ibz.zero(capacity).withBound(transformBits)
    var state =
        GramState(
            form.a.withBound(gramWorkBits),
            form.b.withBound(gramWorkBits),
            form.c.withBound(gramWorkBits),
            listOf(one, zero, zero, one),
        )
    repeat(iterations) { state = pass(state) }
    return GramReduction(
        GramForm(state.a, state.b, state.c, form.scale),
        state.u.map { it.withBound(transformBits) },
    )
}

private class GramState(val a: Ibz, val b: Ibz, val c: Ibz, val u: List<Ibz>)

private fun LllConfig.pass(current: GramState): GramState {
    val state =
        if (current.c < current.a) {
            val u = current.u
            GramState(current.c, current.b, current.a, listOf(u[M01], u[M00], u[M11], u[M10]))
        } else {
            current
        }
    val bitsA = state.a.bitSize()
    val bitsC = state.c.bitSize()
    val rho = bitsC - bitsA
    val large = rho >= THRESHOLD
    val fromC = maxOf(bitsC - WINDOW, 0)
    val extra = if (large) (rho - THRESHOLD + 2) / 2 else 0
    val m =
        partialGramMsb(
            window(state.a, fromC - 2 * extra),
            signedWindow(state.b, fromC - extra),
            window(state.c, fromC),
            !large,
        )
    return applyTransform(state, m, extra)
}

/** The reference `apply_matrix_2x2_shift` and `apply_matrix_to_U_shift`. */
private fun LllConfig.applyTransform(state: GramState, m: IntArray, extra: Int): GramState {
    val maxShift = (gramBits - THRESHOLD + 2) / 2
    val w = gramWorkBits
    val (a, b, c) = Triple(state.a, state.b, state.c)
    val nextA = combine(w, a to m[M00] * m[M00], b to 2 * m[M00] * m[M10], c to m[M10] * m[M10])
    val nextB =
        combine(
            w,
            a.shiftSigned(extra, maxShift, w) to m[M00] * m[M01],
            b to m[M00] * m[M11] + m[M01] * m[M10],
            c to m[M10] * m[M11],
        )
    val nextC =
        combine(
            w,
            a.shiftSigned(2 * extra, 2 * maxShift, w) to m[M01] * m[M01],
            b.shiftSigned(extra, maxShift, w) to 2 * m[M01] * m[M11],
            c to m[M11] * m[M11],
        )
    val t = transformBits
    val u = state.u
    val nextU =
        listOf(
            combine(t, u[M00] to m[M00], u[M01] to m[M10]),
            combine(t, u[M00].shiftSigned(extra, maxShift, t) to m[M01], u[M01] to m[M11]),
            combine(t, u[M10] to m[M00], u[M11] to m[M10]),
            combine(t, u[M10].shiftSigned(extra, maxShift, t) to m[M01], u[M11] to m[M11]),
        )
    return GramState(nextA, nextB, nextC, nextU)
}

/** `sum(value * factor)` with every product and partial sum bounded by [bound], as in C. */
private fun combine(bound: Int, vararg terms: Pair<Ibz, Int>): Ibz =
    terms
        .map { (value, factor) -> value.timesIntWithBound(factor, bound) }
        .reduce { total, product -> total.plusWithBound(product, bound) }

/**
 * The reference `ibz_extract_i64`: the low 63 window bits of the magnitude, with the sign of
 * [value].
 */
private fun signedWindow(value: Ibz, offset: Int): Long {
    val magnitude = window(value, offset) and Long.MAX_VALUE
    return if (value.isNegative) -magnitude else magnitude
}

/**
 * The reference `partial_gram_msb`: up to `THRESHOLD / 2 + 2` steps on the unsigned windows [start]
 * and [end] and the signed window [middle] of a binary form, tracked as the int32 column transform
 * `[m00, m01, m10, m11]`. The first step that sees a zero window, would swap when [swapAllowed] is
 * false, is already size-reduced, or lost precision stops the transform. The step shift stays below
 * 32 for positive-definite windows.
 */
internal fun partialGramMsb(start: Long, middle: Long, end: Long, swapAllowed: Boolean): IntArray {
    var a = start
    var b = middle
    var c = end
    val m = intArrayOf(1, 0, 0, 1)
    var valid = true
    repeat(MSB_STEPS) {
        valid = valid and (a != 0L) and (c != 0L)
        val magnitude = if (b < 0) -b else b
        if (valid and (b < 0)) {
            m[M00] = -m[M00]
            m[M10] = -m[M10]
        }
        b = magnitude
        val swap = valid and (c.toULong() < a.toULong())
        valid = valid and (!swap or swapAllowed)
        if (swap and swapAllowed) {
            a = c.also { c = a }
            m[M00] = m[M01].also { m[M01] = m[M00] }
            m[M10] = m[M11].also { m[M11] = m[M10] }
        }
        val zerosA = (a or 1L).countLeadingZeroBits()
        val zerosC = (c or 1L).countLeadingZeroBits()
        valid =
            valid and
                ((magnitude shl 1).toULong() > a.toULong()) and
                (zerosA < PRECISION_ZEROS) and
                (zerosC < PRECISION_ZEROS)
        if (valid) {
            val k = maxOf(log2Ratio(magnitude, a, zerosA), 0)
            c -= (magnitude shl (k + 1)) - (a shl (2 * k))
            b -= a shl k
            m[M01] -= m[M00] shl k
            m[M11] -= m[M10] shl k
        }
    }
    return m
}

/** The reference `fast_log2_ratio`: `k` with `|x / y - 2^k|` minimal, from the normalised words. */
private fun log2Ratio(x: Long, y: Long, zerosY: Int): Int {
    val zerosX = (x or 1L).countLeadingZeroBits()
    val normX = ((x shl zerosX) ushr 2).toULong()
    val threeY = 3uL * ((y shl zerosY) ushr 2).toULong()
    val up = if (normX shl 1 >= threeY) 1 else 0
    val down = if (normX shl 2 < threeY) 1 else 0
    return zerosY - zerosX + up - down
}
