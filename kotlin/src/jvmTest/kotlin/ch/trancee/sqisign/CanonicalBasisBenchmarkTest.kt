// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.CanonicalBasisBenchmark
import ch.trancee.sqisign.ec.BasisHint
import ch.trancee.sqisign.ec.TorsionBasis
import ch.trancee.sqisign.ec.canonicalBasisP324Vectors
import ch.trancee.sqisign.ec.canonicalBasisP500Vectors
import ch.trancee.sqisign.ec.canonicalBasisP664Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CanonicalBasisBenchmarkTest {
    @Test
    fun generationRetainsEveryIndependentBasisAndHint() {
        forEachWorkload(false) { benchmark, expected ->
            val actual = assertIs<Pair<*, *>>(benchmark.canonical())

            assertBasis(assertIs<TorsionBasis<*>>(actual.first), expected)
            assertEquals(expected[3].toInt().toByte(), assertIs<BasisHint>(actual.second).encoded)
        }
    }

    @Test
    fun reconstructionRetainsEveryIndependentBasis() {
        forEachWorkload(false) { benchmark, expected ->
            val actual = assertIs<TorsionBasis<*>>(benchmark.fromHint())

            assertBasis(actual, expected)
        }
    }

    @Test
    fun orientedGenerationRetainsEveryIndependentBasisAndHint() {
        forEachWorkload(true) { benchmark, expected ->
            val actual = assertIs<Pair<*, *>>(benchmark.canonicalForReconstruction())

            assertBasis(assertIs<TorsionBasis<*>>(actual.first), expected)
            assertEquals(expected[3].toInt().toByte(), assertIs<BasisHint>(actual.second).encoded)
        }
    }

    @Test
    fun unknownCoefficientShapesFailBeforeTiming() {
        val benchmark = CanonicalBasisBenchmark().also { it.coefficientShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public coefficient shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownTorsionShapesFailBeforeTiming() {
        val benchmark = CanonicalBasisBenchmark().also { it.torsionShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public torsion shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = CanonicalBasisBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }

    private fun forEachWorkload(
        oriented: Boolean,
        assertion: (CanonicalBasisBenchmark, List<String>) -> Unit,
    ) {
        for (parameter in ParameterSet.entries) {
            val vectors =
                when (parameter) {
                    ParameterSet.NIST_I -> canonicalBasisP324Vectors
                    ParameterSet.NIST_III -> canonicalBasisP500Vectors
                    ParameterSet.NIST_V -> canonicalBasisP664Vectors
                }
            for ((coefficient, offset) in listOf("SQUARE" to 0, "NONSQUARE" to 4)) {
                for ((torsion, index) in
                    listOf("TWO_TORSION" to 0, "FULL_TORSION" to if (oriented) 3 else 2)) {
                    val benchmark =
                        CanonicalBasisBenchmark().also {
                            it.parameterSet = parameter.name
                            it.coefficientShape = coefficient
                            it.torsionShape = torsion
                            it.prepare()
                        }
                    assertion(benchmark, vectors[offset + index].split(':'))
                }
            }
        }
    }

    private fun assertBasis(actual: TorsionBasis<*>, expected: List<String>) {
        assertEquals(expected[1].toInt(), actual.exponent)
        for ((point, encoded) in
            listOf(actual.first, actual.second, actual.difference).zip(expected.drop(4))) {
            assertContentEquals(
                encoded.hexToByteArray(),
                assertNotNull(point.affineX()).toLittleEndian(),
            )
        }
    }
}
