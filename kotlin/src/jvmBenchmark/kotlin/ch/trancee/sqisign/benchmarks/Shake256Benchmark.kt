// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.Shake256
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** One-shot SHAKE256 including sponge allocation, 64 output bytes, and state clearing. */
@State(Scope.Benchmark)
public open class Shake256Benchmark {
    /** Empty input, one complete rate block, and a multi-block input. */
    @Param("0", "136", "4096") public var inputBytes: Int = 0

    private lateinit var input: ByteArray

    /** Prepares deterministic public input outside the measured invocation. */
    @Setup
    public fun prepare() {
        input = ByteArray(inputBytes) { it.toByte() }
    }

    /** Returns the digest so JMH retains the computed output. */
    @Benchmark public open fun digest(): ByteArray = Shake256.digest(input, outputLength = 64)
}
