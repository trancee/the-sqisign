// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.Int328
import ch.trancee.sqisign.benchmarks.Int328Benchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class Int328BenchmarkTest {
    @Test
    fun decodeWorkloadsMatchIndependentArithmetic() {
        for ((benchmark, left) in workloads()) {
            val output = benchmark.decode()

            val actual = assertIs<Int328>(output)
            assertEquals(
                left,
                BigInteger(actual.toLittleEndian().reversedArray()),
                benchmark.operandShape,
            )
        }
    }

    @Test
    fun encodeWorkloadsMatchIndependentArithmetic() {
        for ((benchmark, left) in workloads()) {
            val expectedBytes = ByteArray(Int328.ENCODED_BYTES) { if (left.signum() < 0) -1 else 0 }
            left.toByteArray().reversedArray().copyInto(expectedBytes)

            val encoded = benchmark.encode()

            assertContentEquals(expectedBytes, encoded, benchmark.operandShape)
        }
    }

    @Test
    fun compareWorkloadsMatchIndependentArithmetic() {
        for ((benchmark, left, right) in workloads()) {
            val comparison = benchmark.compare()

            assertEquals(left.compareTo(right), comparison, benchmark.operandShape)
        }
    }

    @Test
    fun additionWorkloadsMatchIndependentArithmetic() {
        for ((benchmark, left, right) in workloads()) {
            val output = benchmark.add()

            val actual = assertIs<Int328>(output)
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

            val actual = assertIs<Int328>(output)
            assertEquals(
                left - right,
                BigInteger(actual.toLittleEndian().reversedArray()),
                benchmark.operandShape,
            )
        }
    }

    private fun workloads(): List<Triple<Int328Benchmark, BigInteger, BigInteger>> {
        val zero = BigInteger.ZERO
        val one = BigInteger.ONE
        val minimum = one.shiftLeft(327).negate()
        return listOf(
                Triple("ZERO_EQUAL", zero, zero),
                Triple("MIN_BY_ZERO", minimum, zero),
                Triple("MAX_BY_ZERO", minimum.negate() - one, zero),
                Triple("NEGATIVE_EQUAL", minimum.shiftRight(1), minimum.shiftRight(1)),
                Triple("MIXED_SIGN", one.shiftLeft(325), one.shiftLeft(325).negate()),
                Triple("CARRY_CHAIN", one.shiftLeft(315) - one, one),
            )
            .map { (shape, left, right) ->
                val benchmark = Int328Benchmark()
                benchmark.operandShape = shape
                benchmark.prepare()
                Triple(benchmark, left, right)
            }
    }

    @Test
    fun unknownSignedShapesAreRejected() {
        val benchmark = Int328Benchmark()
        benchmark.operandShape = "UNKNOWN"

        val error = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public signed operand shape: UNKNOWN", error.message)
    }
}
