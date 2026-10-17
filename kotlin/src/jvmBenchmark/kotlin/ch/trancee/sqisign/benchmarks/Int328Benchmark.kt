// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Int328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Checked signed arithmetic on public fixtures whose sums and differences fit. */
@State(Scope.Benchmark)
public open class Int328Benchmark {
    /** Covers both signed bounds, negative equality, cancellation and a radix carry chain. */
    @Param(
        "ZERO_EQUAL",
        "MIN_BY_ZERO",
        "MAX_BY_ZERO",
        "NEGATIVE_EQUAL",
        "MIXED_SIGN",
        "CARRY_CHAIN",
    )
    public var operandShape: String = "ZERO_EQUAL"

    private lateinit var leftBytes: ByteArray
    private lateinit var left: Int328
    private lateinit var right: Int328

    /** Constructs all fixtures and resolves their shapes outside timing. */
    @Setup
    public fun prepare() {
        val zero = ByteArray(Int328.ENCODED_BYTES)
        val operands =
            when (operandShape) {
                "ZERO_EQUAL" -> zero to zero
                "MIN_BY_ZERO" -> zero.copyOf().also { it[it.lastIndex] = Byte.MIN_VALUE } to zero
                "MAX_BY_ZERO" ->
                    ByteArray(Int328.ENCODED_BYTES) { -1 }
                        .also { it[it.lastIndex] = Byte.MAX_VALUE } to zero
                "NEGATIVE_EQUAL" -> highByte(0xc0) to highByte(0xc0)
                "MIXED_SIGN" -> highByte(0x20) to highByte(0xe0)
                "CARRY_CHAIN" -> radixCarryInput() to zero.copyOf().also { it[0] = 1 }
                else ->
                    throw IllegalArgumentException(
                        "Unknown public signed operand shape: $operandShape",
                    )
            }
        leftBytes = operands.first
        left = Int328.fromLittleEndian(leftBytes)
        right = Int328.fromLittleEndian(operands.second)
    }

    /** Includes owned unsigned limb decoding and signed wrapper construction. */
    @Benchmark public open fun decode(): Any = Int328.fromLittleEndian(leftBytes)

    /** Includes the exact-width owned two's-complement byte result. */
    @Benchmark public open fun encode(): ByteArray = left.toLittleEndian()

    /** Includes signed ordering and variable-time unsigned comparison where needed. */
    @Benchmark public open fun compare(): Int = left.compareTo(right)

    /** Includes the sum, signed overflow check and immutable result construction. */
    @Benchmark public open fun add(): Any = left.add(right)

    /** Includes the difference, signed overflow check and immutable result construction. */
    @Benchmark public open fun subtract(): Any = left.subtract(right)

    private fun highByte(value: Int): ByteArray =
        ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = value.toByte() }

    private fun radixCarryInput(): ByteArray =
        ByteArray(Int328.ENCODED_BYTES) { index ->
            when {
                index < CARRY_BIT / Byte.SIZE_BITS -> -1
                index == CARRY_BIT / Byte.SIZE_BITS ->
                    ((1 shl (CARRY_BIT % Byte.SIZE_BITS)) - 1).toByte()
                else -> 0
            }
        }
}

private const val CARRY_BIT = 315
