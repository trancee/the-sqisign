// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.MontgomeryP324Benchmark
import ch.trancee.sqisign.ec.MontgomeryPointP324
import ch.trancee.sqisign.ec.curveP324Vectors
import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MontgomeryP324BenchmarkTest {
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
    fun coefficientWorkloadsMatchIndependentAffinePythonAndPinnedC() {
        for ((shape, index) in listOf("SHORT" to 12, "FULL_WIDTH" to 13)) {
            val fields = curveP324Vectors[index].split(':')
            val benchmark = MontgomeryP324Benchmark()
            benchmark.scalarShape = shape
            benchmark.prepare()

            val output = benchmark.coefficient()

            assertContentEquals(
                fields[12].hexToByteArray(),
                assertIs<Fp2P324>(output).toLittleEndian(),
                "$shape coefficient",
            )
        }
    }

    @Test
    fun unknownScalarShapesAreRejected() {
        val benchmark = MontgomeryP324Benchmark()
        benchmark.scalarShape = "UNKNOWN"

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public curve scalar shape: UNKNOWN", failure.message)
    }

    private fun assertPointWorkloads(
        expectedOffset: Int,
        operation: (MontgomeryP324Benchmark) -> Any,
    ) {
        for ((shape, index) in listOf("SHORT" to 12, "FULL_WIDTH" to 13)) {
            val fields = curveP324Vectors[index].split(':')
            val benchmark = MontgomeryP324Benchmark()
            benchmark.scalarShape = shape
            benchmark.prepare()

            val output = operation(benchmark)

            val coordinate = assertNotNull(assertIs<MontgomeryPointP324>(output).affineX())
            assertContentEquals(
                fields[expectedOffset].hexToByteArray(),
                coordinate.toLittleEndian(),
                "$shape field $expectedOffset",
            )
        }
    }
}
