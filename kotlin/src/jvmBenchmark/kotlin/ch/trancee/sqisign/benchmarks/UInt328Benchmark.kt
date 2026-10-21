// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.UInt328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** Fixed-width unsigned operations, including result allocation but excluding fixture setup. */
@State(Scope.Benchmark)
public open class UInt328Benchmark {
    /** Public operand shapes exercise equal values, full carry/borrow chains, and dense limbs. */
    @Param("ZERO_EQUAL", "MAX_EQUAL", "CARRY_CHAIN", "BORROW_CHAIN", "FULL_WIDTH")
    public var operandShape: String = "ZERO_EQUAL"

    private lateinit var encoded: ByteArray
    private lateinit var left: UInt328
    private lateinit var right: UInt328

    /** Prepares immutable operands and the decoding input outside timing. */
    @Setup
    public fun prepare() {
        val zero = ByteArray(UInt328.ENCODED_BYTES)
        val one = ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 }
        val maximum = ByteArray(UInt328.ENCODED_BYTES) { -1 }
        val operands =
            when (operandShape) {
                "ZERO_EQUAL" -> zero to zero
                "MAX_EQUAL" -> maximum to maximum
                "CARRY_CHAIN" -> maximum to one
                "BORROW_CHAIN" -> zero to one
                "FULL_WIDTH" ->
                    ByteArray(UInt328.ENCODED_BYTES) { 0x55 } to
                        ByteArray(UInt328.ENCODED_BYTES) { 0xaa.toByte() }
                else ->
                    throw IllegalArgumentException("Unknown public operand shape: $operandShape")
            }
        encoded = operands.first
        left = UInt328.fromLittleEndian(encoded)
        right = UInt328.fromLittleEndian(operands.second)
    }

    /**
     * Returns the internal value opaquely to keep its allocation live without exposing its type.
     */
    @Benchmark public open fun decode(): Any = UInt328.fromLittleEndian(encoded)

    /** Exports one owned 41-byte encoding. */
    @Benchmark public open fun encode(): ByteArray = left.toLittleEndian()

    /** Measures variable-time order, including both equal and unequal operands. */
    @Benchmark public open fun compare(): Int = left.compareTo(right)

    /** Retains the entire result, including its low value and carry flag. */
    @Benchmark public open fun add(): Any = left.add(right)

    /** Retains the entire result, including its low value and borrow flag. */
    @Benchmark public open fun subtract(): Any = left.subtract(right)

    /** Retains all 656 product bits, including result encoding and ownership copies. */
    @Benchmark public open fun multiply(): Any = left.multiply(right)
}
