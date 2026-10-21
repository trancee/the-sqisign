// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.arithmetic.UInt328
import ch.trancee.sqisign.arithmetic.UInt656
import ch.trancee.sqisign.benchmarks.UInt328Benchmark
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class UInt328BenchmarkTest {
    @Test
    fun decodingWorkloadsRetainAllInputBits() {
        for (fixture in fixtures) {
            val benchmark = fixture.prepare()

            val output = benchmark.decode()

            assertContentEquals(
                fixture.left,
                assertIs<UInt328>(output).toLittleEndian(),
                fixture.shape,
            )
        }
    }

    @Test
    fun encodingWorkloadsReturnTheSpecifiedBytes() {
        for (fixture in fixtures) {
            val benchmark = fixture.prepare()

            val output = benchmark.encode()

            assertContentEquals(fixture.left, output, fixture.shape)
        }
    }

    @Test
    fun comparisonWorkloadsMatchIndependentUnsignedOrdering() {
        for (fixture in fixtures) {
            val benchmark = fixture.prepare()
            val expected = fixture.leftInteger.compareTo(fixture.rightInteger)

            val output = benchmark.compare()

            assertEquals(expected, output, fixture.shape)
        }
    }

    @Test
    fun additionWorkloadsRetainTheLowValueAndCarry() {
        for (fixture in fixtures) {
            val benchmark = fixture.prepare()
            val expected = fixture.leftInteger + fixture.rightInteger

            val output = benchmark.add()

            val addition = assertIs<UInt328.Addition>(output)
            assertEquals(expected.mod(modulus), addition.value.toLittleEndian().unsignedInteger())
            assertEquals(expected.shiftRight(328).toInt(), addition.carry, fixture.shape)
        }
    }

    @Test
    fun subtractionWorkloadsRetainTheLowValueAndBorrow() {
        for (fixture in fixtures) {
            val benchmark = fixture.prepare()
            val expected = fixture.leftInteger - fixture.rightInteger

            val output = benchmark.subtract()

            val subtraction = assertIs<UInt328.Subtraction>(output)
            assertEquals(
                expected.mod(modulus),
                subtraction.value.toLittleEndian().unsignedInteger(),
            )
            assertEquals(if (expected.signum() < 0) 1 else 0, subtraction.borrow, fixture.shape)
        }
    }

    @Test
    fun multiplicationWorkloadsRetainTheEntireProduct() {
        for (fixture in fixtures) {
            val benchmark = fixture.prepare()
            val expected = fixture.leftInteger * fixture.rightInteger

            val output = benchmark.multiply()

            val product = assertIs<UInt656>(output)
            assertEquals(
                expected,
                product.toLittleEndian().unsignedInteger(),
                fixture.shape,
            )
        }
    }

    @Test
    fun unknownOperandShapesAreRejected() {
        val benchmark = UInt328Benchmark()
        benchmark.operandShape = "UNKNOWN"

        assertFailsWith<IllegalArgumentException> { benchmark.prepare() }
    }
}

private class UnsignedBenchmarkFixture(
    val shape: String,
    leftHex: String,
    rightHex: String,
) {
    val left = leftHex.hexToByteArray()
    val right = rightHex.hexToByteArray()
    val leftInteger = left.unsignedInteger()
    val rightInteger = right.unsignedInteger()

    fun prepare(): UInt328Benchmark =
        UInt328Benchmark().also {
            it.operandShape = shape
            it.prepare()
        }
}

private fun ByteArray.unsignedInteger(): BigInteger = BigInteger(1, reversedArray())

private val modulus = BigInteger.ONE.shiftLeft(328)
private val zeroHex = "00".repeat(41)
private val oneHex = "01" + "00".repeat(40)
private val maximumHex = "ff".repeat(41)
private val fixtures =
    listOf(
        UnsignedBenchmarkFixture("ZERO_EQUAL", zeroHex, zeroHex),
        UnsignedBenchmarkFixture("MAX_EQUAL", maximumHex, maximumHex),
        UnsignedBenchmarkFixture("CARRY_CHAIN", maximumHex, oneHex),
        UnsignedBenchmarkFixture("BORROW_CHAIN", zeroHex, oneHex),
        UnsignedBenchmarkFixture("FULL_WIDTH", "55".repeat(41), "aa".repeat(41)),
    )
