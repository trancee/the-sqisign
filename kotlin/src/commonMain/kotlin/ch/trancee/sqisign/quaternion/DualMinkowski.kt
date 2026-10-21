// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.highProduct
import ch.trancee.sqisign.arithmetic.low32
import ch.trancee.sqisign.arithmetic.plusWithBound
import ch.trancee.sqisign.arithmetic.shiftLeft
import ch.trancee.sqisign.arithmetic.shiftSigned
import ch.trancee.sqisign.arithmetic.timesIntWithBound
import ch.trancee.sqisign.arithmetic.unaryMinus

/** Slots of the bitonic sorting network; the candidates beyond the 27 real ones are sentinels. */
private const val SLOTS = 32

/** Headroom of every candidate norm below the sentinel key. */
private const val KEY_HEADROOM = 4

/** Row-major indices `i * 4 + j` of the lower triangle `j <= i`, in the reference order. */
private val LOWER_TRIANGLE =
    (0 until DIMENSION * DIMENSION).filter { it % DIMENSION <= it / DIMENSION }

/** One representative `(t1, t2, t3)` of each `+-` pair of nonzero points of `{-1, 0, 1}^3`. */
private val TAILS =
    listOf(
        intArrayOf(0, 0, 1),
        intArrayOf(0, 1, -1),
        intArrayOf(0, 1, 0),
        intArrayOf(0, 1, 1),
        intArrayOf(1, -1, -1),
        intArrayOf(1, -1, 0),
        intArrayOf(1, -1, 1),
        intArrayOf(1, 0, -1),
        intArrayOf(1, 0, 0),
        intArrayOf(1, 0, 1),
        intArrayOf(1, 1, -1),
        intArrayOf(1, 1, 0),
        intArrayOf(1, 1, 1),
    )

/**
 * The reference `ct_mk_result_t`: the lower-triangular [gram] of the LLL-reduced dual basis, the
 * four selected coordinate [vectors] `T` and the total inverse transform [inverseTotal] `Ainv
 * T^-1`.
 */
internal class MinkowskiReduction(
    val gram: Array<Array<Ibz>>,
    val vectors: List<IntArray>,
    val inverseTotal: List<List<Ibz>>,
)

/**
 * The reference `ct_minkowski_reduce`: sort the 27 short candidates by norm, select the first four
 * independent ones, orient them against the first and compose their inverse with the accumulated
 * inverse transform.
 */
internal fun LllConfig.minkowski(state: DualLdl): MinkowskiReduction {
    val gram = buildGram(state)
    val vectors = selectIndependent(sortedCandidates(state, gram)).toMutableList()
    for (i in 1 until DIMENSION) {
        if (form(gram, vectors[0], vectors[i]).isNegative) {
            vectors[i] = IntArray(DIMENSION) { c -> -vectors[i][c] }
        }
    }
    val v = inverseTranspose(vectors)
    val zero = Ibz.zero(gram[0][0].capacityBits).withBound(inverseBits)
    val total =
        List(DIMENSION) { i ->
            List(DIMENSION) { j ->
                (0 until DIMENSION).fold(zero) { sum, c ->
                    val term = state.inverse[i][c].timesIntWithBound(v[j][c], inverseBits)
                    sum.plusWithBound(term, inverseBits)
                }
            }
        }
    return MinkowskiReduction(gram, vectors, total)
}

/**
 * The reference `ct_mk_eval_inner`: `<t, u>` under the lower-triangular [gram], folding each
 * off-diagonal pair into its stored entry. With `t = u` this is the reference `ct_mk_eval_norm`.
 */
internal fun LllConfig.form(gram: Array<Array<Ibz>>, t: IntArray, u: IntArray): Ibz {
    var total = Ibz.zero(gram[0][0].capacityBits).withBound(normBits)
    for (index in LOWER_TRIANGLE) {
        val i = index / DIMENSION
        val j = index % DIMENSION
        val coefficient = if (i == j) t[i] * u[i] else t[i] * u[j] + t[j] * u[i]
        total = total.plusWithBound(gram[i][j].timesIntWithBound(coefficient, normBits), normBits)
    }
    return total
}

/** The reference `ct_mk_build_gram`: `G[i][j] = sum_k D_k L[i][k] L[j][k]` for `j <= i`. */
private fun LllConfig.buildGram(state: DualLdl): Array<Array<Ibz>> {
    val p = precisionBits
    val n = normBits
    val d = state.diagonal
    val lower = state.lower
    val scaled =
        Array(DIMENSION) { k ->
            Array(DIMENSION) { i ->
                if (k < i) d[k].highProduct(lower[i][k], p, n) else d[i].withBound(n)
            }
        }
    val zero = Ibz.zero(d[0].capacityBits)
    return Array(DIMENSION) { i ->
        Array(DIMENSION) { j ->
            if (j > i) {
                zero
            } else {
                (0 until j).fold(scaled[j][i].withBound(n)) { sum, k ->
                    sum.plusWithBound(scaled[k][i].highProduct(lower[j][k], p, n), n)
                }
            }
        }
    }
}

/**
 * The reference `gen_candidates` and `bitonic_sort32`: `(1, 0, 0, 0)` and, for each tail, the floor
 * and ceiling of its size-reduced first coordinate, padded with zero-vector sentinels and sorted
 * ascending by norm through the 32-slot bitonic network.
 */
private fun LllConfig.sortedCandidates(state: DualLdl, gram: Array<Array<Ibz>>): List<IntArray> {
    val vectors = mutableListOf(intArrayOf(1, 0, 0, 0))
    for (tail in TAILS) {
        val floor = firstCoordinate(state, tail)
        vectors += intArrayOf(floor, tail[0], tail[1], tail[2])
        vectors += intArrayOf(floor + 1, tail[0], tail[1], tail[2])
    }
    val keys = vectors.mapTo(mutableListOf()) { form(gram, it, it) }
    val sentinel =
        Ibz.one(keys[0].capacityBits)
            .withBound(normBits)
            .shiftLeft(normBits - KEY_HEADROOM)
            .withBound(normBits)
    while (vectors.size < SLOTS) {
        vectors += IntArray(DIMENSION)
        keys += sentinel
    }
    bitonicSort(keys, vectors)
    return vectors
}

/** The reference `ct_mk_t0_floor`: `floor(-(t1 L[1][0] + t2 L[2][0] + t3 L[3][0]) / 2^P)`. */
private fun LllConfig.firstCoordinate(state: DualLdl, tail: IntArray): Int {
    val l = lowerBits
    var shift = Ibz.zero(state.lower[0][0].capacityBits).withBound(l)
    for (j in 1 until DIMENSION) {
        shift = shift.plusWithBound(state.lower[j][0].timesIntWithBound(tail[j - 1], l), l)
    }
    return (-shift).withBound(l).shiftSigned(-precisionBits, l, coefficientBits).low32()
}

/** The ascending 32-slot bitonic network, swapping on a strictly smaller partner key. */
private fun bitonicSort(keys: MutableList<Ibz>, vectors: MutableList<IntArray>) {
    var k = 2
    while (k <= SLOTS) {
        var j = k shr 1
        while (j > 0) {
            for (i in 0 until SLOTS) {
                val partner = i xor j
                if (partner > i && (keys[partner] < keys[i]) == (i and k == 0)) {
                    keys[i] = keys[partner].also { keys[partner] = keys[i] }
                    vectors[i] = vectors[partner].also { vectors[partner] = vectors[i] }
                }
            }
            j = j shr 1
        }
        k = k shl 1
    }
}
