// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.ThetaChainBenchmark
import ch.trancee.sqisign.hd.ThetaChainVectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThetaChainBenchmarkTest {
    @Test
    fun verificationChainRetainsTheIndependentFirstCodomain() {
        for (parameter in ParameterSet.entries) {
            val records =
                when (parameter) {
                    ParameterSet.NIST_I -> ThetaChainVectors.p324Chains
                    ParameterSet.NIST_III -> ThetaChainVectors.p500Chains
                    ParameterSet.NIST_V -> ThetaChainVectors.p664Chains
                }
            val expected =
                records.map { it.split(':') }.single { it[0] == "kat0" && it[1] == "-1" }[10]
            val benchmark =
                ThetaChainBenchmark().also {
                    it.parameterSet = parameter.name
                    it.prepare()
                }

            val actual = assertIs<ByteArray>(benchmark.verify()).toHexString()

            assertEquals(expected.substringBefore(','), actual, parameter.name)
        }
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = ThetaChainBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }
}
