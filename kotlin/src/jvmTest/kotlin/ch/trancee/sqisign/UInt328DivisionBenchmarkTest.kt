// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.UInt328
import ch.trancee.sqisign.benchmarks.UInt328DivisionBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UInt328DivisionBenchmarkTest {
    @Test
    fun divisionWorkloadsMatchIndependentQuotientAndRemainder() {
        val zero = "00".repeat(41)
        val one = "01" + "00".repeat(40)
        val seven = "07" + "00".repeat(40)
        val maximum = "ff".repeat(41)
        val low = "55".repeat(41)
        val high = "aa".repeat(41)
        val fixtures =
            listOf(
                Triple("ZERO_DIVIDEND", zero, one),
                Triple("MAX_BY_ONE", maximum, one),
                Triple("MAX_EQUAL", maximum, maximum),
                Triple("SMALL_DIVISOR", maximum, seven),
                Triple("FULL_WIDTH", high, low),
                Triple("LARGER_DIVISOR", low, high),
            )
        for ((shape, dividendHex, divisorHex) in fixtures) {
            val benchmark = UInt328DivisionBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val dividend = dividendHex.hexToByteArray().unsignedInteger()
            val divisor = divisorHex.hexToByteArray().unsignedInteger()
            val expected = dividend.divideAndRemainder(divisor)

            val output = benchmark.divideAndRemainder()

            val division = assertIs<UInt328.Division>(output)
            assertEquals(expected[0], division.quotient.toLittleEndian().unsignedInteger(), shape)
            assertEquals(expected[1], division.remainder.toLittleEndian().unsignedInteger(), shape)
        }
    }

    @Test
    fun unknownDivisionShapesAreRejected() {
        val benchmark = UInt328DivisionBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public division operand shape: UNKNOWN", error.message)
    }
}

private fun ByteArray.unsignedInteger(): BigInteger = BigInteger(1, reversedArray())
