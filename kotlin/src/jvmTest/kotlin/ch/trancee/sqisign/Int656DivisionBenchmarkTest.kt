// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Int656
import ch.trancee.sqisign.benchmarks.Int656DivisionBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Int656DivisionBenchmarkTest {
    @Test
    fun signedWideDivisionWorkloadsMatchIndependentArithmetic() {
        val one = BigInteger.ONE
        val zero = BigInteger.ZERO
        val wideMinimum = one.shiftLeft(655).negate()
        val wideMaximum = one.shiftLeft(655) - one
        val narrowMinimum = one.shiftLeft(327).negate()
        val narrowMaximum = one.shiftLeft(327) - one
        val fixtures =
            listOf(
                Triple("ZERO_NEGATIVE", zero, one.negate()),
                Triple("MIN_BY_ONE", wideMinimum, one),
                Triple("MIN_BY_NEGATIVE_MAX", wideMinimum, narrowMaximum.negate()),
                Triple("MAX_BY_NEGATIVE_ONE", wideMaximum, one.negate()),
                Triple(
                    "NEGATIVE_BY_POSITIVE",
                    BigInteger(ByteArray(82) { 0xaa.toByte() }),
                    narrowMaximum,
                ),
                Triple(
                    "POSITIVE_BY_NEGATIVE",
                    BigInteger(ByteArray(82) { 0x55 }),
                    narrowMaximum.negate(),
                ),
                Triple(
                    "FULL_WIDTH",
                    BigInteger(ByteArray(82) { 0xaa.toByte() }),
                    BigInteger(ByteArray(41) { 0x55 }),
                ),
                Triple("LARGER_DIVISOR", BigInteger.valueOf(7), narrowMinimum),
            )
        for ((shape, dividend, divisor) in fixtures) {
            val benchmark = Int656DivisionBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val expected = dividend.divideAndRemainder(divisor)

            val output = benchmark.divideAndRemainder()

            val division = assertIs<Int656.Division>(output)
            val quotient = BigInteger(division.quotient.toLittleEndian().reversedArray())
            val remainder = BigInteger(division.remainder.toLittleEndian().reversedArray())
            assertEquals(expected[0], quotient, "Quotient $shape")
            assertEquals(expected[1], remainder, "Remainder $shape")
            assertEquals(dividend, quotient * divisor + remainder, "Identity $shape")
            assertTrue(remainder.abs() < divisor.abs(), "Remainder bound $shape")
        }
    }

    @Test
    fun unknownSignedWideDivisionShapesAreRejected() {
        val benchmark = Int656DivisionBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public signed wide division operand shape: UNKNOWN", error.message)
    }
}
