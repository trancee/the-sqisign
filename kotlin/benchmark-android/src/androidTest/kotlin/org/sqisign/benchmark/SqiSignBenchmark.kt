package org.sqisign.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.sqisign.ParameterSet
import org.sqisign.SqiSign
import org.sqisign.SqiSignKeyPair

/** Key generation, signing (32-byte message) and verification per parameter set, matching the JVM benchmarks. */
@RunWith(Parameterized::class)
class SqiSignBenchmark(private val parameterSet: ParameterSet) {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val message = ByteArray(MESSAGE_BYTES) { it.toByte() }

    @Test
    fun keyGeneration() {
        benchmarkRule.measureRepeated { SqiSign.generateKeyPair(parameterSet) }
    }

    @Test
    fun signing() {
        val keyPair = SqiSign.generateKeyPair(parameterSet)
        benchmarkRule.measureRepeated { SqiSign.sign(message, keyPair.secretKey) }
    }

    @Test
    fun verification() {
        val keyPair: SqiSignKeyPair = SqiSign.generateKeyPair(parameterSet)
        val signature = SqiSign.sign(message, keyPair.secretKey)
        check(SqiSign.verify(message, signature, keyPair.publicKey)) { "benchmark signature does not verify" }
        benchmarkRule.measureRepeated { check(SqiSign.verify(message, signature, keyPair.publicKey)) }
    }

    companion object {
        const val MESSAGE_BYTES = 32

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameterSets(): List<ParameterSet> = ParameterSet.entries
    }
}
