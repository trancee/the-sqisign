// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.ChallengeHash
import ch.trancee.sqisign.EncodedPublicKey
import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.WireFormat
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Fiat-Shamir hashing with canonical synthetic coefficients, not signature verification. */
@State(Scope.Benchmark)
public open class ChallengeHashBenchmark {
    /** All supported parameter sets; names are resolved only during setup. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** Short and multi-block deterministic messages. */
    @Param("32", "4096") public var messageBytes: Int = 32

    private lateinit var publicKey: EncodedPublicKey
    private lateinit var commitment: ByteArray
    private lateinit var message: ByteArray

    /** Builds inputs outside timing; curve validity is intentionally not established. */
    @Setup
    public fun prepare() {
        val parameters = ParameterSet.valueOf(parameterSet)
        val encoded = ByteArray(parameters.publicKeyBytes)
        encoded[0] = 1
        encoded[encoded.lastIndex] = -1
        publicKey = WireFormat.publicKey(parameters, encoded)
        commitment = ByteArray(parameters.extensionFieldBytes).also { it[0] = 2 }
        message = ByteArray(messageBytes) { it.toByte() }
    }

    /** Includes commitment encoding validation, transcript hashing, allocation, and clearing. */
    @Benchmark
    public open fun compute(): ByteArray = ChallengeHash.compute(publicKey, commitment, message)
}
