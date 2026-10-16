// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.ChallengeHashBenchmark
import ch.trancee.sqisign.benchmarks.PrngBenchmark
import ch.trancee.sqisign.benchmarks.Shake256Benchmark
import ch.trancee.sqisign.benchmarks.WireFormatBenchmark
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BenchmarkWorkloadTest {
    @Test
    fun shakeWorkloadsMatchIndependentVectors() {
        for (size in listOf(0, 136, 4096)) {
            val benchmark = Shake256Benchmark()
            benchmark.inputBytes = size
            benchmark.prepare()
            val expected = shakeVectors.single { it.first == size }.second.take(128)

            val output = benchmark.digest()

            assertEquals(expected, output.toHexString(), "Input size $size")
        }
    }

    @Test
    fun prngWorkloadsUseTheSpecifiedDomainStream() {
        for (size in listOf(64, 4096)) {
            val benchmark = PrngBenchmark()
            benchmark.outputBytes = size
            benchmark.prepare()
            val expected =
                Shake256.digest(ByteArray(48) { it.toByte() } + "key".encodeToByteArray(), size)

            val output = benchmark.expand()

            assertContentEquals(expected, output, "Output size $size")
        }
    }

    @Test
    fun prngWorkloadsRestartTheStreamOnEachInvocation() {
        for (size in listOf(64, 4096)) {
            val benchmark = PrngBenchmark()
            benchmark.outputBytes = size
            benchmark.prepare()
            val previous = benchmark.expand()

            val output = benchmark.expand()

            assertContentEquals(previous, output, "Each invocation must start a fresh stream")
        }
    }

    @Test
    fun challengeWorkloadsHashTheSpecifiedTranscript() {
        for (parameters in ParameterSet.entries) {
            for (size in listOf(32, 4096)) {
                val benchmark = ChallengeHashBenchmark()
                benchmark.parameterSet = parameters.name
                benchmark.messageBytes = size
                benchmark.prepare()
                val publicKey = ByteArray(parameters.publicKeyBytes)
                publicKey[0] = 1
                publicKey[publicKey.lastIndex] = -1
                val commitment = ByteArray(parameters.extensionFieldBytes).also { it[0] = 2 }
                val transcript =
                    "SQI".encodeToByteArray() +
                        publicKey +
                        commitment +
                        ByteArray(size) { it.toByte() }
                val expected = Shake256.digest(transcript, parameters.challengeBytes)

                val output = benchmark.compute()

                assertContentEquals(
                    expected,
                    output,
                    "${parameters.identifier}, message size $size",
                )
            }
        }
    }

    @Test
    fun publicKeyWorkloadsRetainTheirCanonicalBytes() {
        for (parameters in ParameterSet.entries) {
            val benchmark = WireFormatBenchmark()
            benchmark.parameterSet = parameters.name
            benchmark.prepare()
            val expected = ByteArray(parameters.publicKeyBytes).also { it[0] = 1 }

            val output = benchmark.parsePublicKey()

            assertEquals(parameters, output.parameters)
            assertContentEquals(expected, output.toByteArray())
        }
    }

    @Test
    fun signatureWorkloadsRetainTheirCanonicalBytes() {
        for (parameters in ParameterSet.entries) {
            val benchmark = WireFormatBenchmark()
            benchmark.parameterSet = parameters.name
            benchmark.prepare()
            val expected = ByteArray(parameters.signatureBytes).also { it[0] = 2 }

            val output = benchmark.parseSignature()

            assertEquals(parameters, output.parameters)
            assertContentEquals(expected, output.toByteArray())
        }
    }
}
