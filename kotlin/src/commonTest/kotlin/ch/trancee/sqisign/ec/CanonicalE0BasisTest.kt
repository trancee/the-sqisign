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
import kotlin.test.assertTrue

class CanonicalE0BasisTest {
    @Test
    fun p324TwoTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P324, e0BasisP324Vectors[0])

    @Test
    fun p324FourTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P324, e0BasisP324Vectors[1])

    @Test
    fun p324IntermediateTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P324, e0BasisP324Vectors[2])

    @Test
    fun p324FullTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P324, e0BasisP324Vectors[3])

    @Test
    fun p500TwoTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P500, e0BasisP500Vectors[0])

    @Test
    fun p500FourTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P500, e0BasisP500Vectors[1])

    @Test
    fun p500IntermediateTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P500, e0BasisP500Vectors[2])

    @Test
    fun p500FullTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P500, e0BasisP500Vectors[3])

    @Test
    fun p664TwoTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P664, e0BasisP664Vectors[0])

    @Test
    fun p664FourTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P664, e0BasisP664Vectors[1])

    @Test
    fun p664IntermediateTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P664, e0BasisP664Vectors[2])

    @Test
    fun p664FullTorsionMatchesTheIndependentCanonicalOracle() =
        assertVector(Fp2P664, e0BasisP664Vectors[3])

    @Test
    fun invalidExponentsAreRejectedWithTheSharedBasisBound() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        for (exponent in listOf(-1, 0, Fp2P324.parameters.torsionExponent + 1)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    TorsionBasis.canonicalE0(curve, exponent)
                }

            assertEquals(
                "Torsion exponent must be between one and the parameter maximum",
                failure.message,
            )
        }
    }

    @Test
    fun nonzeroCoefficientsCannotUseTheSpecialE0Basis() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.one(), Fp2P324.one())

        val failure =
            assertFailsWith<IllegalArgumentException> { TorsionBasis.canonicalE0(curve, 1) }

        assertEquals("Canonical E0 basis requires Montgomery coefficient zero", failure.message)
    }

    private fun <Element : QuadraticFieldElement<Element>> assertVector(
        field: QuadraticField<Element>,
        record: String,
    ) {
        val scale =
            field.fromLittleEndian(
                ByteArray(field.parameters.extensionFieldBytes).also {
                    it[0] = 2
                    it[field.parameters.fieldBytes] = 3
                },
            )
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), scale)
        val expected = record.split(':')
        val exponent = expected[0].toInt()

        val actual = TorsionBasis.canonicalE0(curve, exponent)

        assertEquals(exponent, actual.exponent)
        for ((point, encoded) in
            listOf(actual.first, actual.second, actual.difference).zip(expected.drop(1))) {
            assertContentEquals(
                encoded.hexToByteArray(),
                assertNotNull(point.affineX()).toLittleEndian(),
                "${field.parameters.identifier}, exponent $exponent",
            )
        }
        assertTrue(curve.montgomeryCoefficient().isEqualTo(field.zero()))
    }
}
