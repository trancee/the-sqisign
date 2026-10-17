// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Int328
import ch.trancee.sqisign.benchmarks.Int328ExtendedGcdBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class Int328ExtendedGcdBenchmarkTest {
    @Test
    fun signedExtendedGcdWorkloadsMatchIndependentArithmetic() {
        val minimum = BigInteger.ONE.shiftLeft(327).negate()
        val maximum = minimum.negate() - BigInteger.ONE
        val (fibonacciCurrent, fibonacciPrevious) = fibonacciPair(maximum)
        val fixtures =
            listOf(
                Triple("ZERO_EQUAL", BigInteger.ZERO, BigInteger.ZERO),
                Triple("ZERO_NEGATIVE", BigInteger.ZERO, minimum),
                Triple("MIN_ZERO", minimum, BigInteger.ZERO),
                Triple("MIN_ONE", minimum, BigInteger.ONE),
                Triple("MIN_MAX", minimum, maximum),
                Triple(
                    "FULL_WIDTH",
                    BigInteger(ByteArray(41) { 0xaa.toByte() }),
                    BigInteger(ByteArray(41) { 0x55 }),
                ),
                Triple("FIBONACCI", fibonacciCurrent, fibonacciPrevious),
            )
        for ((shape, expectedLeft, expectedRight) in fixtures) {
            val benchmark = Int328ExtendedGcdBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val output = assertIs<Int328.ExtendedGcdResult>(benchmark.extendedGcd())
            val gcd = BigInteger(1, output.gcd.toLittleEndian().reversedArray())
            val leftCoefficient =
                BigInteger(output.leftCoefficient.toLittleEndian().reversedArray())
            val rightCoefficient =
                BigInteger(output.rightCoefficient.toLittleEndian().reversedArray())

            assertEquals(expectedLeft.gcd(expectedRight), gcd, "GCD $shape")
            assertEquals(
                expectedLeft * leftCoefficient + expectedRight * rightCoefficient,
                gcd,
                "Bezout identity $shape",
            )
        }
    }

    @Test
    fun unknownSignedExtendedGcdShapesAreRejected() {
        val benchmark = Int328ExtendedGcdBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals(
            "Unknown public signed extended-GCD operand shape: UNKNOWN",
            error.message,
        )
    }
}

private fun fibonacciPair(maximum: BigInteger): Pair<BigInteger, BigInteger> {
    var previous = BigInteger.ZERO
    var current = BigInteger.ONE
    while (previous + current <= maximum) {
        val next = previous + current
        previous = current
        current = next
    }
    return current to previous
}
