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

class E0HintOrientationTest {
    @Test fun p324IntermediateOrientation() = assertVector(Fp2P324, hintedE0P324Vectors[0])

    @Test fun p324FullOrientation() = assertVector(Fp2P324, hintedE0P324Vectors[1])

    @Test fun p500IntermediateOrientation() = assertVector(Fp2P500, hintedE0P500Vectors[0])

    @Test fun p500FullOrientation() = assertVector(Fp2P500, hintedE0P500Vectors[1])

    @Test fun p664IntermediateOrientation() = assertVector(Fp2P664, hintedE0P664Vectors[0])

    @Test fun p664FullOrientation() = assertVector(Fp2P664, hintedE0P664Vectors[1])

    @Test
    fun p324PlainGenerationPreservesE0() = assertPlainGeneration(Fp2P324, e0BasisP324Vectors[2])

    @Test
    fun p500PlainGenerationPreservesE0() = assertPlainGeneration(Fp2P500, e0BasisP500Vectors[2])

    @Test
    fun p664PlainGenerationPreservesE0() = assertPlainGeneration(Fp2P664, e0BasisP664Vectors[2])

    @Test
    fun p324ZeroHintDecodingPreservesE0() {
        val curve = e0Curve(Fp2P324)
        val expected = e0BasisP324Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(0))

        assertCoordinates(actual, expected)
    }

    @Test
    fun p324HintOneDecodingPreservesE0() {
        val curve = e0Curve(Fp2P324)
        val expected = e0BasisP324Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(1))

        assertCoordinates(actual, expected)
    }

    @Test
    fun p324UnsignedMaxHintDecodingPreservesE0() {
        val curve = e0Curve(Fp2P324)
        val expected = e0BasisP324Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(0xff.toByte()))

        assertCoordinates(actual, expected)
    }

    @Test
    fun p500ZeroHintDecodingPreservesE0() {
        val curve = e0Curve(Fp2P500)
        val expected = e0BasisP500Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(0))

        assertCoordinates(actual, expected)
    }

    @Test
    fun p500HintOneDecodingPreservesE0() {
        val curve = e0Curve(Fp2P500)
        val expected = e0BasisP500Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(1))

        assertCoordinates(actual, expected)
    }

    @Test
    fun p500UnsignedMaxHintDecodingPreservesE0() {
        val curve = e0Curve(Fp2P500)
        val expected = e0BasisP500Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(0xff.toByte()))

        assertCoordinates(actual, expected)
    }

    @Test
    fun p664ZeroHintDecodingPreservesE0() {
        val curve = e0Curve(Fp2P664)
        val expected = e0BasisP664Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(0))

        assertCoordinates(actual, expected)
    }

    @Test
    fun p664HintOneDecodingPreservesE0() {
        val curve = e0Curve(Fp2P664)
        val expected = e0BasisP664Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(1))

        assertCoordinates(actual, expected)
    }

    @Test
    fun p664UnsignedMaxHintDecodingPreservesE0() {
        val curve = e0Curve(Fp2P664)
        val expected = e0BasisP664Vectors[2].split(':').drop(1)

        val actual = TorsionBasis.fromHint(curve, 8, BasisHint.fromEncoded(0xff.toByte()))

        assertCoordinates(actual, expected)
    }

    private fun <Element : QuadraticFieldElement<Element>> assertVector(
        field: QuadraticField<Element>,
        record: String,
    ) {
        val expected = record.split(':')
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())

        val (actual, hint) = TorsionBasis.canonical(curve, expected[0].toInt(), expected[1].toInt())

        assertEquals(0.toByte(), hint.encoded)
        assertCoordinates(actual, expected.drop(2))
    }

    private fun <Element : QuadraticFieldElement<Element>> assertPlainGeneration(
        field: QuadraticField<Element>,
        record: String,
    ) {
        val expected = record.split(':')
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())

        val (actual, hint) = TorsionBasis.canonical(curve, expected[0].toInt())

        assertEquals(0.toByte(), hint.encoded)
        assertCoordinates(actual, expected.drop(1))
    }

    private fun <Element : QuadraticFieldElement<Element>> e0Curve(
        field: QuadraticField<Element>,
    ): MontgomeryCurve<Element> = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())

    private fun <Element : QuadraticFieldElement<Element>> assertCoordinates(
        actual: TorsionBasis<Element>,
        expected: List<String>,
    ) {
        for ((point, encoded) in
            listOf(actual.first, actual.second, actual.difference).zip(expected)) {
            assertContentEquals(
                encoded.hexToByteArray(),
                assertNotNull(point.affineX()).toLittleEndian(),
            )
        }
    }
}
