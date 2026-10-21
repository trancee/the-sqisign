// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.UInt328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Variable-time Euclidean GCD on synthetic public unsigned operands. */
@State(Scope.Benchmark)
public open class UInt328GcdBenchmark {
    /** Shapes include zero conventions, shared factors and a long Euclidean chain. */
    @Param(
        "ZERO_EQUAL",
        "ZERO_MAX",
        "MAX_ZERO",
        "MAX_EQUAL",
        "SHARED_FACTOR",
        "COPRIME",
        "FIBONACCI",
    )
    public var operandShape: String = "ZERO_EQUAL"

    private lateinit var left: UInt328
    private lateinit var right: UInt328

    /** Constructs all fixtures outside timing, including the largest fitting Fibonacci pair. */
    @Setup
    public fun prepare() {
        val zero = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES))
        val maximum = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { -1 })
        val operands =
            when (operandShape) {
                "ZERO_EQUAL" -> zero to zero
                "ZERO_MAX" -> zero to maximum
                "MAX_ZERO" -> maximum to zero
                "MAX_EQUAL" -> maximum to maximum
                "SHARED_FACTOR" -> highFactor(3) to highFactor(5)
                "COPRIME" ->
                    maximum to
                        UInt328.fromLittleEndian(
                            ByteArray(UInt328.ENCODED_BYTES) { -1 }.also { it[0] = -2 },
                        )
                "FIBONACCI" -> fibonacciPair()
                else ->
                    throw IllegalArgumentException(
                        "Unknown public GCD operand shape: $operandShape",
                    )
            }
        left = operands.first
        right = operands.second
    }

    /** Retains the immutable result; includes Euclidean division and its allocations. */
    @Benchmark public open fun gcd(): Any = left.gcd(right)

    private fun highFactor(factor: Int): UInt328 =
        UInt328.fromLittleEndian(
            ByteArray(UInt328.ENCODED_BYTES).also { it[it.lastIndex] = factor.toByte() },
        )

    private fun fibonacciPair(): Pair<UInt328, UInt328> {
        var previous = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES))
        var current = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 })
        while (true) {
            val next = previous.add(current)
            if (next.carry != 0) return current to previous
            previous = current
            current = next.value
        }
    }
}
