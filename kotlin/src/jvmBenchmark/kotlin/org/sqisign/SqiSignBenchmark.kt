package org.sqisign

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Times one key generation, signature, or verification through the public API, per parameter set. */
@State(Scope.Benchmark)
class SqiSignBenchmark {
    @Param("P324_3", "P500_27", "P664_17")
    var parameterSet: String = "P324_3"

    private val message = ByteArray(MESSAGE_BYTES) { it.toByte() }
    private lateinit var keyPair: SqiSignKeyPair
    private lateinit var signature: ByteArray

    @Setup
    fun prepare() {
        keyPair = SqiSign.generateKeyPair(ParameterSet.valueOf(parameterSet))
        signature = SqiSign.sign(message, keyPair.secretKey)
        check(SqiSign.verify(message, signature, keyPair.publicKey)) { "benchmark signature does not verify" }
    }

    @Benchmark
    fun keyGeneration(): SqiSignKeyPair = SqiSign.generateKeyPair(keyPair.parameterSet)

    @Benchmark
    fun signing(): ByteArray = SqiSign.sign(message, keyPair.secretKey)

    @Benchmark
    fun verification(): Boolean = SqiSign.verify(message, signature, keyPair.publicKey)

    private companion object {
        const val MESSAGE_BYTES = 32
    }
}
