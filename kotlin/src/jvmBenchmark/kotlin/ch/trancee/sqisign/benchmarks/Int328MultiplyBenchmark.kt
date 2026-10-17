// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Int328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Exact signed multiplication on synthetic public operands, including both signed bounds. */
@State(Scope.Benchmark)
public open class Int328MultiplyBenchmark {
    /** Exercises sign conversion, zero normalization and products wider than the operand range. */
    @Param("ZERO_NEGATIVE", "MIN_SQUARE", "MIN_NEGATIVE_ONE", "MIN_MAX", "MAX_SQUARE", "FULL_WIDTH")
    public var operandShape: String = "ZERO_NEGATIVE"

    private lateinit var left: Int328
    private lateinit var right: Int328

    /** Constructs and decodes fixtures outside the measured operation. */
    @Setup
    public fun prepare() {
        val minimum = ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val maximum =
            ByteArray(Int328.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
        val operands =
            when (operandShape) {
                "ZERO_NEGATIVE" -> ByteArray(Int328.ENCODED_BYTES) to minimum
                "MIN_SQUARE" -> minimum to minimum
                "MIN_NEGATIVE_ONE" -> minimum to ByteArray(Int328.ENCODED_BYTES) { -1 }
                "MIN_MAX" -> minimum to maximum
                "MAX_SQUARE" -> maximum to maximum
                "FULL_WIDTH" ->
                    ByteArray(Int328.ENCODED_BYTES) { 0x55 } to
                        ByteArray(Int328.ENCODED_BYTES) { 0xaa.toByte() }
                else ->
                    throw IllegalArgumentException(
                        "Unknown public signed multiplication operand shape: $operandShape",
                    )
            }
        left = Int328.fromLittleEndian(operands.first)
        right = Int328.fromLittleEndian(operands.second)
    }

    /** Retains the full product, including unsigned magnitudes, sign encoding and ownership. */
    @Benchmark public open fun multiply(): Any = left.multiply(right)
}
