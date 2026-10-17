// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Int328
import ch.trancee.sqisign.arithmetic.Int656
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Truncating signed wide quotient/remainder workloads with representable results. */
@State(Scope.Benchmark)
public open class Int656DivisionBenchmark {
    /** Covers signed bounds, sign restoration, full-width inputs and zero quotients. */
    @Param(
        "ZERO_NEGATIVE",
        "MIN_BY_ONE",
        "MIN_BY_NEGATIVE_MAX",
        "MAX_BY_NEGATIVE_ONE",
        "NEGATIVE_BY_POSITIVE",
        "POSITIVE_BY_NEGATIVE",
        "FULL_WIDTH",
        "LARGER_DIVISOR",
    )
    public var operandShape: String = "ZERO_NEGATIVE"

    private lateinit var dividend: Int656
    private lateinit var divisor: Int328

    /** Constructs synthetic public values outside the timed operation. */
    @Setup
    public fun prepare() {
        val minimum = ByteArray(Int656.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val maximum =
            ByteArray(Int656.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
        val narrowMaximum =
            ByteArray(Int328.ENCODED_BYTES) { -1 }.also { it[it.lastIndex] = Byte.MAX_VALUE }
        val narrowMinimum =
            ByteArray(Int328.ENCODED_BYTES).also { it[it.lastIndex] = Byte.MIN_VALUE }
        val negativeOne = ByteArray(Int328.ENCODED_BYTES) { -1 }
        val operands =
            when (operandShape) {
                "ZERO_NEGATIVE" -> ByteArray(Int656.ENCODED_BYTES) to negativeOne
                "MIN_BY_ONE" -> minimum to smallValue(1)
                "MIN_BY_NEGATIVE_MAX" -> minimum to negate(narrowMaximum)
                "MAX_BY_NEGATIVE_ONE" -> maximum to negativeOne
                "NEGATIVE_BY_POSITIVE" ->
                    ByteArray(Int656.ENCODED_BYTES) { 0xaa.toByte() } to narrowMaximum
                "POSITIVE_BY_NEGATIVE" ->
                    ByteArray(Int656.ENCODED_BYTES) { 0x55 } to negate(narrowMaximum)
                "FULL_WIDTH" ->
                    ByteArray(Int656.ENCODED_BYTES) { 0xaa.toByte() } to
                        ByteArray(Int328.ENCODED_BYTES) { 0x55 }
                "LARGER_DIVISOR" -> smallValue(7).copyOf(Int656.ENCODED_BYTES) to narrowMinimum
                else ->
                    throw IllegalArgumentException(
                        "Unknown public signed wide division operand shape: $operandShape",
                    )
            }
        dividend = Int656.fromLittleEndian(operands.first)
        divisor = Int328.fromLittleEndian(operands.second)
    }

    /** Retains the full signed quotient and remainder, including sign restoration. */
    @Benchmark public open fun divideAndRemainder(): Any = dividend.divideAndRemainder(divisor)

    private fun smallValue(value: Int): ByteArray =
        ByteArray(Int328.ENCODED_BYTES).also { it[0] = value.toByte() }

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
}

private const val BYTE_MASK = 0xff
