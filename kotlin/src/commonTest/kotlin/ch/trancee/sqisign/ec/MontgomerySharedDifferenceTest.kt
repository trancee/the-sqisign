// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp2P500
import ch.trancee.sqisign.field.Fp2P664
import ch.trancee.sqisign.field.Fp324
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MontgomerySharedDifferenceTest {
    @Test
    fun commonTranslationsRecoverTheActualDifference() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        // On y^2=x^3+x, x(P)=2, x(2P)=9/40 and x(3P)=242/5041.
        val first = point(2, 1)
        val second = point(9, 40)
        val translatedSecond = point(242, 5041)

        val actual = curve.sharedDifference(first, second, second, translatedSecond)

        assertTrue(actual.isEqualTo(first), "P-2P=-P has x-coordinate 2")
    }

    @Test
    fun p324MatchesIndependentAffineTranslations() =
        assertVectors(Fp2P324, sharedDifferenceP324Vectors)

    @Test
    fun p500MatchesIndependentAffineTranslations() =
        assertVectors(Fp2P500, sharedDifferenceP500Vectors)

    @Test
    fun p664MatchesIndependentAffineTranslations() =
        assertVectors(Fp2P664, sharedDifferenceP664Vectors)

    @Test
    fun rankDeficientEliminationIsNotTreatedAsInfinity() {
        val fields = sharedDifferenceP324Vectors.last().split(':')
        val curve =
            MontgomeryCurve.fromCoefficients(
                Fp2P324,
                Fp2P324.fromLittleEndian(fields[0].hexToByteArray()),
                Fp2P324.fromLittleEndian(fields[1].hexToByteArray()),
            )
        val points = (2..8 step 2).map { vectorPoint(Fp2P324, fields, it) }

        val failure =
            assertFailsWith<IllegalArgumentException> {
                curve.sharedDifference(points[0], points[1], points[2], points[3])
            }

        assertEquals("Projective point coordinates must not both be zero", failure.message)
    }

    private fun <Element : QuadraticFieldElement<Element>> assertVectors(
        field: QuadraticField<Element>,
        records: List<String>,
    ) {
        for ((index, record) in records.dropLast(1).withIndex()) {
            val fields = record.split(':')
            val curve =
                MontgomeryCurve.fromCoefficients(
                    field,
                    field.fromLittleEndian(fields[0].hexToByteArray()),
                    field.fromLittleEndian(fields[1].hexToByteArray()),
                )
            val points = (2..8 step 2).map { vectorPoint(field, fields, it) }

            val actual = curve.sharedDifference(points[0], points[1], points[2], points[3])

            val context = "${field.parameters.identifier} shared difference $index"
            if (fields[10] == "-") assertTrue(actual.isInfinity(), context)
            else
                assertContentEquals(
                    fields[10].hexToByteArray(),
                    assertNotNull(actual.affineX(), context).toLittleEndian(),
                    context,
                )
            for ((offset, point) in points.withIndex()) {
                assertContentEquals(
                    fields[2 + 2 * offset].hexToByteArray(),
                    point.x.toLittleEndian(),
                )
                assertContentEquals(
                    fields[3 + 2 * offset].hexToByteArray(),
                    point.z.toLittleEndian(),
                )
            }
        }
    }

    private fun <Element : QuadraticFieldElement<Element>> vectorPoint(
        field: QuadraticField<Element>,
        fields: List<String>,
        offset: Int,
    ): MontgomeryPoint<Element> =
        MontgomeryPoint.fromCoordinates(
            field,
            field.fromLittleEndian(fields[offset].hexToByteArray()),
            field.fromLittleEndian(fields[offset + 1].hexToByteArray()),
        )

    private fun point(x: Int, z: Int): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(
            Fp2P324,
            Fp2P324.fromComponents(Fp324.fromInt(x), Fp324.zero()),
            Fp2P324.fromComponents(Fp324.fromInt(z), Fp324.zero()),
        )
}
