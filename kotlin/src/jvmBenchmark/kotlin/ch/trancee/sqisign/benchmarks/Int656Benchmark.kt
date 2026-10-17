// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Int656
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Checked signed arithmetic on wide public fixtures whose sums and differences fit. */
@State(Scope.Benchmark)
public open class Int656Benchmark {
    /** Covers signed bounds, cancellation and carry propagation across the full width. */
    @Param(
        "ZERO_EQUAL",
        "MIN_BY_ZERO",
        "MAX_BY_ZERO",
        "NEGATIVE_EQUAL",
        "MIXED_SIGN",
        "CARRY_CHAIN",
    )
    public var operandShape: String = "ZERO_EQUAL"

    private lateinit var left: Int656
    private lateinit var right: Int656

    /** Builds and decodes both public operands outside timing. */
    @Setup
    public fun prepare() {
        val zero = ByteArray(Int656.ENCODED_BYTES)
        val minimum = ByteArray(Int656.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val maximum =
            ByteArray(Int656.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
        val operands =
            when (operandShape) {
                "ZERO_EQUAL" -> zero to zero
                "MIN_BY_ZERO" -> minimum to zero
                "MAX_BY_ZERO" -> maximum to zero
                "NEGATIVE_EQUAL" -> negate(powerOfTwo(654)).let { it to it.copyOf() }
                "MIXED_SIGN" -> powerOfTwo(653) to negate(powerOfTwo(653))
                "CARRY_CHAIN" ->
                    carryChainValue() to ByteArray(Int656.ENCODED_BYTES).also { it[0] = 1 }
                else ->
                    throw IllegalArgumentException(
                        "Unknown public wide signed operand shape: $operandShape",
                    )
            }
        left = Int656.fromLittleEndian(operands.first)
        right = Int656.fromLittleEndian(operands.second)
    }

    /** Includes signed ordering and the full-width byte comparison. */
    @Benchmark public open fun compare(): Int = left.compareTo(right)

    /** Includes full-width addition, signed overflow validation and owned result construction. */
    @Benchmark public open fun add(): Any = left.add(right)

    /**
     * Includes full-width subtraction, signed overflow validation and owned result construction.
     */
    @Benchmark public open fun subtract(): Any = left.subtract(right)

    private fun powerOfTwo(bit: Int): ByteArray =
        ByteArray(Int656.ENCODED_BYTES).also {
            it[bit / Byte.SIZE_BITS] = (1 shl (bit % Byte.SIZE_BITS)).toByte()
        }

    private fun negate(value: ByteArray): ByteArray {
        val result = value.copyOf()
        var carry = 1
        for (index in result.indices) {
            val negated = (result[index].toInt().inv() and BYTE_MASK) + carry
            result[index] = negated.toByte()
            carry = negated ushr Byte.SIZE_BITS
        }
        return result
    }

    private fun carryChainValue(): ByteArray =
        ByteArray(Int656.ENCODED_BYTES) { index ->
            when {
                index < CARRY_BIT / Byte.SIZE_BITS -> -1
                index == CARRY_BIT / Byte.SIZE_BITS ->
                    ((1 shl (CARRY_BIT % Byte.SIZE_BITS)) - 1).toByte()
                else -> 0
            }
        }
}

private const val BYTE_MASK = 0xff
private const val CARRY_BIT = 645
