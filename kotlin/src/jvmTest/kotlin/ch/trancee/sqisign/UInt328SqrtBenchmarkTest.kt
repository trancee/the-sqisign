// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.UInt328
import ch.trancee.sqisign.benchmarks.UInt328SqrtBenchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UInt328SqrtBenchmarkTest {
    @Test
    fun squareRootWorkloadsMatchIndependentArithmetic() {
        val maximum = BigInteger.ONE.shiftLeft(328) - BigInteger.ONE
        val root = BigInteger.ONE.shiftLeft(164) - BigInteger.ONE
        val square = root.multiply(root)
        val fixtures =
            listOf(
                "ZERO" to BigInteger.ZERO,
                "ONE" to BigInteger.ONE,
                "MAXIMUM" to maximum,
                "PERFECT_SQUARE" to square,
                "BELOW_SQUARE" to square - BigInteger.ONE,
                "ABOVE_SQUARE" to square + BigInteger.ONE,
                "FULL_WIDTH" to BigInteger("55".repeat(41), 16),
            )
        for ((shape, expectedInput) in fixtures) {
            val benchmark = UInt328SqrtBenchmark()
            benchmark.operandShape = shape
            benchmark.prepare()
            val expected = expectedInput.sqrt()

            val output = benchmark.sqrtFloor()

            val rootValue = assertIs<UInt328>(output)
            assertEquals(
                expected,
                BigInteger(1, rootValue.toLittleEndian().reversedArray()),
                shape,
            )
        }
    }

    @Test
    fun unknownSquareRootShapesAreRejected() {
        val benchmark = UInt328SqrtBenchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public square-root operand shape: UNKNOWN", error.message)
    }
}
