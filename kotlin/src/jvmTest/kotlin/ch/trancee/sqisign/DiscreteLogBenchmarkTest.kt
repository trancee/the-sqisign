// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.DiscreteLogBenchmark
import ch.trancee.sqisign.ec.TorsionScalar
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiscreteLogBenchmarkTest {
    @Test
    fun preparedSolvingRetainsEveryExpectedScalarBit() {
        forEachWorkload { benchmark, exponent, expected ->
            val logarithm = assertIs<TorsionScalar>(benchmark.solve())

            assertEquals(exponent, logarithm.exponent)
            assertContentEquals(expected, logarithm.toLittleEndian())
        }
    }

    @Test
    fun freshPreparationAndSolvingRetainEveryExpectedScalarBit() {
        forEachWorkload { benchmark, exponent, expected ->
            val logarithm = assertIs<TorsionScalar>(benchmark.prepareAndSolve())

            assertEquals(exponent, logarithm.exponent)
            assertContentEquals(expected, logarithm.toLittleEndian())
        }
    }

    @Test
    fun unknownTorsionShapesFailBeforeTiming() {
        val benchmark = DiscreteLogBenchmark().also { it.torsionShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public torsion shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownLogarithmShapesFailBeforeTiming() {
        val benchmark = DiscreteLogBenchmark().also { it.valueShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public logarithm shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = DiscreteLogBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }

    private fun forEachWorkload(assertion: (DiscreteLogBenchmark, Int, ByteArray) -> Unit) {
        for (parameter in ParameterSet.entries) {
            for (torsion in listOf("TWO_TORSION", "FULL_TORSION")) {
                for (value in listOf("ZERO", "MAXIMUM")) {
                    val exponent = if (torsion == "TWO_TORSION") 1 else parameter.torsionExponent
                    val expected = ByteArray((exponent + 7) / Byte.SIZE_BITS)
                    if (value == "MAXIMUM") {
                        expected.fill(0xff.toByte())
                        expected[expected.lastIndex] =
                            ((1 shl ((exponent - 1) % Byte.SIZE_BITS + 1)) - 1).toByte()
                    }
                    val benchmark =
                        DiscreteLogBenchmark().also {
                            it.parameterSet = parameter.name
                            it.torsionShape = torsion
                            it.valueShape = value
                            it.prepare()
                        }
                    assertion(benchmark, exponent, expected)
                }
            }
        }
    }
}
