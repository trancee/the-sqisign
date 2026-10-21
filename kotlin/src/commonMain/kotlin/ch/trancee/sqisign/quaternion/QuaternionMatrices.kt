// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.gcd
import ch.trancee.sqisign.arithmetic.plus

/*
 * The 4x4 integer matrices of the reference `dim4.c` that the lattice, ideal and signing layers
 * call, as row-major `List<List<Ibz>>`. Every operation keeps the reference operation order, so
 * result bit lengths match `ibz_t`; sums start from the one-bit zero `ibz_set(0, 0)`.
 * Variable-time.
 */

/** `ibz_mat_4x4_eval`: the matrix times the column vector. */
internal fun List<List<Ibz>>.evaluate(vector: List<Ibz>): List<Ibz> = map { row ->
    row.indices.fold(zeroOf(vector)) { sum, j -> sum + row[j] * vector[j] }
}

/** `ibz_mat_4x4_eval_t`: the row vector times the matrix. */
internal fun List<List<Ibz>>.evaluateTransposed(vector: List<Ibz>): List<Ibz> = indices.map { i ->
    indices.fold(zeroOf(vector)) { sum, j -> sum + this[j][i] * vector[j] }
}

/** `quat_qf_eval`: `vector^T * matrix * vector`. */
internal fun List<List<Ibz>>.quadraticForm(vector: List<Ibz>): Ibz =
    evaluate(vector).zip(vector) { image, x -> image * x }.reduce { sum, term -> sum + term }

/** `ibz_mat_4x4_gcd`: the non-negative gcd of all entries, zero for the zero matrix. */
internal fun List<List<Ibz>>.entryGcd(): Ibz = flatten().fold(this[0][0]) { g, x -> g.gcd(x) }

/** `ibz_mat_4x4_scalar_div`: quotients rounded toward zero; callers divide exactly. */
internal fun List<List<Ibz>>.matrixDividedBy(scalar: Ibz): List<List<Ibz>> = map {
    it.dividedBy(scalar)
}

/** `ibz_mat_4x4_scalar_mul`. */
internal fun List<List<Ibz>>.matrixScaledBy(scalar: Ibz): List<List<Ibz>> = map {
    it.scaledBy(scalar)
}

/** `ibz_mat_4x4_identity`: one as `ibz_set(1, 2)`, zero as `ibz_set(0, 0)`. */
internal fun identityMatrix(capacityBits: Int): List<List<Ibz>> =
    List(DIMENSION) { i ->
        List(DIMENSION) { j -> if (i == j) Ibz.one(capacityBits) else Ibz.zero(capacityBits) }
    }

/** `ibz_mat_4x4_zero`. */
internal fun zeroMatrix(capacityBits: Int): List<List<Ibz>> =
    List(DIMENSION) { List(DIMENSION) { Ibz.zero(capacityBits) } }

private fun zeroOf(vector: List<Ibz>): Ibz = Ibz.zero(vector[0].capacityBits)
