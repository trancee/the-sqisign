// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.IsogenyBenchmark
import ch.trancee.sqisign.benchmarks.isogenyInputs
import ch.trancee.sqisign.ec.IsogenyVectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IsogenyBenchmarkTest {
    @Test
    fun normalizationRetainsTheIndependentCoefficient() {
        forEachParameter { benchmark, records ->
            val expected = records.normalizations.single { it[2] == records.kernel }[3]

            val actual = assertIs<ByteArray>(benchmark.normalize()).toHexString()

            assertEquals(expected, actual)
        }
    }

    @Test
    fun fourIsogenyRetainsTheIndependentCodomainAndImage() {
        forEachParameter { benchmark, records ->
            val expected =
                records.fourIsogenies
                    .single { it[1] == records.kernel && it[2] == records.point }
                    .let { it[3] + it[4] }

            val actual = assertIs<ByteArray>(benchmark.fourIsogeny()).toHexString()

            assertEquals(expected, actual)
        }
    }

    @Test
    fun securityLengthChainRetainsTheIndependentCoefficient() {
        forEachParameter { benchmark, records ->
            val expected =
                records.chains
                    .single { it[2] == records.seed && it[3] == records.length.toString() }[4]

            val actual = assertIs<ByteArray>(benchmark.chain()).toHexString()

            assertEquals(expected, actual)
        }
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = IsogenyBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }

    private class Records(
        val normalizations: List<List<String>>,
        val fourIsogenies: List<List<String>>,
        val chains: List<List<String>>,
        val length: Int,
        inputs: List<String>,
    ) {
        val seed = inputs[0]
        val kernel = inputs[1]
        val point = inputs[2]
    }

    private fun forEachParameter(assertion: (IsogenyBenchmark, Records) -> Unit) {
        for (parameter in ParameterSet.entries) {
            val records =
                when (parameter) {
                    ParameterSet.NIST_I ->
                        Records(
                            IsogenyVectors.p324Normalizations.map { it.split(':') },
                            IsogenyVectors.p324FourIsogenies.map { it.split(':') },
                            IsogenyVectors.p324Chains.map { it.split(':') },
                            parameter.securityBits,
                            isogenyInputs(parameter),
                        )
                    ParameterSet.NIST_III ->
                        Records(
                            IsogenyVectors.p500Normalizations.map { it.split(':') },
                            IsogenyVectors.p500FourIsogenies.map { it.split(':') },
                            IsogenyVectors.p500Chains.map { it.split(':') },
                            parameter.securityBits,
                            isogenyInputs(parameter),
                        )
                    ParameterSet.NIST_V ->
                        Records(
                            IsogenyVectors.p664Normalizations.map { it.split(':') },
                            IsogenyVectors.p664FourIsogenies.map { it.split(':') },
                            IsogenyVectors.p664Chains.map { it.split(':') },
                            parameter.securityBits,
                            isogenyInputs(parameter),
                        )
                }
            val benchmark =
                IsogenyBenchmark().also {
                    it.parameterSet = parameter.name
                    it.prepare()
                }
            assertion(benchmark, records)
        }
    }
}
