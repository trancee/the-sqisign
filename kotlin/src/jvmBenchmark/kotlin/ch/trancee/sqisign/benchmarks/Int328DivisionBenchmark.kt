// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Int328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Truncating signed quotient/remainder workloads with representable results and nonzero divisors.
 */
@State(Scope.Benchmark)
public open class Int328DivisionBenchmark {
    /** Covers zero, minimum boundaries, all nonzero sign pairs and a zero quotient. */
    @Param(
        "ZERO_NEGATIVE",
        "MIN_BY_ONE",
        "MIN_EQUAL",
        "NEGATIVE_POSITIVE",
        "POSITIVE_NEGATIVE",
        "BOTH_NEGATIVE",
        "FULL_WIDTH",
        "LARGER_DIVISOR",
    )
    public var operandShape: String = "ZERO_NEGATIVE"

    private lateinit var dividend: Int328
    private lateinit var divisor: Int328

    /** Constructs and decodes both operands outside timing. */
    @Setup
    public fun prepare() {
        val minimum = ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val maximum =
            ByteArray(Int328.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
        val operands =
            when (operandShape) {
                "ZERO_NEGATIVE" -> smallValue(0) to smallValue(-1)
                "MIN_BY_ONE" -> minimum to smallValue(1)
                "MIN_EQUAL" -> minimum to minimum
                "NEGATIVE_POSITIVE" -> minimum to smallValue(7)
                "POSITIVE_NEGATIVE" -> maximum to smallValue(-7)
                "BOTH_NEGATIVE" -> minimum to smallValue(-7)
                "FULL_WIDTH" ->
                    ByteArray(Int328.ENCODED_BYTES) { 0xaa.toByte() } to
                        ByteArray(Int328.ENCODED_BYTES) { 0x55 }
                "LARGER_DIVISOR" -> smallValue(-1) to minimum
                else ->
                    throw IllegalArgumentException(
                        "Unknown public signed division operand shape: $operandShape",
                    )
            }
        dividend = Int328.fromLittleEndian(operands.first)
        divisor = Int328.fromLittleEndian(operands.second)
    }

    /**
     * Retains both signed results, including magnitudes, division, sign restoration and ownership.
     */
    @Benchmark public open fun divideAndRemainder(): Any = dividend.divideAndRemainder(divisor)

    private fun smallValue(value: Int): ByteArray =
        ByteArray(Int328.ENCODED_BYTES) { if (value < 0) -1 else 0 }.also { it[0] = value.toByte() }
}
