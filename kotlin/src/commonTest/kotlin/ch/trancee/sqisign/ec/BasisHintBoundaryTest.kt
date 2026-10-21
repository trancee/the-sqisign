// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp2P500
import ch.trancee.sqisign.field.Fp2P664
import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class BasisHintBoundaryTest {
    @Test
    fun p324ZeroHintRegeneratesTheSquareCurveBasis() =
        assertRegeneration(Fp2P324, reconstructionP324Vectors[1])

    @Test
    fun p324ZeroHintRegeneratesTheNonsquareCurveBasis() =
        assertRegeneration(Fp2P324, reconstructionP324Vectors[4])

    @Test
    fun p500ZeroHintRegeneratesTheSquareCurveBasis() =
        assertRegeneration(Fp2P500, reconstructionP500Vectors[1])

    @Test
    fun p500ZeroHintRegeneratesTheNonsquareCurveBasis() =
        assertRegeneration(Fp2P500, reconstructionP500Vectors[4])

    @Test
    fun p664ZeroHintRegeneratesTheSquareCurveBasis() =
        assertRegeneration(Fp2P664, reconstructionP664Vectors[1])

    @Test
    fun p664ZeroHintRegeneratesTheNonsquareCurveBasis() =
        assertRegeneration(Fp2P664, reconstructionP664Vectors[4])

    @Test
    fun invalidGenerationExponentsFailBeforeTheE0Shortcut() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P664, Fp2P664.zero(), Fp2P664.one())
        for (exponent in listOf(-1, 0, Fp2P664.parameters.torsionExponent + 1)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    TorsionBasis.canonical(curve, exponent)
                }

            assertEquals(
                "Torsion exponent must be between one and the parameter maximum",
                failure.message,
            )
        }
    }

    @Test
    fun invalidReconstructionTargetsFailBeforeTheE0Shortcut() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        for (target in listOf(-1, 8, 9)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    TorsionBasis.canonical(curve, 8, target)
                }

            assertEquals(
                "Basis reconstruction exponent must be nonnegative and below the torsion exponent",
                failure.message,
            )
        }
    }

    @Test
    fun invalidDecodingExponentsFailBeforeTheE0Shortcut() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P664, Fp2P664.zero(), Fp2P664.one())
        for (exponent in listOf(-1, 0, Fp2P664.parameters.torsionExponent + 1)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    TorsionBasis.fromHint(curve, exponent, BasisHint.fromEncoded(0))
                }

            assertEquals(
                "Torsion exponent must be between one and the parameter maximum",
                failure.message,
            )
        }
    }

    @Test
    fun hintOneIsSyntacticallyValidButCannotSelectAValidGeneralBasis() {
        val curve = p324Curve(canonicalBasisP324Vectors[0])

        val failure =
            assertFailsWith<IllegalArgumentException> {
                TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(1))
            }

        assertEquals(
            "Basis hint must select a positive search index or request regeneration",
            failure.message,
        )
    }

    @Test
    fun squareCurveOffCurveHintIsRejected() = assertOffCurveHint(canonicalBasisP324Vectors[0], 7)

    @Test
    fun nonsquareCurveOffCurveHintIsRejected() = assertOffCurveHint(canonicalBasisP324Vectors[4], 4)

    @Test
    fun anOnCurveHintWithInsufficientOrderIsRejected() {
        val curve = p324Curve(canonicalBasisP324Vectors[0])

        val failure =
            assertFailsWith<IllegalArgumentException> {
                TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(2))
            }

        assertEquals(
            "Basis generators must have the exact requested torsion order",
            failure.message,
        )
    }

    private fun assertOffCurveHint(record: String, hint: Byte) {
        val curve = p324Curve(record)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(hint))
            }

        assertEquals("Basis hint does not select a point on the curve", failure.message)
    }

    private fun p324Curve(record: String): MontgomeryCurve<Fp2P324> =
        MontgomeryCurve.fromCoefficients(
            Fp2P324,
            Fp2P324.fromLittleEndian(record.substringBefore(':').hexToByteArray()),
            Fp2P324.one(),
        )

    private fun <Element : QuadraticFieldElement<Element>> assertRegeneration(
        field: QuadraticField<Element>,
        record: String,
    ) {
        val expected = record.split(':')
        val curve =
            MontgomeryCurve.fromCoefficients(
                field,
                field.fromLittleEndian(expected[0].hexToByteArray()),
                field.one(),
            )

        val actual = TorsionBasis.fromHint(curve, expected[1].toInt(), BasisHint.fromEncoded(0))

        for ((point, encoded) in
            listOf(actual.first, actual.second, actual.difference).zip(expected.drop(3))) {
            assertContentEquals(
                encoded.hexToByteArray(),
                assertNotNull(point.affineX()).toLittleEndian(),
            )
        }
    }
}
