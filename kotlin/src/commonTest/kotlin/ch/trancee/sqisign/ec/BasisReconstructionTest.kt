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
import kotlin.test.assertNotNull

class BasisReconstructionTest {
    @Test fun p324SquareTwoTorsion() = assertVector(Fp2P324, reconstructionP324Vectors[0])

    @Test fun p324SquareIntermediateTorsion() = assertVector(Fp2P324, reconstructionP324Vectors[1])

    @Test fun p324SquareFullTorsion() = assertVector(Fp2P324, reconstructionP324Vectors[2])

    @Test fun p324NonsquareTwoTorsion() = assertVector(Fp2P324, reconstructionP324Vectors[3])

    @Test
    fun p324NonsquareIntermediateTorsion() = assertVector(Fp2P324, reconstructionP324Vectors[4])

    @Test fun p324NonsquareFullTorsion() = assertVector(Fp2P324, reconstructionP324Vectors[5])

    @Test fun p324NonminimalSquareHint() = assertVector(Fp2P324, reconstructionP324Vectors[6])

    @Test fun p324NonminimalNonsquareHint() = assertVector(Fp2P324, reconstructionP324Vectors[7])

    @Test fun p324SignedByteBoundary() = assertVector(Fp2P324, reconstructionP324Vectors[8])

    @Test fun p500SquareTwoTorsion() = assertVector(Fp2P500, reconstructionP500Vectors[0])

    @Test fun p500SquareIntermediateTorsion() = assertVector(Fp2P500, reconstructionP500Vectors[1])

    @Test fun p500SquareFullTorsion() = assertVector(Fp2P500, reconstructionP500Vectors[2])

    @Test fun p500NonsquareTwoTorsion() = assertVector(Fp2P500, reconstructionP500Vectors[3])

    @Test
    fun p500NonsquareIntermediateTorsion() = assertVector(Fp2P500, reconstructionP500Vectors[4])

    @Test fun p500NonsquareFullTorsion() = assertVector(Fp2P500, reconstructionP500Vectors[5])

    @Test fun p500NonminimalSquareHint() = assertVector(Fp2P500, reconstructionP500Vectors[6])

    @Test fun p500NonminimalNonsquareHint() = assertVector(Fp2P500, reconstructionP500Vectors[7])

    @Test fun p500SquareSignedByteBoundary() = assertVector(Fp2P500, reconstructionP500Vectors[8])

    @Test
    fun p500NonsquareSignedByteBoundary() = assertVector(Fp2P500, reconstructionP500Vectors[9])

    @Test fun p664SquareTwoTorsion() = assertVector(Fp2P664, reconstructionP664Vectors[0])

    @Test fun p664SquareIntermediateTorsion() = assertVector(Fp2P664, reconstructionP664Vectors[1])

    @Test fun p664SquareFullTorsion() = assertVector(Fp2P664, reconstructionP664Vectors[2])

    @Test fun p664NonsquareTwoTorsion() = assertVector(Fp2P664, reconstructionP664Vectors[3])

    @Test
    fun p664NonsquareIntermediateTorsion() = assertVector(Fp2P664, reconstructionP664Vectors[4])

    @Test fun p664NonsquareFullTorsion() = assertVector(Fp2P664, reconstructionP664Vectors[5])

    @Test fun p664NonminimalSquareHint() = assertVector(Fp2P664, reconstructionP664Vectors[6])

    @Test fun p664NonminimalNonsquareHint() = assertVector(Fp2P664, reconstructionP664Vectors[7])

    private fun <Element : QuadraticFieldElement<Element>> assertVector(
        field: QuadraticField<Element>,
        record: String,
    ) {
        val expected = record.split(':')
        val coefficient = field.fromLittleEndian(expected[0].hexToByteArray())
        val scale =
            field.fromLittleEndian(
                ByteArray(field.parameters.extensionFieldBytes).also {
                    it[0] = 2
                    it[field.parameters.fieldBytes] = 3
                },
            )
        val curve =
            MontgomeryCurve.fromCoefficients(
                field,
                coefficient.multiply(scale),
                scale,
            )

        val actual =
            TorsionBasis.fromHint(
                curve,
                expected[1].toInt(),
                BasisHint.fromEncoded(expected[2].toInt().toByte()),
            )

        assertEquals(expected[1].toInt(), actual.exponent)
        for ((point, encoded) in
            listOf(actual.first, actual.second, actual.difference).zip(expected.drop(3))) {
            assertContentEquals(
                encoded.hexToByteArray(),
                assertNotNull(point.affineX()).toLittleEndian(),
                "${field.parameters.identifier}, hint ${expected[2]}",
            )
        }
    }
}
