// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Int656
import ch.trancee.sqisign.benchmarks.Int328MultiplyBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class Int328MultiplyBenchmarkTest {
    @Test
    fun fullSignedProductWorkloadsMatchIndependentArithmetic() {
        val zero = BigInteger.ZERO
        val one = BigInteger.ONE
        val minimum = one.shiftLeft(327).negate()
        val maximum = minimum.negate() - one
        val fixtures =
            listOf(
                Triple("ZERO_NEGATIVE", zero, minimum),
                Triple("MIN_SQUARE", minimum, minimum),
                Triple("MIN_NEGATIVE_ONE", minimum, one.negate()),
                Triple("MIN_MAX", minimum, maximum),
                Triple("MAX_SQUARE", maximum, maximum),
                Triple(
                    "FULL_WIDTH",
                    BigInteger(ByteArray(41) { 0x55 }),
                    BigInteger(ByteArray(41) { 0xaa.toByte() }),
                ),
            )
        for ((shape, left, right) in fixtures) {
            val benchmark = Int328MultiplyBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val expected = left * right

            val output = benchmark.multiply()

            val product = assertIs<Int656>(output)
            assertEquals(expected, BigInteger(product.toLittleEndian().reversedArray()), shape)
        }
    }

    @Test
    fun unknownSignedMultiplicationShapesAreRejected() {
        val benchmark = Int328MultiplyBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public signed multiplication operand shape: UNKNOWN", error.message)
    }
}
