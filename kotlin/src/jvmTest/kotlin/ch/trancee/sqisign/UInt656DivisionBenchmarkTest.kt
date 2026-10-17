// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.UInt656
import ch.trancee.sqisign.benchmarks.UInt656DivisionBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UInt656DivisionBenchmarkTest {
    @Test
    fun wideDivisionWorkloadsMatchIndependentArithmetic() {
        val one = BigInteger.ONE
        val narrowMaximum = one.shiftLeft(328) - one
        val wideMaximum = one.shiftLeft(656) - one
        val fixtures =
            listOf(
                Triple("ZERO_DIVIDEND", BigInteger.ZERO, one),
                Triple("MAX_BY_ONE", wideMaximum, one),
                Triple("MAX_BY_MAX", wideMaximum, narrowMaximum),
                Triple("SMALL_DIVISOR", wideMaximum, BigInteger.valueOf(7)),
                Triple("HIGH_BIT_DIVISOR", wideMaximum, one.shiftLeft(327) + one),
                Triple("LARGER_DIVISOR", one, narrowMaximum),
                Triple("PRODUCT_BY_FACTOR", narrowMaximum.pow(2), narrowMaximum),
            )
        for ((shape, dividend, divisor) in fixtures) {
            val benchmark = UInt656DivisionBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val expected = dividend.divideAndRemainder(divisor)

            val output = benchmark.divideAndRemainder()

            val division = assertIs<UInt656.Division>(output)
            assertEquals(expected[0], division.quotient.toLittleEndian().unsignedInteger(), shape)
            assertEquals(expected[1], division.remainder.toLittleEndian().unsignedInteger(), shape)
        }
    }

    @Test
    fun unknownWideDivisionShapesAreRejected() {
        val benchmark = UInt656DivisionBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public wide division operand shape: UNKNOWN", error.message)
    }
}

private fun ByteArray.unsignedInteger(): BigInteger = BigInteger(1, reversedArray())
