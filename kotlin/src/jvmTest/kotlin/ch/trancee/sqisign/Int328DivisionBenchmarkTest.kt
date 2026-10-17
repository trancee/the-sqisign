// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Int328
import ch.trancee.sqisign.benchmarks.Int328DivisionBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Int328DivisionBenchmarkTest {
    @Test
    fun signedDivisionWorkloadsMatchIndependentArithmetic() {
        val zero = BigInteger.ZERO
        val one = BigInteger.ONE
        val minimum = one.shiftLeft(327).negate()
        val maximum = minimum.negate() - one
        val seven = BigInteger.valueOf(7)
        val fixtures =
            listOf(
                Triple("ZERO_NEGATIVE", zero, one.negate()),
                Triple("MIN_BY_ONE", minimum, one),
                Triple("MIN_EQUAL", minimum, minimum),
                Triple("NEGATIVE_POSITIVE", minimum, seven),
                Triple("POSITIVE_NEGATIVE", maximum, seven.negate()),
                Triple("BOTH_NEGATIVE", minimum, seven.negate()),
                Triple(
                    "FULL_WIDTH",
                    BigInteger(ByteArray(41) { 0xaa.toByte() }),
                    BigInteger(ByteArray(41) { 0x55 }),
                ),
                Triple("LARGER_DIVISOR", one.negate(), minimum),
            )
        for ((shape, dividend, divisor) in fixtures) {
            val benchmark = Int328DivisionBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val expected = dividend.divideAndRemainder(divisor)

            val output = benchmark.divideAndRemainder()

            val division = assertIs<Int328.Division>(output)
            val quotient = BigInteger(division.quotient.toLittleEndian().reversedArray())
            val remainder = BigInteger(division.remainder.toLittleEndian().reversedArray())
            assertEquals(expected[0], quotient, "Quotient $shape")
            assertEquals(expected[1], remainder, "Remainder $shape")
            assertEquals(dividend, quotient * divisor + remainder, "Identity $shape")
            assertTrue(remainder.abs() < divisor.abs(), "Remainder bound $shape")
        }
    }

    @Test
    fun unknownSignedDivisionShapesAreRejected() {
        val benchmark = Int328DivisionBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public signed division operand shape: UNKNOWN", error.message)
    }
}
