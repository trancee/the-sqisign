// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.PrngDomain
import ch.trancee.sqisign.SqiSignPrng
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Fresh deterministic PRNG expansion including initialization, output allocation, and clearing. */
@State(Scope.Benchmark)
public open class PrngBenchmark {
    /** Small output and multi-block expansion sizes. */
    @Param("64", "4096") public var outputBytes: Int = 64

    private lateinit var seed: ByteArray

    /** Prepares a synthetic public seed, never production entropy. */
    @Setup
    public fun prepare() {
        seed = ByteArray(SEED_BYTES) { it.toByte() }
    }

    /** Returns a fresh key-domain stream prefix; state never carries across invocations. */
    @Benchmark
    public open fun expand(): ByteArray {
        val prng = SqiSignPrng(seed, PrngDomain.KEY_GENERATION)
        return try {
            prng.bytes(outputBytes)
        } finally {
            prng.clear()
        }
    }
}

private const val SEED_BYTES = 48
