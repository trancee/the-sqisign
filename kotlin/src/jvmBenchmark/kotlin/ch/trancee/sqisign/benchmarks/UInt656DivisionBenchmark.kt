// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.UInt328
import ch.trancee.sqisign.arithmetic.UInt656
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Exact wide quotient/remainder workloads with synthetic nonzero divisors. */
@State(Scope.Benchmark)
public open class UInt656DivisionBenchmark {
    /** Shapes cover full-width quotients, high-bit divisors and exact product division. */
    @Param(
        "ZERO_DIVIDEND",
        "MAX_BY_ONE",
        "MAX_BY_MAX",
        "SMALL_DIVISOR",
        "HIGH_BIT_DIVISOR",
        "LARGER_DIVISOR",
        "PRODUCT_BY_FACTOR",
    )
    public var operandShape: String = "ZERO_DIVIDEND"

    private lateinit var dividend: UInt656
    private lateinit var divisor: UInt328

    /** Builds and decodes public inputs outside timing, including the product fixture. */
    @Setup
    public fun prepare() {
        val maximum = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val maximumWide = ByteArray(UInt656.ENCODED_BYTES) { -1 }
        val one = ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 }
        val operands =
            when (operandShape) {
                "ZERO_DIVIDEND" -> ByteArray(UInt656.ENCODED_BYTES) to one
                "MAX_BY_ONE" -> maximumWide to one
                "MAX_BY_MAX" -> maximumWide to maximum
                "SMALL_DIVISOR" ->
                    maximumWide to ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 7 }
                "HIGH_BIT_DIVISOR" ->
                    maximumWide to one.copyOf().also { it[it.lastIndex] = 0x80.toByte() }
                "LARGER_DIVISOR" -> one.copyOf(UInt656.ENCODED_BYTES) to maximum
                "PRODUCT_BY_FACTOR" -> {
                    val factor = UInt328.fromLittleEndian(maximum)
                    factor.multiply(factor).toLittleEndian() to maximum
                }
                else ->
                    throw IllegalArgumentException(
                        "Unknown public wide division operand shape: $operandShape",
                    )
            }
        dividend = UInt656.fromLittleEndian(operands.first)
        divisor = UInt328.fromLittleEndian(operands.second)
    }

    /** Includes divisor export, scratch storage, and both independently owned results. */
    @Benchmark public open fun divideAndRemainder(): Any = dividend.divideAndRemainder(divisor)
}
