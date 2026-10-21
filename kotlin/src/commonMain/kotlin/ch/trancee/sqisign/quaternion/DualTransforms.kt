// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

/** All column indices of a quaternion lattice basis. */
private val COLUMNS = (0 until DIMENSION).toList()

/**
 * The reference `select_basis`: the first four [candidates] that are linearly independent of those
 * already selected, tested through the maximal minors of the stacked coordinates.
 */
internal fun selectIndependent(candidates: List<IntArray>): List<IntArray> {
    val selected = mutableListOf<IntArray>()
    for (candidate in candidates) {
        if (selected.size < DIMENSION && independent(selected + listOf(candidate))) {
            selected += candidate
        }
    }
    return selected
}

/**
 * The reference `ct_mk_invert_transpose_int`: `(T^-1)^T` of the unimodular coordinate matrix [t],
 * the cofactor matrix times `det T = +-1`.
 */
internal fun inverseTranspose(t: List<IntArray>): List<IntArray> {
    val determinant = minor(t, COLUMNS)
    return List(DIMENSION) { i ->
        IntArray(DIMENSION) { j ->
            val sign = if ((i + j) % 2 == 0) 1L else -1L
            (sign * minor(t.filterIndexed { row, _ -> row != i }, COLUMNS - j) * determinant)
                .toInt()
        }
    }
}

private fun independent(rows: List<IntArray>): Boolean =
    (0 until (1 shl DIMENSION))
        .filter { it.countOneBits() == rows.size }
        .any { mask -> minor(rows, COLUMNS.filter { mask shr it and 1 == 1 }) != 0L }

/** The determinant of [rows] restricted to [columns], by expansion along the first row. */
private fun minor(rows: List<IntArray>, columns: List<Int>): Long =
    if (rows.size == 1) {
        rows[0][columns[0]].toLong()
    } else {
        columns.indices.sumOf { index ->
            val sign = if (index % 2 == 0) 1L else -1L
            sign * rows[0][columns[index]] * minor(rows.drop(1), columns - columns[index])
        }
    }
