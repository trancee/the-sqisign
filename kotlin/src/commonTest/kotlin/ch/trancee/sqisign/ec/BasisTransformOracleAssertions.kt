// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class BasisTransformOracleAssertions<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    curves: List<String>,
    records: List<String>,
) {
    private val cases = records.map { it.split(':') }
    private val domains = curves.map { lazy { BasisTransformDomain(field, it) } }

    internal fun verifyAction(index: Int) {
        val record = cases[index]
        val exponent = record[1].toInt()
        val domain = domains[record[0].toInt()].value
        val source = domain.source(exponent)
        val entries =
            record.subList(3, 7).map {
                TorsionScalar.fromLittleEndian(exponent, it.hexToByteArray())
            }
        val matrix = TorsionMatrix.of(entries[0], entries[1], entries[2], entries[3])

        val actual = source.applyMatrix(domain.curve, matrix)

        assertEquals(exponent, actual.exponent, context(record))
        val actualPoints = listOf(actual.first, actual.second, actual.difference)
        record.subList(7, 10).zip(actualPoints).forEach { (encoded, point) ->
            val expected = field.fromLittleEndian(encoded.hexToByteArray())
            assertTrue(point.x.isEqualTo(expected.multiply(point.z)), context(record))
            assertTrue(!point.isInfinity(), context(record))
        }
        actual.requireCurve(domain.curve)
    }

    internal fun verifyChange(index: Int) {
        val record = cases[index]
        val exponent = record[1].toInt()
        val domain = domains[record[0].toInt()].value
        val points =
            record.subList(7, 10).mapIndexed { position, encoded ->
                scaledPoint(field, encoded, position + 2)
            }
        val target =
            TorsionBasis.checked(
                domain.curve,
                exponent,
                points[0],
                points[1],
                points[2],
            )
        val prepared = domain.change

        val actual = prepared.between(target)

        assertEquals(exponent, actual.exponent, context(record))
        assertEquals(
            record.subList(10, 14),
            actual.rowMajor().map { it.toLittleEndian().toHexString() },
            context(record),
        )
    }

    private fun context(record: List<String>): String =
        "${field.parameters}: curve ${record[0]}, exponent ${record[1]}, ${record[2]}"
}

private class BasisTransformDomain<Element : QuadraticFieldElement<Element>>(
    field: QuadraticField<Element>,
    encoded: String,
) {
    private val inputs = encoded.split(':')
    private val coefficient = field.fromLittleEndian(inputs[0].hexToByteArray())
    private val scale = smallElement(field, 7)
    val curve = MontgomeryCurve.fromCoefficients(field, coefficient.multiply(scale), scale)
    private val fullPoints =
        inputs.drop(1).mapIndexed { index, value ->
            scaledPoint(field, value, index + 2)
        }
    private val bases =
        listOf(
                1,
                8,
                field.parameters.torsionExponent - 1,
                field.parameters.torsionExponent,
            )
            .associateWith { exponent ->
                lazy {
                    val points = fullPoints.map {
                        repeatedDouble(curve, it, field.parameters.torsionExponent - exponent)
                    }
                    TorsionBasis.checked(curve, exponent, points[0], points[1], points[2])
                }
            }
    val change by lazy { BasisChange.prepare(curve, source(field.parameters.torsionExponent)) }

    fun source(exponent: Int): TorsionBasis<Element> = bases.getValue(exponent).value
}

private fun <Element : QuadraticFieldElement<Element>> scaledPoint(
    field: QuadraticField<Element>,
    encoded: String,
    scale: Int,
): MontgomeryPoint<Element> =
    MontgomeryPoint.fromCoordinates(
        field,
        field.fromLittleEndian(encoded.hexToByteArray()).multiply(smallElement(field, scale)),
        smallElement(field, scale),
    )

private fun <Element : QuadraticFieldElement<Element>> smallElement(
    field: QuadraticField<Element>,
    value: Int,
): Element =
    field.fromLittleEndian(
        ByteArray(field.parameters.extensionFieldBytes).also { it[0] = value.toByte() },
    )
