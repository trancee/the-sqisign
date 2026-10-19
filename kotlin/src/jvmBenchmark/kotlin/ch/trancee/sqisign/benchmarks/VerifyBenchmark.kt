// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.EncodedPublicKey
import ch.trancee.sqisign.EncodedSignature
import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.SqiSign
import ch.trancee.sqisign.WireFormat
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Variable-time public `SqiSign.verify` of the first public KAT signature. */
@State(Scope.Benchmark)
public open class VerifyBenchmark {
    /** All supported parameter sets. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    private lateinit var publicKey: EncodedPublicKey
    private lateinit var message: ByteArray
    private lateinit var signature: EncodedSignature

    /** Parses the canonical encodings outside timing. */
    @Setup
    public fun prepare() {
        val parameters = ParameterSet.valueOf(parameterSet)
        val (encodedKey, encodedSignature, encodedMessage) =
            verifyInputs(parameters).map { it.hexToByteArray() }
        publicKey = WireFormat.publicKey(parameters, encodedKey)
        signature = WireFormat.signature(parameters, encodedSignature)
        message = encodedMessage
    }

    /** Includes every step of algorithm 3.24; parsing the wire encodings is excluded. */
    @Benchmark public open fun verify(): Boolean = SqiSign.verify(publicKey, message, signature)
}
