// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.TorsionRingBenchmark
import ch.trancee.sqisign.ec.TorsionMatrix
import ch.trancee.sqisign.ec.TorsionScalar
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TorsionRingBenchmarkTest {
    @Test
    fun additionRetainsItsModularCarry() {
        forEachWorkload { benchmark, modulus ->
            val result = assertIs<TorsionScalar>(benchmark.add())

            assertScalar(BigInteger.TWO.mod(modulus), result, modulus)
        }
    }

    @Test
    fun subtractionRetainsItsModularDifference() {
        forEachWorkload { benchmark, modulus ->
            val result = assertIs<TorsionScalar>(benchmark.subtract())

            assertScalar(modulus.subtract(BigInteger.valueOf(4)).mod(modulus), result, modulus)
        }
    }

    @Test
    fun inversionRetainsItsFullWidthUnit() {
        forEachWorkload { benchmark, modulus ->
            val result = assertIs<TorsionScalar>(benchmark.inverse())

            assertScalar(modulus.subtract(BigInteger.ONE), result, modulus)
        }
    }

    @Test
    fun multiplicationRetainsItsFullWidthModularResult() {
        forEachWorkload { benchmark, modulus ->
            val result = assertIs<TorsionScalar>(benchmark.multiply())

            assertScalar(modulus.subtract(BigInteger.valueOf(3)).mod(modulus), result, modulus)
        }
    }

    @Test
    fun matrixNormalizationRetainsTheSpecifiedGlobalSign() {
        forEachWorkload { benchmark, modulus ->
            val expected = listOf(3L, -4L, -2L, 3L).map { BigInteger.valueOf(it).mod(modulus) }

            val result = assertIs<TorsionMatrix>(benchmark.normalizeMatrix())

            assertEquals(4, result.rowMajor().size)
            for ((entry, value) in result.rowMajor().zip(expected)) {
                assertScalar(value, entry, modulus)
            }
        }
    }

    @Test
    fun matrixInversionRetainsAllFourCanonicalEntries() {
        forEachWorkload { benchmark, modulus ->
            val expected = listOf(-3L, -4L, -2L, -3L).map { BigInteger.valueOf(it).mod(modulus) }

            val result = assertIs<TorsionMatrix>(benchmark.invertMatrix())

            assertEquals(4, result.rowMajor().size)
            for ((entry, value) in result.rowMajor().zip(expected)) {
                assertScalar(value, entry, modulus)
            }
        }
    }

    @Test
    fun unknownTorsionShapesFailBeforeTiming() {
        val benchmark = TorsionRingBenchmark().also { it.torsionShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public torsion shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = TorsionRingBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }

    private fun forEachWorkload(assertion: (TorsionRingBenchmark, BigInteger) -> Unit) {
        for (parameter in ParameterSet.entries) {
            for (shape in listOf("TWO_TORSION", "FULL_TORSION")) {
                val benchmark =
                    TorsionRingBenchmark().also {
                        it.parameterSet = parameter.name
                        it.torsionShape = shape
                        it.prepare()
                    }
                val exponent = if (shape == "TWO_TORSION") 1 else parameter.torsionExponent
                assertion(benchmark, BigInteger.ONE.shiftLeft(exponent))
            }
        }
    }

    private fun assertScalar(expected: BigInteger, actual: TorsionScalar, modulus: BigInteger) {
        assertEquals(modulus.bitLength() - 1, actual.exponent)
        val encoded = expected.toByteArray().reversedArray()
        assertContentEquals(
            encoded.copyOf((actual.exponent + 7) / Byte.SIZE_BITS),
            actual.toLittleEndian(),
        )
    }
}
