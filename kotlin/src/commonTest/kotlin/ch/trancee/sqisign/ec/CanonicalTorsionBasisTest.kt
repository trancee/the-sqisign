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

class CanonicalTorsionBasisTest {
    @Test
    fun p324SquareReconstructionTargetMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[3])

    @Test
    fun p324NonsquareReconstructionTargetMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[7])

    @Test
    fun p500SquareReconstructionTargetMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[3])

    @Test
    fun p500NonsquareReconstructionTargetMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[7])

    @Test
    fun p664SquareReconstructionTargetMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[3])

    @Test
    fun p664NonsquareReconstructionTargetMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[7])

    @Test
    fun p324SquareOrderTwoReconstructionMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[8])

    @Test
    fun p324NonsquareOrderTwoReconstructionMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[9])

    @Test
    fun p500SquareOrderTwoReconstructionMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[8])

    @Test
    fun p500NonsquareOrderTwoReconstructionMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[9])

    @Test
    fun p664SquareOrderTwoReconstructionMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[8])

    @Test
    fun p664NonsquareOrderTwoReconstructionMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[9])

    @Test
    fun p324SquareTwoTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[0])

    @Test
    fun p324SquareIntermediateTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[1])

    @Test
    fun p324SquareFullTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[2])

    @Test
    fun p324NonsquareTwoTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[4])

    @Test
    fun p324NonsquareIntermediateTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[5])

    @Test
    fun p324NonsquareFullTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P324, canonicalBasisP324Vectors[6])

    @Test
    fun p500SquareTwoTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[0])

    @Test
    fun p500SquareIntermediateTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[1])

    @Test
    fun p500SquareFullTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[2])

    @Test
    fun p500NonsquareTwoTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[4])

    @Test
    fun p500NonsquareIntermediateTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[5])

    @Test
    fun p500NonsquareFullTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P500, canonicalBasisP500Vectors[6])

    @Test
    fun p664SquareTwoTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[0])

    @Test
    fun p664SquareIntermediateTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[1])

    @Test
    fun p664SquareFullTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[2])

    @Test
    fun p664NonsquareTwoTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[4])

    @Test
    fun p664NonsquareIntermediateTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[5])

    @Test
    fun p664NonsquareFullTorsionMatchesTheIndependentOracle() =
        assertVector(Fp2P664, canonicalBasisP664Vectors[6])

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

        val (actual, hint) = TorsionBasis.canonical(curve, expected[1].toInt(), expected[2].toInt())

        assertEquals(expected[1].toInt(), actual.exponent)
        assertEquals(expected[3].toInt().toByte(), hint.encoded)
        for ((point, encoded) in
            listOf(actual.first, actual.second, actual.difference).zip(expected.drop(4))) {
            assertContentEquals(
                encoded.hexToByteArray(),
                assertNotNull(point.affineX()).toLittleEndian(),
                "${field.parameters.identifier}, exponent ${actual.exponent}",
            )
        }
        assertContentEquals(
            coefficient.toLittleEndian(),
            curve.montgomeryCoefficient().toLittleEndian(),
        )
    }
}
