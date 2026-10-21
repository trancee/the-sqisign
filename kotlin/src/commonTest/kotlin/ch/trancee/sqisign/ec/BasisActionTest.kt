// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BasisActionTest {
    private val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())

    @Test
    fun swappingColumnsSwapsTheGeneratorsAndRetainsTheirDifference() {
        val basis = twoTorsionBasis()
        val zero = scalar(1, 0)
        val one = scalar(1, 1)
        val swap = TorsionMatrix.of(zero, one, one, zero)

        val actual = basis.applyMatrix(curve, swap)

        assertEquals(1, actual.exponent)
        assertTrue(actual.first.isEqualTo(basis.second))
        assertTrue(actual.second.isEqualTo(basis.first))
        assertTrue(actual.difference.isEqualTo(basis.difference))
    }

    @Test
    fun singularMatricesCannotProduceACheckedBasis() {
        val basis = twoTorsionBasis()
        val one = scalar(1, 1)
        val singular = TorsionMatrix.of(one, one, one, one)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                basis.applyMatrix(curve, singular)
            }

        assertEquals("Basis action requires a matrix with odd determinant", failure.message)
    }

    @Test
    fun matrixAndBasisMustHaveTheSameExponent() {
        val basis = twoTorsionBasis()
        val zero = scalar(2, 0)
        val one = scalar(2, 1)
        val identity = TorsionMatrix.of(one, zero, zero, one)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                basis.applyMatrix(curve, identity)
            }

        assertEquals("Basis action requires matching basis and matrix exponents", failure.message)
    }

    @Test
    fun aBasisCannotActOnAnotherCurveEvenWithAnInvalidMatrix() {
        val basis = twoTorsionBasis()
        val other = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.one(), Fp2P324.one())
        val zero = scalar(2, 0)
        val matrix = TorsionMatrix.of(zero, zero, zero, zero)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                basis.applyMatrix(other, matrix)
            }

        assertEquals("Torsion basis must be bound to the supplied curve", failure.message)
    }

    @Test
    fun exponentMismatchIsRejectedBeforeSingularity() {
        val basis = twoTorsionBasis()
        val zero = scalar(2, 0)
        val matrix = TorsionMatrix.of(zero, zero, zero, zero)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                basis.applyMatrix(curve, matrix)
            }

        assertEquals("Basis action requires matching basis and matrix exponents", failure.message)
    }

    @Test
    fun independentProjectiveCurveScalesRetainTheCheckedBasisBinding() {
        val basis = twoTorsionBasis()
        val scaled =
            MontgomeryCurve.fromCoefficients(
                Fp2P324,
                Fp2P324.zero(),
                Fp2P324.one().add(Fp2P324.one()),
            )
        val zero = scalar(1, 0)
        val one = scalar(1, 1)
        val identity = TorsionMatrix.of(one, zero, zero, one)

        val actual = basis.applyMatrix(scaled, identity)

        assertTrue(actual.first.isEqualTo(basis.first))
        assertTrue(actual.second.isEqualTo(basis.second))
        assertTrue(actual.difference.isEqualTo(basis.difference))
        actual.requireCurve(curve)
    }

    private fun twoTorsionBasis(): TorsionBasis<Fp2P324> {
        val imaginary =
            Fp2P324.fromLittleEndian(
                ByteArray(Fp2P324.parameters.extensionFieldBytes).also {
                    it[Fp2P324.parameters.fieldBytes] = 1
                },
            )
        val first = MontgomeryPoint.fromCoordinates(Fp2P324, imaginary, Fp2P324.one())
        val second = MontgomeryPoint.fromCoordinates(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val difference = MontgomeryPoint.fromCoordinates(Fp2P324, imaginary.negate(), Fp2P324.one())
        return TorsionBasis.checked(curve, 1, first, second, difference)
    }

    private fun scalar(exponent: Int, value: Int): TorsionScalar =
        TorsionScalar.fromLittleEndian(exponent, byteArrayOf(value.toByte()))
}
