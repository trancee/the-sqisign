// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BasisCorrespondenceTest {
    private val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())

    @Test
    fun canonicalHintsAndTheTransportedMatrixPreserveTheTwoTorsionSwap() {
        val field = Fp2P324
        val points =
            e0BasisP324Vectors.first().split(':').drop(1).map {
                MontgomeryPoint.fromCoordinates(
                    field,
                    field.fromLittleEndian(it.hexToByteArray()),
                    field.one(),
                )
            }
        val first = TorsionBasis.checked(curve, 1, points[0], points[1], points[2])
        val second = TorsionBasis.checked(curve, 1, points[1], points[0], points[2])

        val actual = setChangeOfBasisMatrix(curve, first, curve, second)

        assertEquals(
            listOf("00", "01", "01", "00"),
            actual.matrix.rowMajor().map { it.toLittleEndian().toHexString() },
        )
        assertEquals(0, actual.firstHint.encoded.toInt())
        assertEquals(0, actual.secondHint.encoded.toInt())
    }

    @Test
    fun theTwoInputBasesMustHaveTheSameExponentBeforeGeneration() {
        val first = fixtureBasis(1)
        val second = fixtureBasis(2)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                setChangeOfBasisMatrix(curve, first, curve, second)
            }

        assertEquals("Basis correspondence requires matching torsion exponents", failure.message)
    }

    @Test
    fun theFirstBasisMustMatchItsCurveBeforeCanonicalGeneration() {
        val first = fixtureBasis(1)
        val second = fixtureBasis(1)
        val other = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.one(), Fp2P324.one())

        val failure =
            assertFailsWith<IllegalArgumentException> {
                setChangeOfBasisMatrix(other, first, curve, second)
            }

        assertEquals("Torsion basis must be bound to the supplied curve", failure.message)
    }

    @Test
    fun theSecondBasisMustMatchItsCurveBeforeCanonicalGeneration() {
        val first = fixtureBasis(1)
        val second = fixtureBasis(1)
        val other = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.one(), Fp2P324.one())

        val failure =
            assertFailsWith<IllegalArgumentException> {
                setChangeOfBasisMatrix(curve, first, other, second)
            }

        assertEquals("Torsion basis must be bound to the supplied curve", failure.message)
    }

    private fun fixtureBasis(exponent: Int): TorsionBasis<Fp2P324> {
        val encoded = e0BasisP324Vectors.first { it.substringBefore(':').toInt() == exponent }
        val points =
            encoded.split(':').drop(1).map {
                MontgomeryPoint.fromCoordinates(
                    Fp2P324,
                    Fp2P324.fromLittleEndian(it.hexToByteArray()),
                    Fp2P324.one(),
                )
            }
        return TorsionBasis.checked(curve, exponent, points[0], points[1], points[2])
    }
}
