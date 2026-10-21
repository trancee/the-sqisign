// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.compareTo
import ch.trancee.sqisign.arithmetic.inverseMod
import ch.trancee.sqisign.arithmetic.minus
import ch.trancee.sqisign.arithmetic.mod
import ch.trancee.sqisign.arithmetic.plus
import ch.trancee.sqisign.arithmetic.shiftLeft
import ch.trancee.sqisign.arithmetic.unaryMinus

/*
 * The 2x2 integer matrices of the reference `dim2.c` that ideals, `qlapoty`, `id2iso` and signing
 * call, as row-major `List<List<Ibz>>`. Every operation keeps the reference operation order, so
 * result bit lengths match `ibz_t`. Variable-time.
 */

/** The outputs of `ibz_mat_2x2_inv_with_det_as_denom`; [adjugate] is null when singular. */
internal class Adjugate2x2(internal val determinant: Ibz, internal val adjugate: List<List<Ibz>>?)

/** The outputs of `ibz_mat_2x2_inv_mod`; [inverse] is zero when [exists] is false. */
internal class ModularInverse2x2(
    internal val inverse: List<List<Ibz>>,
    internal val exists: Boolean,
)

/** `ibz_mat_2x2_eval`. */
internal fun List<List<Ibz>>.evaluate2x2(vector: List<Ibz>): List<Ibz> = map { row ->
    row[0] * vector[0] + row[1] * vector[1]
}

/** `ibz_mat_2x2_mul`: sums start from the one-bit zero. */
internal fun List<List<Ibz>>.times2x2(other: List<List<Ibz>>): List<List<Ibz>> {
    val zero = Ibz.zero(this[0][0].capacityBits)
    return map { row ->
        List(row.size) { j -> row.indices.fold(zero) { sum, k -> sum + row[k] * other[k][j] } }
    }
}

/** `ibz_mat_2x2_inv_with_det_as_denom`: the determinant and, if it is nonzero, the adjugate. */
internal fun List<List<Ibz>>.adjugate2x2(): Adjugate2x2 {
    val determinant = this[0][0] * this[1][1] - this[0][1] * this[1][0]
    return Adjugate2x2(determinant, if (determinant.isZero) null else adjugateMatrix())
}

/** `ibz_mat_2x2_inv_mod`: the inverse modulo a positive [modulus], or zero if none exists. */
internal fun List<List<Ibz>>.inverse2x2Mod(modulus: Ibz): ModularInverse2x2 {
    val first = (this[0][0] * this[1][1]).mod(modulus)
    val inverse = (first - this[0][1] * this[1][0]).mod(modulus).inverseMod(modulus)
    val scale = inverse.value * Ibz.of(modulus.capacityBits, if (inverse.exists) 1 else 0, 2)
    val result = adjugateMatrix().map { row -> row.map { (it * scale).mod(modulus) } }
    return ModularInverse2x2(result, inverse.exists)
}

/**
 * `ibz_mat_2x2_normalize`: for entries in `[0, 2^exponent)`, negates the matrix modulo `2^exponent`
 * when its first entry other than zero and `2^(exponent - 1)` exceeds the latter.
 */
internal fun List<List<Ibz>>.normalized2x2(exponent: Int): List<List<Ibz>> {
    val half = Ibz.one(this[0][0].capacityBits).shiftLeft(exponent - 1)
    val deciding = flatten().firstOrNull { !it.isZero && it.compareTo(half) != 0 }
    if (deciding == null || deciding < half) return this
    val full = half.shiftLeft(1)
    return map { row -> row.map { full - it } }
}

private fun List<List<Ibz>>.adjugateMatrix(): List<List<Ibz>> =
    listOf(listOf(this[1][1], -this[0][1]), listOf(-this[1][0], this[0][0]))
