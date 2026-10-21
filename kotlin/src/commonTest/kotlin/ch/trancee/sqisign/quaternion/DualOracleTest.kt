// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test

class DualOracleTest {
    @Test
    fun dualLdlStartsFromTheClosedFormDecomposition() =
        each("ldl") { fields(config(it).initDual(quadruple(it))) }

    @Test
    fun blockToursFollowTheReferencePath() =
        each("tour") {
            val config = config(it)
            val state = config.initDual(quadruple(it))
            config.tours(state)
            fields(state)
        }

    @Test
    fun idealReductionReturnsTheReferenceBasisNormsAndTransform() =
        each("dual") {
            val reduction = config(it).dualReduceIdeal(hnf(it), it.ibz(P))
            (reduction.basis.flatten() + reduction.gramDiagonal + reduction.transform.flatten())
                .joinToString(" ") { value -> encode(value) }
        }

    private fun fields(state: DualLdl): String {
        val lower = (0 until DIMENSION).flatMap { i -> (0 until i).map { j -> state.lower[i][j] } }
        return (state.diagonal.toList() + lower + state.inverse.flatMap { it.toList() })
            .joinToString(" ") { value -> encode(value) }
    }

    private fun quadruple(record: IbzRecord): IdealQuadruple =
        IdealQuadruple(
            record.ibz(TWO_N),
            record.ibz(E),
            record.ibz(DQ),
            record.ibz(C),
            record.ibz(B),
            record.ibz(P),
        )

    private fun hnf(record: IbzRecord): List<List<Ibz>> {
        val zero = Ibz.zero(record.capacity)
        val one = Ibz.one(record.capacity)
        val twoN = record.ibz(TWO_N)
        return listOf(
            listOf(twoN, zero, record.ibz(E), record.ibz(DQ)),
            listOf(zero, twoN, record.ibz(C), record.ibz(B)),
            listOf(zero, zero, one, zero),
            listOf(zero, zero, zero, one),
        )
    }

    private fun config(record: IbzRecord): LllConfig =
        LllConfig(ParameterSet.entries.single { it.identifier == record.level })

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, DualVectors.records)) {
            record.assertResult { compute(record) }
        }
    }

    private companion object {
        const val DIMENSION = 4
        const val TWO_N = 0
        const val E = 1
        const val DQ = 2
        const val C = 3
        const val B = 4
        const val P = 5
    }
}
