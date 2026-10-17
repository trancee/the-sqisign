// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.benchmarks

import ch.trancee.sqisign.arithmetic.UInt328
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

private const val MAXIMUM_ROOT_FULL_BYTES = 20
private const val MAXIMUM_ROOT_TOP_BYTE = 0x0f

/** Variable-time floor square root of synthetic public unsigned operands. */
@State(Scope.Benchmark)
public open class UInt328SqrtBenchmark {
    /** Shapes cover zero, exact and neighboring squares, and dense full-width inputs. */
    @Param("ZERO", "ONE", "MAXIMUM", "PERFECT_SQUARE", "BELOW_SQUARE", "ABOVE_SQUARE", "FULL_WIDTH")
    public var operandShape: String = "ZERO"

    private lateinit var input: UInt328

    /** Constructs each operand before timing. */
    @Setup
    public fun prepare() {
        val zero = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES))
        val one = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 })
        val maximum = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { 0xff.toByte() })
        val square =
            maximumRoot().multiply(maximumRoot()).toLittleEndian().copyOf(UInt328.ENCODED_BYTES)
        val squareInput = UInt328.fromLittleEndian(square)
        input =
            when (operandShape) {
                "ZERO" -> zero
                "ONE" -> one
                "MAXIMUM" -> maximum
                "PERFECT_SQUARE" -> squareInput
                "BELOW_SQUARE" -> squareInput.subtract(one).value
                "ABOVE_SQUARE" -> squareInput.add(one).value
                "FULL_WIDTH" ->
                    UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { 0x55.toByte() })
                else ->
                    throw IllegalArgumentException(
                        "Unknown public square-root operand shape: $operandShape",
                    )
            }
    }

    /** Retains the immutable root result and includes all iteration allocations. */
    @Benchmark public open fun sqrtFloor(): Any = input.sqrtFloor()

    private fun maximumRoot(): UInt328 =
        UInt328.fromLittleEndian(
            ByteArray(UInt328.ENCODED_BYTES).also { bytes ->
                for (index in 0 until MAXIMUM_ROOT_FULL_BYTES) bytes[index] = 0xff.toByte()
                bytes[MAXIMUM_ROOT_FULL_BYTES] = MAXIMUM_ROOT_TOP_BYTE.toByte()
            },
        )
}
