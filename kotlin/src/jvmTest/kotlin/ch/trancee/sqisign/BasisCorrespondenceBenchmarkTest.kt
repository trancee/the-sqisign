// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.BasisCorrespondenceBenchmark
import ch.trancee.sqisign.ec.BasisChangeVectors
import ch.trancee.sqisign.ec.BasisCorrespondence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BasisCorrespondenceBenchmarkTest {
    @Test
    fun correspondenceRetainsTheIndependentCompositionAndBothHints() {
        for (parameter in ParameterSet.entries) {
            val records =
                when (parameter) {
                    ParameterSet.NIST_I -> BasisChangeVectors.p324Correspondences
                    ParameterSet.NIST_III -> BasisChangeVectors.p500Correspondences
                    ParameterSet.NIST_V -> BasisChangeVectors.p664Correspondences
                }
            for (shape in listOf("TWO_TORSION", "FULL_TORSION")) {
                val label = if (shape == "TWO_TORSION") "twoTorsion" else "fullOrder"
                val expected = records.map { it.split(':') }.single { it[3] == label }
                val benchmark =
                    BasisCorrespondenceBenchmark().also {
                        it.parameterSet = parameter.name
                        it.torsionShape = shape
                        it.prepare()
                    }

                val actual = assertIs<BasisCorrespondence>(benchmark.setChangeOfBasisMatrix())

                assertEquals(expected[2].toInt(), actual.matrix.exponent)
                assertEquals(
                    expected.drop(MATRIX_OFFSET),
                    actual.matrix.rowMajor().map { it.toLittleEndian().toHexString() },
                )
                assertEquals(
                    expected[FIRST_HINT_OFFSET].toInt(),
                    actual.firstHint.encoded.toInt() and 0xff,
                )
                assertEquals(
                    expected[FIRST_HINT_OFFSET + 1].toInt(),
                    actual.secondHint.encoded.toInt() and 0xff,
                )
            }
        }
    }

    @Test
    fun unknownShapesFailBeforeTiming() {
        val benchmark = BasisCorrespondenceBenchmark().also { it.torsionShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public torsion shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = BasisCorrespondenceBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }
}

private const val FIRST_HINT_OFFSET = 10
private const val MATRIX_OFFSET = 12
