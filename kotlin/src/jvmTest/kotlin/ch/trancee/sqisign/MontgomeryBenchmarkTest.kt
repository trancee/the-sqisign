// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.MontgomeryBenchmark
import ch.trancee.sqisign.ec.MontgomeryPoint
import ch.trancee.sqisign.ec.curveP324Vectors
import ch.trancee.sqisign.ec.curveP500ReferenceRootVectors
import ch.trancee.sqisign.ec.curveP500Vectors
import ch.trancee.sqisign.ec.curveP664ReferenceRootVectors
import ch.trancee.sqisign.ec.curveP664Vectors
import ch.trancee.sqisign.ec.curveReferenceRootVectors
import ch.trancee.sqisign.ec.sharedDifferenceP324Vectors
import ch.trancee.sqisign.ec.sharedDifferenceP500Vectors
import ch.trancee.sqisign.ec.sharedDifferenceP664Vectors
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MontgomeryBenchmarkTest {
    @Test
    fun doublingWorkloadsMatchIndependentAffinePythonAndPinnedC() {
        assertPointWorkloads(9) { it.doublePoint() }
    }

    @Test
    fun additionWorkloadsMatchIndependentAffinePythonAndPinnedC() {
        assertPointWorkloads(10) { it.differentialAdd() }
    }

    @Test
    fun multiplicationWorkloadsMatchIndependentAffinePythonAndPinnedC() {
        assertPointWorkloads(11) { it.multiply() }
    }

    @Test
    fun threePointWorkloadsMatchIndependentAffinePythonAndPinnedC() {
        assertPointWorkloads(13) { it.threePointMultiply() }
    }

    @Test
    fun biscalarWorkloadsMatchIndependentAffinePythonAndPinnedC() {
        assertPointWorkloads(15) { it.biscalarMultiply() }
    }

    @Test
    fun differenceWorkloadsMatchTheExactPythonAndPinnedCChoice() {
        assertPointWorkloads(16) { it.deterministicDifference() }
    }

    @Test
    fun sharedDifferenceWorkloadsMatchIndependentAffinePythonAndPinnedC() {
        for (parameter in ParameterSet.entries) {
            val vectors =
                when (parameter) {
                    ParameterSet.NIST_I -> sharedDifferenceP324Vectors
                    ParameterSet.NIST_III -> sharedDifferenceP500Vectors
                    ParameterSet.NIST_V -> sharedDifferenceP664Vectors
                }
            val expected = vectors.first().split(':')[10].hexToByteArray()
            for (shape in listOf("SHORT", "FULL_WIDTH")) {
                val benchmark = benchmark(parameter, shape)

                val output = benchmark.sharedDifference()

                assertContentEquals(
                    expected,
                    assertNotNull(assertIs<MontgomeryPoint<*>>(output).affineX()).toLittleEndian(),
                    "${parameter.identifier} $shape shared difference",
                )
            }
        }
    }

    @Test
    fun referenceRootWorkloadsMatchTheExactPythonAndPinnedCSign() {
        for (parameter in ParameterSet.entries) {
            val roots =
                when (parameter) {
                    ParameterSet.NIST_I -> curveReferenceRootVectors
                    ParameterSet.NIST_III -> curveP500ReferenceRootVectors
                    ParameterSet.NIST_V -> curveP664ReferenceRootVectors
                }
            val expected = roots[5].split(':')[1].hexToByteArray()
            for (shape in listOf("SHORT", "FULL_WIDTH")) {
                val benchmark = benchmark(parameter, shape)

                val output = benchmark.referenceSquareRoot()

                assertContentEquals(
                    expected,
                    assertIs<QuadraticFieldElement<*>>(output).toLittleEndian(),
                    "${parameter.identifier} $shape",
                )
            }
        }
    }

    @Test
    fun coefficientWorkloadsMatchIndependentAffinePythonAndPinnedC() {
        for (parameter in ParameterSet.entries) {
            for ((shape, index) in listOf("SHORT" to 12, "FULL_WIDTH" to 13)) {
                val fields = vectors(parameter)[index].split(':')
                val benchmark = benchmark(parameter, shape)

                val output = benchmark.coefficient()

                assertContentEquals(
                    fields[12].hexToByteArray(),
                    assertIs<QuadraticFieldElement<*>>(output).toLittleEndian(),
                    "${parameter.identifier} $shape coefficient",
                )
            }
        }
    }

    @Test
    fun unknownScalarShapesAreRejected() {
        val benchmark = MontgomeryBenchmark()
        benchmark.scalarShape = "UNKNOWN"

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public curve scalar shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownParameterSetsAreRejected() {
        val benchmark = MontgomeryBenchmark()
        benchmark.parameterSet = "UNKNOWN"

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }

    private fun assertPointWorkloads(
        expectedOffset: Int,
        operation: (MontgomeryBenchmark) -> Any,
    ) {
        for (parameter in ParameterSet.entries) {
            for ((shape, index) in listOf("SHORT" to 12, "FULL_WIDTH" to 13)) {
                val fields = vectors(parameter)[index].split(':')
                val benchmark = benchmark(parameter, shape)

                val output = operation(benchmark)

                val coordinate = assertNotNull(assertIs<MontgomeryPoint<*>>(output).affineX())
                assertContentEquals(
                    fields[expectedOffset].hexToByteArray(),
                    coordinate.toLittleEndian(),
                    "${parameter.identifier} $shape field $expectedOffset",
                )
            }
        }
    }

    private fun benchmark(parameter: ParameterSet, shape: String): MontgomeryBenchmark =
        MontgomeryBenchmark().also {
            it.parameterSet = parameter.name
            it.scalarShape = shape
            it.prepare()
        }

    private fun vectors(parameter: ParameterSet): List<String> =
        when (parameter) {
            ParameterSet.NIST_I -> curveP324Vectors
            ParameterSet.NIST_III -> curveP500Vectors
            ParameterSet.NIST_V -> curveP664Vectors
        }
}
