// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.divRem
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.arithmetic.plus
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Bounded signed integer workloads at each parameter set's quaternion capacity. */
@State(Scope.Benchmark)
public open class IbzBenchmark {
    /** Selects the `ibz_t` capacity of NIST level I, III or V. */
    @Param("NIST_I", "NIST_III", "NIST_V") public var parameterSet: String = "NIST_I"

    /** One-word operands, or a dividend of half the capacity over a quarter-capacity divisor. */
    @Param("WORD", "HALF_CAPACITY") public var operandShape: String = "WORD"

    private lateinit var left: Ibz
    private lateinit var right: Ibz

    /** Parses deterministic public operands outside the measured operation. */
    @Setup
    public fun prepare() {
        val capacity = ibzBenchmarkCapacity(parameterSet)
        val bits = ibzBenchmarkOperandBits(operandShape, capacity)
        left = parseOperand(capacity, ibzBenchmarkOperand(bits, negative = true))
        right = parseOperand(capacity, ibzBenchmarkOperand(bits / 2, negative = false))
    }

    /** Checked signed addition. */
    @Benchmark public open fun add(): Any = left + right

    /** Checked signed multiplication. */
    @Benchmark public open fun multiply(): Any = left * right

    /** Truncated signed division with remainder. */
    @Benchmark public open fun divideAndRemainder(): Any = left.divRem(right)
}

private fun parseOperand(capacity: Int, text: String): Ibz =
    requireNotNull(Ibz.parse(capacity, text, HEXADECIMAL))

private const val NIST_I_CAPACITY = 1920
private const val NIST_III_CAPACITY = 2560
private const val NIST_V_CAPACITY = 3520
private const val WORD_OPERAND_BITS = 64
private const val HEX_DIGIT_BITS = 4
private const val HEXADECIMAL = 16

internal fun ibzBenchmarkCapacity(parameterSet: String): Int =
    when (parameterSet) {
        "NIST_I" -> NIST_I_CAPACITY
        "NIST_III" -> NIST_III_CAPACITY
        "NIST_V" -> NIST_V_CAPACITY
        else -> throw IllegalArgumentException("Unknown parameter set: $parameterSet")
    }

internal fun ibzBenchmarkOperandBits(operandShape: String, capacity: Int): Int =
    when (operandShape) {
        "WORD" -> WORD_OPERAND_BITS
        "HALF_CAPACITY" -> capacity / 2
        else -> throw IllegalArgumentException("Unknown ibz operand shape: $operandShape")
    }

/** Signed hexadecimal text with exactly [bits] magnitude bits from a fixed public digit pattern. */
internal fun ibzBenchmarkOperand(bits: Int, negative: Boolean): String {
    val digits = (bits + HEX_DIGIT_BITS - 1) / HEX_DIGIT_BITS
    val pattern = "9e3779b97f4a7c15f39cc0605cedc834"
    val body = StringBuilder()
    while (body.length < digits) {
        body.append(pattern)
    }
    val leading = (1 shl ((bits - 1) % HEX_DIGIT_BITS)).toString(HEXADECIMAL)
    return (if (negative) "-" else "") + leading + body.substring(0, digits - 1)
}
