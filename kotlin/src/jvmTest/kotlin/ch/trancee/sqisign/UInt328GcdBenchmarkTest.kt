// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.UInt328
import ch.trancee.sqisign.benchmarks.UInt328GcdBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UInt328GcdBenchmarkTest {
    @Test
    fun gcdWorkloadsMatchIndependentArithmetic() {
        val zero = BigInteger.ZERO
        val one = BigInteger.ONE
        val modulus = one.shiftLeft(328)
        val maximum = modulus - one
        var previous = zero
        var current = one
        while (previous + current < modulus) {
            val next = previous + current
            previous = current
            current = next
        }
        val fixtures =
            listOf(
                Triple("ZERO_EQUAL", zero, zero),
                Triple("ZERO_MAX", zero, maximum),
                Triple("MAX_ZERO", maximum, zero),
                Triple("MAX_EQUAL", maximum, maximum),
                Triple(
                    "SHARED_FACTOR",
                    BigInteger.valueOf(3).shiftLeft(320),
                    BigInteger.valueOf(5).shiftLeft(320),
                ),
                Triple("COPRIME", maximum, maximum - one),
                Triple("FIBONACCI", current, previous),
            )
        for ((shape, left, right) in fixtures) {
            val benchmark = UInt328GcdBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val expected = left.gcd(right)

            val output = benchmark.gcd()

            val gcd = assertIs<UInt328>(output)
            assertEquals(expected, BigInteger(1, gcd.toLittleEndian().reversedArray()), shape)
        }
    }

    @Test
    fun unknownGcdShapesAreRejected() {
        val benchmark = UInt328GcdBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public GCD operand shape: UNKNOWN", error.message)
    }
}
