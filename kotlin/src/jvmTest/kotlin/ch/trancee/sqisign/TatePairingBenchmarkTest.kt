// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.TatePairingBenchmark
import ch.trancee.sqisign.ec.PairingVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TatePairingBenchmarkTest {
    @Test
    fun preparedEvaluationRetainsTheIndependentMillerValue() {
        forEachWorkload { benchmark, expected ->
            val actual = assertIs<ByteArray>(benchmark.evaluate())

            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun freshPreparationAndEvaluationRetainTheIndependentMillerValue() {
        forEachWorkload { benchmark, expected ->
            val actual = assertIs<ByteArray>(benchmark.prepareAndEvaluate())

            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun unknownShapesFailBeforeTiming() {
        val benchmark = TatePairingBenchmark().also { it.torsionShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public torsion shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = TatePairingBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }

    private fun forEachWorkload(assertion: (TatePairingBenchmark, ByteArray) -> Unit) {
        for (parameter in ParameterSet.entries) {
            val records =
                when (parameter) {
                    ParameterSet.NIST_I -> PairingVectors.p324TatePairings
                    ParameterSet.NIST_III -> PairingVectors.p500TatePairings
                    ParameterSet.NIST_V -> PairingVectors.p664TatePairings
                }
            for (shape in listOf("TWO_TORSION", "FULL_TORSION")) {
                val exponent = if (shape == "TWO_TORSION") 1 else parameter.torsionExponent
                val expected =
                    records
                        .map { it.split(':') }
                        .single {
                            it[0] == "0" && it[1].toInt() == exponent && it[2] == "generic"
                        }[6]
                        .hexToByteArray()
                val benchmark =
                    TatePairingBenchmark().also {
                        it.parameterSet = parameter.name
                        it.torsionShape = shape
                        it.prepare()
                    }
                assertion(benchmark, expected)
            }
        }
    }
}
