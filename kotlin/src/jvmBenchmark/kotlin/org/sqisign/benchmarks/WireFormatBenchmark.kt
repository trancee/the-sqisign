// SPDX-License-Identifier: Apache-2.0

package org.sqisign.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import org.sqisign.EncodedPublicKey
import org.sqisign.EncodedSignature
import org.sqisign.ParameterSet
import org.sqisign.WireFormat

/** Canonical parser validation and defensive copying; no curve/signature verification. */
@State(Scope.Benchmark)
public open class WireFormatBenchmark {
    /** All supported encoded widths. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    private lateinit var parameters: ParameterSet
    private lateinit var publicKey: ByteArray
    private lateinit var signature: ByteArray

    /** Creates syntactically canonical public fixtures outside timing. */
    @Setup
    public fun prepare() {
        parameters = ParameterSet.valueOf(parameterSet)
        publicKey = ByteArray(parameters.publicKeyBytes).also { it[0] = 1 }
        signature = ByteArray(parameters.signatureBytes).also { it[0] = 2 }
    }

    /** Returns the parsed key, including its defensive encoding copy. */
    @Benchmark
    public open fun parsePublicKey(): EncodedPublicKey = WireFormat.publicKey(parameters, publicKey)

    /** Returns the parsed signature; synthetic bytes do not constitute a valid signature. */
    @Benchmark
    public open fun parseSignature(): EncodedSignature = WireFormat.signature(parameters, signature)
}
