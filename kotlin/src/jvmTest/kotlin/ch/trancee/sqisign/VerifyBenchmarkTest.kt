// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.VerifyBenchmark
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VerifyBenchmarkTest {
    @Test
    fun everyBenchmarkedSignatureVerifies() {
        for (parameter in ParameterSet.entries) {
            val benchmark =
                VerifyBenchmark().also {
                    it.parameterSet = parameter.name
                    it.prepare()
                }

            val valid = benchmark.verify()

            assertTrue(valid, parameter.name)
        }
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = VerifyBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }
}
