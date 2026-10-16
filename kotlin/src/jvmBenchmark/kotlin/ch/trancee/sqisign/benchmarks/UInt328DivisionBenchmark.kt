// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.UInt328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Unsigned quotient/remainder workloads with explicitly nonzero divisors. */
@State(Scope.Benchmark)
public open class UInt328DivisionBenchmark {
    /** Synthetic public shapes cover zero, equality, long quotients and remainder-only results. */
    @Param(
        "ZERO_DIVIDEND",
        "MAX_BY_ONE",
        "MAX_EQUAL",
        "SMALL_DIVISOR",
        "FULL_WIDTH",
        "LARGER_DIVISOR",
    )
    public var operandShape: String = "ZERO_DIVIDEND"

    private lateinit var dividend: UInt328
    private lateinit var divisor: UInt328

    /** Constructs and decodes both operands outside the measured operation. */
    @Setup
    public fun prepare() {
        val zero = ByteArray(UInt328.ENCODED_BYTES)
        val one = ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 }
        val maximum = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val lowAlternating = ByteArray(UInt328.ENCODED_BYTES) { 0x55 }
        val highAlternating = ByteArray(UInt328.ENCODED_BYTES) { 0xaa.toByte() }
        val operands =
            when (operandShape) {
                "ZERO_DIVIDEND" -> zero to one
                "MAX_BY_ONE" -> maximum to one
                "MAX_EQUAL" -> maximum to maximum
                "SMALL_DIVISOR" -> maximum to ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 7 }
                "FULL_WIDTH" -> highAlternating to lowAlternating
                "LARGER_DIVISOR" -> lowAlternating to highAlternating
                else ->
                    throw IllegalArgumentException(
                        "Unknown public division operand shape: $operandShape",
                    )
            }
        dividend = UInt328.fromLittleEndian(operands.first)
        divisor = UInt328.fromLittleEndian(operands.second)
    }

    /** Retains the complete quotient/remainder result without exposing the internal types. */
    @Benchmark public open fun divideAndRemainder(): Any = dividend.divideAndRemainder(divisor)
}
