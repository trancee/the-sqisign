// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.Shake256
import ch.trancee.sqisign.arithmetic.UInt328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Variable-time modular inversion on synthetic public inputs; all workloads are invertible. */
@State(Scope.Benchmark)
public open class UInt328ModInverseBenchmark {
    /** Exercises one, maximum-width residues, even/composite moduli and a full-width reduction. */
    @Param("ONE", "MAX_MINUS_ONE", "EVEN_MODULUS", "COMPOSITE", "FULL_WIDTH")
    public var operandShape: String = "ONE"

    private lateinit var value: UInt328
    private lateinit var modulus: UInt328

    /** Constructs and decodes fixtures outside timing, without computing an inverse. */
    @Setup
    public fun prepare() {
        val maximumBytes = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val powerOfTwoBytes =
            ByteArray(UInt328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val operands =
            when (operandShape) {
                "ONE" -> smallValue(1) to UInt328.fromLittleEndian(maximumBytes)
                "MAX_MINUS_ONE" ->
                    UInt328.fromLittleEndian(maximumBytes.copyOf().also { it[0] = -2 }) to
                        UInt328.fromLittleEndian(maximumBytes)
                "EVEN_MODULUS" ->
                    UInt328.fromLittleEndian(maximumBytes) to
                        UInt328.fromLittleEndian(powerOfTwoBytes)
                "COMPOSITE" -> smallValue(5) to smallValue(12)
                "FULL_WIDTH" ->
                    UInt328.fromLittleEndian(
                        Shake256.digest(
                                "public UInt328 inverse benchmark".encodeToByteArray(),
                                UInt328.ENCODED_BYTES,
                            )
                            .also { it[0] = (it[0].toInt() or 1).toByte() },
                    ) to UInt328.fromLittleEndian(powerOfTwoBytes)
                else ->
                    throw IllegalArgumentException(
                        "Unknown public modular inverse operand shape: $operandShape",
                    )
            }
        value = operands.first
        modulus = operands.second
    }

    /** Retains the canonical inverse, including coefficient products, reduction and allocations. */
    @Benchmark public open fun modInverse(): Any = value.modInverse(modulus)

    private fun smallValue(value: Int): UInt328 =
        UInt328.fromLittleEndian(
            ByteArray(UInt328.ENCODED_BYTES).also { it[0] = value.toByte() },
        )
}
