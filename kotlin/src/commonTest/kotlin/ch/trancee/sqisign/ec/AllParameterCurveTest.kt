// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp2P500
import ch.trancee.sqisign.field.Fp2P664
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AllParameterCurveTest {
    @Test
    fun levelThreeCurveDoublingUsesTheSameProjectiveGroupLaw() {
        val field = Fp2P500
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())
        val point = MontgomeryPoint.fromCoordinates(field, element(2), field.one())
        val expected = MontgomeryPoint.fromCoordinates(field, element(9), element(40))

        val actual = curve.double(point)

        assertTrue(actual.isEqualTo(expected), "A=0 doubles (2:1) to (9:40)")
    }

    @Test fun levelOneScalarWidthsFailExplicitly() = assertScalarWidths(Fp2P324)

    @Test fun levelThreeScalarWidthsFailExplicitly() = assertScalarWidths(Fp2P500)

    @Test fun levelFiveScalarWidthsFailExplicitly() = assertScalarWidths(Fp2P664)

    private fun <Element : QuadraticFieldElement<Element>> assertScalarWidths(
        field: QuadraticField<Element>,
    ) {
        val width = field.parameters.fieldBytes
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())
        val point = MontgomeryPoint.fromCoordinates(field, field.one(), field.one())
        val valid = ByteArray(width)
        for (invalidWidth in listOf(0, width - 1, width + 1)) {
            val invalid = ByteArray(invalidWidth)
            val operations =
                listOf(
                    { curve.multiply(point, invalid) },
                    { curve.threePointMultiply(point, point, point, invalid) },
                    { curve.biscalarMultiply(point, point, point, invalid, valid) },
                    { curve.biscalarMultiply(point, point, point, valid, invalid) },
                )
            for (operation in operations) {
                val failure = assertFailsWith<IllegalArgumentException> { operation() }

                assertEquals("Montgomery scalars require exactly $width bytes", failure.message)
            }
        }
    }

    private fun element(value: Int): Fp2P500 =
        Fp2P500.fromLittleEndian(ByteArray(128).also { it[0] = value.toByte() })
}
