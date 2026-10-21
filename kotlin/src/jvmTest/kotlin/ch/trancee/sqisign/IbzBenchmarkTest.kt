// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzDivision
import ch.trancee.sqisign.arithmetic.toString
import ch.trancee.sqisign.benchmarks.IbzBenchmark
import ch.trancee.sqisign.benchmarks.ibzBenchmarkCapacity
import ch.trancee.sqisign.benchmarks.ibzBenchmarkOperand
import ch.trancee.sqisign.benchmarks.ibzBenchmarkOperandBits
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class IbzBenchmarkTest {
    @Test
    fun workloadsMatchIndependentBigIntegers() {
        for (parameterSet in listOf("NIST_I", "NIST_III", "NIST_V")) {
            for (shape in listOf("WORD", "HALF_CAPACITY")) {
                val benchmark = IbzBenchmark()
                benchmark.parameterSet = parameterSet
                benchmark.operandShape = shape
                benchmark.prepare()
                val bits = ibzBenchmarkOperandBits(shape, ibzBenchmarkCapacity(parameterSet))
                val left = BigInteger(ibzBenchmarkOperand(bits, negative = true), 16)
                val right = BigInteger(ibzBenchmarkOperand(bits / 2, negative = false), 16)
                val (quotient, remainder) = left.divideAndRemainder(right)

                val sum = assertIs<Ibz>(benchmark.add())
                val product = assertIs<Ibz>(benchmark.multiply())
                val division = assertIs<IbzDivision>(benchmark.divideAndRemainder())

                val label = "$parameterSet $shape"
                assertEquals(bits, left.abs().bitLength(), label)
                assertEquals(left + right, BigInteger(sum.toString(16), 16), label)
                assertEquals(left * right, BigInteger(product.toString(16), 16), label)
                assertEquals(quotient, BigInteger(division.quotient.toString(16), 16), label)
                assertEquals(remainder, BigInteger(division.remainder.toString(16), 16), label)
            }
        }
    }

    @Test
    fun unknownParametersAreRejected() {
        val benchmark = IbzBenchmark()
        benchmark.parameterSet = "UNKNOWN"

        val parameterError = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }
        val shapeError =
            assertFailsWith<IllegalArgumentException> { ibzBenchmarkOperandBits("UNKNOWN", 1920) }

        assertEquals("Unknown parameter set: UNKNOWN", parameterError.message)
        assertEquals("Unknown ibz operand shape: UNKNOWN", shapeError.message)
    }
}
