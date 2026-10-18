// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.benchmarks.BasisTransformBenchmark
import ch.trancee.sqisign.ec.BasisChangeVectors
import ch.trancee.sqisign.ec.TorsionBasis
import ch.trancee.sqisign.ec.TorsionMatrix
import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp2P500
import ch.trancee.sqisign.field.Fp2P664
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BasisTransformBenchmarkTest {
    @Test
    fun unknownShapesFailBeforeTiming() {
        val benchmark = BasisTransformBenchmark().also { it.torsionShape = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertEquals("Unknown public torsion shape: UNKNOWN", failure.message)
    }

    @Test
    fun unknownParametersFailBeforeTiming() {
        val benchmark = BasisTransformBenchmark().also { it.parameterSet = "UNKNOWN" }

        val failure = assertFailsWith<IllegalArgumentException> { benchmark.prepare() }

        assertTrue(assertNotNull(failure.message).contains("UNKNOWN"))
    }

    @Test
    fun freshChangeRetainsTheIndependentNormalizedMatrix() {
        forEachWorkload { benchmark, _, record ->
            val actual = assertIs<TorsionMatrix>(benchmark.prepareAndBetween())

            assertEquals(record[1].toInt(), actual.exponent)
            assertEquals(
                record.subList(10, 14),
                actual.rowMajor().map { it.toLittleEndian().toHexString() },
            )
        }
    }

    @Test
    fun preparedChangeRetainsTheIndependentNormalizedMatrix() {
        forEachWorkload { benchmark, _, record ->
            val actual = assertIs<TorsionMatrix>(benchmark.between())

            assertEquals(record[1].toInt(), actual.exponent)
            assertEquals(
                record.subList(10, 14),
                actual.rowMajor().map { it.toLittleEndian().toHexString() },
            )
        }
    }

    @Test
    fun actionRetainsTheIndependentAffineCoordinates() {
        forEachWorkload { benchmark, parameter, record ->
            val actual = assertIs<TorsionBasis<*>>(benchmark.applyMatrix())

            assertEquals(record[1].toInt(), actual.exponent)
            val expected = record.subList(7, 10)
            when (parameter) {
                ParameterSet.NIST_I -> assertCoordinates(Fp2P324, expected, actual)
                ParameterSet.NIST_III -> assertCoordinates(Fp2P500, expected, actual)
                ParameterSet.NIST_V -> assertCoordinates(Fp2P664, expected, actual)
            }
        }
    }

    private fun forEachWorkload(
        assertion: (BasisTransformBenchmark, ParameterSet, List<String>) -> Unit,
    ) {
        for (parameter in ParameterSet.entries) {
            val records =
                when (parameter) {
                    ParameterSet.NIST_I -> BasisChangeVectors.p324Records
                    ParameterSet.NIST_III -> BasisChangeVectors.p500Records
                    ParameterSet.NIST_V -> BasisChangeVectors.p664Records
                }
            for (shape in listOf("TWO_TORSION", "FULL_TORSION")) {
                val exponent = if (shape == "TWO_TORSION") 1 else parameter.torsionExponent
                val record =
                    records
                        .map { it.split(':') }
                        .single {
                            it[0] == "0" &&
                                it[1].toInt() == exponent &&
                                if (exponent == 1) {
                                    it.subList(3, 7) == listOf("00", "01", "01", "00")
                                } else {
                                    it[2] == "dense"
                                }
                        }
                val benchmark =
                    BasisTransformBenchmark().also {
                        it.parameterSet = parameter.name
                        it.torsionShape = shape
                        it.prepare()
                    }
                assertion(benchmark, parameter, record)
            }
        }
    }

    private fun <Element : QuadraticFieldElement<Element>> assertCoordinates(
        field: QuadraticField<Element>,
        expected: List<String>,
        actual: TorsionBasis<*>,
    ) {
        expected.zip(listOf(actual.first, actual.second, actual.difference)).forEach {
            (encoded, point) ->
            val coordinate = field.fromLittleEndian(encoded.hexToByteArray())
            val actualX = field.fromLittleEndian(point.x.toLittleEndian())
            val actualZ = field.fromLittleEndian(point.z.toLittleEndian())
            assertTrue(actualX.isEqualTo(coordinate.multiply(actualZ)), field.parameters.name)
            assertTrue(!point.isInfinity(), field.parameters.name)
        }
    }
}
