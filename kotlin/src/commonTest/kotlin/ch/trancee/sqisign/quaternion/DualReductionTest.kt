// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.shiftLeft
import ch.trancee.sqisign.arithmetic.unaryMinus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DualReductionTest {
    private val record = IbzRecord.all("dual", DualVectors.records).first()
    private val config = LllConfig(ParameterSet.entries.single { it.identifier == record.level })
    private val zero = Ibz.zero(record.capacity)
    private val one = Ibz.one(record.capacity)
    private val twoN = record.ibz(0)

    @Test
    fun reducedDualNormsAreNonIncreasing() {
        val diagonal = config.dualReduceIdeal(hnf(), record.ibz(PRIME)).gramDiagonal

        assertTrue((1 until diagonal.size).all { diagonal[it] <= diagonal[it - 1] })
    }

    @Test
    fun selectedVectorsInvertToTheirTransposedInverse() {
        val t = listOf(intArrayOf(1, 1, 0, 0), intArrayOf(0, 1, 0, 0), intArrayOf(0, 0, -1, 0))
        val unimodular = t + listOf(intArrayOf(1, 0, 0, 1))

        val v = inverseTranspose(unimodular)

        val identity =
            List(DIMENSION) { i ->
                List(DIMENSION) { j ->
                    (0 until DIMENSION).sumOf { c -> unimodular[i][c] * v[j][c] }
                }
            }
        assertEquals(
            List(DIMENSION) { i -> List(DIMENSION) { j -> if (i == j) 1 else 0 } },
            identity,
        )
    }

    @Test
    fun selectionSkipsDependentCandidates() {
        val candidates =
            listOf(
                intArrayOf(1, 0, 0, 0),
                intArrayOf(-1, 0, 0, 0),
                intArrayOf(0, 1, 0, 0),
                intArrayOf(1, 1, 0, 0),
                intArrayOf(0, 0, 1, 0),
                intArrayOf(1, 1, 1, 0),
                intArrayOf(0, 0, 0, 1),
                intArrayOf(0, 1, 0, 1),
            )

        val selected = selectIndependent(candidates)

        assertEquals(listOf(0, 2, 4, 6), selected.map { candidates.indexOf(it) })
    }

    @Test
    fun rejectsANonPositiveNorm() {
        assertFailsWith<IllegalArgumentException> { config.dualReduceIdeal(hnf(n = zero), p()) }
        assertFailsWith<IllegalArgumentException> { config.dualReduceIdeal(hnf(n = -twoN), p()) }
    }

    @Test
    fun rejectsUnequalDiagonalEntries() {
        val shifted =
            hnf().mapIndexed { r, row -> if (r == 1) listOf(zero, one) + row.drop(2) else row }

        assertFailsWith<IllegalArgumentException> { config.dualReduceIdeal(shifted, p()) }
    }

    @Test
    fun rejectsEntriesBeyondTheIdealBound() {
        val large = one.shiftLeft(config.hnfBits)

        assertFailsWith<IllegalArgumentException> { config.dualReduceIdeal(hnf(n = large), p()) }
        assertFailsWith<IllegalArgumentException> { config.dualReduceIdeal(hnf(e = large), p()) }
    }

    private fun p(): Ibz = record.ibz(PRIME)

    private fun hnf(n: Ibz = twoN, e: Ibz = record.ibz(1)): List<List<Ibz>> =
        listOf(
            listOf(n, zero, e, record.ibz(2)),
            listOf(zero, n, record.ibz(3), record.ibz(4)),
            listOf(zero, zero, one, zero),
            listOf(zero, zero, zero, one),
        )

    private companion object {
        const val PRIME = 5
    }
}
