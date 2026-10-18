// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.assertEquals

internal class BasisCorrespondenceOracleAssertions<Element : QuadraticFieldElement<Element>>(
    private val field: QuadraticField<Element>,
    curves: List<String>,
    records: List<String>,
) {
    private val coefficients = curves.map { it.substringBefore(':') }
    private val cases = records.map { it.split(':') }

    internal fun verify(index: Int) {
        val record = cases[index]
        val exponent = record[2].toInt()
        val firstCurve = curve(record[0].toInt(), 7)
        val secondCurve = curve(record[1].toInt(), 11)
        val first =
            basis(firstCurve, exponent, record.subList(FIRST_POINT_OFFSET, SECOND_POINT_OFFSET))
        val second =
            basis(secondCurve, exponent, record.subList(SECOND_POINT_OFFSET, FIRST_HINT_OFFSET))

        val actual = setChangeOfBasisMatrix(firstCurve, first, secondCurve, second)

        val context = "${field.parameters}, ${record[3]}"
        assertEquals(exponent, actual.matrix.exponent, context)
        assertEquals(
            record.subList(MATRIX_OFFSET, record.size),
            actual.matrix.rowMajor().map { it.toLittleEndian().toHexString() },
            context,
        )
        assertEquals(
            record[FIRST_HINT_OFFSET].toInt(),
            actual.firstHint.encoded.toInt() and 0xff,
            context,
        )
        assertEquals(
            record[FIRST_HINT_OFFSET + 1].toInt(),
            actual.secondHint.encoded.toInt() and 0xff,
            context,
        )
    }

    private fun curve(index: Int, scale: Int): MontgomeryCurve<Element> {
        val factor = small(scale)
        return MontgomeryCurve.fromCoefficients(
            field,
            field.fromLittleEndian(coefficients[index].hexToByteArray()).multiply(factor),
            factor,
        )
    }

    private fun basis(
        curve: MontgomeryCurve<Element>,
        exponent: Int,
        encodings: List<String>,
    ): TorsionBasis<Element> {
        val points = encodings.mapIndexed { index, encoded ->
            val scale = small(index + 2)
            MontgomeryPoint.fromCoordinates(
                field,
                field.fromLittleEndian(encoded.hexToByteArray()).multiply(scale),
                scale,
            )
        }
        return TorsionBasis.checked(curve, exponent, points[0], points[1], points[2])
    }

    private fun small(value: Int): Element =
        field.fromLittleEndian(
            ByteArray(field.parameters.extensionFieldBytes).also { it[0] = value.toByte() },
        )
}

private const val FIRST_POINT_OFFSET = 4
private const val SECOND_POINT_OFFSET = 7
private const val FIRST_HINT_OFFSET = 10
private const val MATRIX_OFFSET = 12
