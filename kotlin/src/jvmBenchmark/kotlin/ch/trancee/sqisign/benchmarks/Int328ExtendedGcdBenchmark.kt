// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Int328
import ch.trancee.sqisign.arithmetic.UInt328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Variable-time signed extended GCD on synthetic public operands. */
@State(Scope.Benchmark)
public open class Int328ExtendedGcdBenchmark {
    /** Covers zero conventions, signed boundaries and a long Euclidean chain. */
    @Param(
        "ZERO_EQUAL",
        "ZERO_NEGATIVE",
        "MIN_ZERO",
        "MIN_ONE",
        "MIN_MAX",
        "FULL_WIDTH",
        "FIBONACCI",
    )
    public var operandShape: String = "ZERO_EQUAL"

    private lateinit var left: Int328
    private lateinit var right: Int328

    /** Constructs and decodes operands outside the measured operation. */
    @Setup
    public fun prepare() {
        val minimum = ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val maximum =
            ByteArray(Int328.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
        val zero = ByteArray(Int328.ENCODED_BYTES)
        val one = smallValue(1)
        val operands =
            when (operandShape) {
                "ZERO_EQUAL" -> zero to zero
                "ZERO_NEGATIVE" -> zero to minimum
                "MIN_ZERO" -> minimum to zero
                "MIN_ONE" -> minimum to one
                "MIN_MAX" -> minimum to maximum
                "FULL_WIDTH" ->
                    ByteArray(Int328.ENCODED_BYTES) { 0xaa.toByte() } to
                        ByteArray(Int328.ENCODED_BYTES) { 0x55 }
                "FIBONACCI" -> fibonacciPair()
                else ->
                    throw IllegalArgumentException(
                        "Unknown public signed extended-GCD operand shape: $operandShape",
                    )
            }
        left = Int328.fromLittleEndian(operands.first)
        right = Int328.fromLittleEndian(operands.second)
    }

    /** Retains the exact GCD and both signed coefficients. */
    @Benchmark public open fun extendedGcd(): Any = left.extendedGcd(right)

    private fun smallValue(value: Int): ByteArray =
        ByteArray(Int328.ENCODED_BYTES).also { it[0] = value.toByte() }

    private fun fibonacciPair(): Pair<ByteArray, ByteArray> {
        var previous = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES))
        var current = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 })
        while (true) {
            val next = previous.add(current).value
            if ((next.toLittleEndian().last().toInt() and SIGN_BIT_MASK) != 0) {
                return current.toLittleEndian() to previous.toLittleEndian()
            }
            previous = current
            current = next
        }
    }

    private companion object {
        const val SIGN_BIT_MASK: Int = 0x80
    }
}
