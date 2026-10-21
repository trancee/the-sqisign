// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test

class DualOracleTest {
    @Test
    fun dualLdlStartsFromTheClosedFormDecomposition() =
        each("ldl") { fields(it.lllConfig().initDual(quadruple(it))) }

    @Test
    fun blockToursFollowTheReferencePath() =
        each("tour") {
            val config = it.lllConfig()
            val state = config.initDual(quadruple(it))
            config.tours(state)
            fields(state)
        }

    @Test
    fun idealReductionReturnsTheReferenceBasisNormsAndTransform() =
        each("dual") {
            val reduction = it.lllConfig().dualReduceIdeal(it.idealHnf(), it.ibz(HNF_P))
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
            record.ibz(HNF_TWO_N),
            record.ibz(HNF_E),
            record.ibz(HNF_DQ),
            record.ibz(HNF_C),
            record.ibz(HNF_B),
            record.ibz(HNF_P),
        )

    private fun each(operation: String, compute: (IbzRecord) -> String) {
        for (record in IbzRecord.all(operation, DualVectors.records)) {
            record.assertResult { compute(record) }
        }
    }

    private companion object {
        const val DIMENSION = 4
    }
}
