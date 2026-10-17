// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Int656
import ch.trancee.sqisign.benchmarks.Int656Benchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class Int656BenchmarkTest {
    @Test
    fun comparisonWorkloadsMatchIndependentArithmetic() {
        for ((benchmark, left, right) in workloads()) {
            val comparison = benchmark.compare()

            assertEquals(left.compareTo(right), comparison, benchmark.operandShape)
        }
    }

    @Test
    fun additionWorkloadsMatchIndependentArithmetic() {
        for ((benchmark, left, right) in workloads()) {
            val output = benchmark.add()

            val actual = assertIs<Int656>(output)
            assertEquals(
                left + right,
                BigInteger(actual.toLittleEndian().reversedArray()),
                benchmark.operandShape,
            )
        }
    }

    @Test
    fun subtractionWorkloadsMatchIndependentArithmetic() {
        for ((benchmark, left, right) in workloads()) {
            val output = benchmark.subtract()

            val actual = assertIs<Int656>(output)
            assertEquals(
                left - right,
                BigInteger(actual.toLittleEndian().reversedArray()),
                benchmark.operandShape,
            )
        }
    }

    @Test
    fun unknownWideSignedShapesAreRejected() {
        val benchmark = Int656Benchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public wide signed operand shape: UNKNOWN", error.message)
    }

    private fun workloads(): List<Triple<Int656Benchmark, BigInteger, BigInteger>> {
        val zero = BigInteger.ZERO
        val one = BigInteger.ONE
        val minimum = one.shiftLeft(655).negate()
        val maximum = one.shiftLeft(655) - one
        val negativePower = one.shiftLeft(654).negate()
        val positivePower = one.shiftLeft(653)
        val carryChain = one.shiftLeft(645) - one
        return listOf(
                Triple("ZERO_EQUAL", zero, zero),
                Triple("MIN_BY_ZERO", minimum, zero),
                Triple("MAX_BY_ZERO", maximum, zero),
                Triple("NEGATIVE_EQUAL", negativePower, negativePower),
                Triple("MIXED_SIGN", positivePower, positivePower.negate()),
                Triple("CARRY_CHAIN", carryChain, one),
            )
            .map { (shape, left, right) ->
                val benchmark = Int656Benchmark()
                benchmark.operandShape = shape
                benchmark.prepare()
                Triple(benchmark, left, right)
            }
    }
}
