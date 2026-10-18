// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TorsionBasisTest {
    @Test
    fun theThreeDistinctNonzeroTwoTorsionPointsFormAValidatedBasis() {
        val field = Fp2P324
        val curve = MontgomeryCurve.fromCoefficients(field, field.zero(), field.one())
        val imaginary = element(0, 1)
        val first = MontgomeryPoint.fromCoordinates(field, field.zero(), field.one())
        val second = MontgomeryPoint.fromCoordinates(field, imaginary, field.one())
        val difference = MontgomeryPoint.fromCoordinates(field, imaginary.negate(), field.one())

        val basis = TorsionBasis.checked(curve, 1, first, second, difference)

        assertEquals(1, basis.exponent)
        assertTrue(basis.first.isEqualTo(first))
        assertTrue(basis.second.isEqualTo(second))
        assertTrue(basis.difference.isEqualTo(difference))
    }

    @Test
    fun allSixTwoTorsionOrientationsAreValid() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val points =
            listOf(
                point(0),
                point(0, 1),
                MontgomeryPoint.fromCoordinates(Fp2P324, element(0, 1).negate(), Fp2P324.one()),
            )
        for (first in points.indices) {
            for (second in points.indices.filter { it != first }) {
                val difference = points[3 - first - second]

                val basis =
                    TorsionBasis.checked(curve, 1, points[first], points[second], difference)

                assertTrue(basis.difference.isEqualTo(difference), "$first, $second")
            }
        }
    }

    @Test
    fun invalidExponentsAreRejectedBeforePointValidation() {
        for (exponent in listOf(0, -1, Fp2P324.parameters.torsionExponent + 1)) {
            assertRejected(
                exponent,
                List(3) { MontgomeryPoint.infinity(Fp2P324) },
                "Torsion exponent must be between one and the parameter maximum",
            )
        }
    }

    @Test
    fun infinityInAnyBasisSlotIsRejected() {
        val points = listOf(point(0), point(0, 1), point(0, 1))
        for (offset in points.indices) {
            assertRejected(
                1,
                points.mapIndexed { index, point ->
                    if (index == offset) MontgomeryPoint.infinity(Fp2P324) else point
                },
                "Basis points must be finite",
            )
        }
    }

    @Test
    fun offCurveCoordinatesAreNotAcceptedAsTorsionPoints() {
        assertRejected(
            1,
            listOf(point(1, 1), point(0, 1), point(0)),
            "Basis points must lie on the curve",
        )
    }

    @Test
    fun generatorsOfTooSmallAnOrderAreRejected() {
        assertRejected(
            2,
            listOf(point(0), point(0, 1), point(0)),
            "Basis generators must have the exact requested torsion order",
        )
    }

    @Test
    fun generatorsNotKilledByTheRequestedPowerAreRejected() {
        assertRejected(
            1,
            listOf(point(2), point(0, 1), point(0)),
            "Basis generators must have the exact requested torsion order",
        )
    }

    @Test
    fun exactOrderAloneDoesNotProveIndependence() {
        assertRejected(
            1,
            listOf(point(0), point(0), point(0, 1)),
            "Basis generators must be independent",
        )
    }

    @Test
    fun independentGeneratorsStillRequireAConsistentDifference() {
        assertRejected(
            1,
            listOf(point(0), point(0, 1), point(0)),
            "Basis points must form a consistent differential triple",
        )
    }

    @Test
    fun aCheckedBasisCannotWitnessADifferentCurve() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val difference =
            MontgomeryPoint.fromCoordinates(Fp2P324, element(0, 1).negate(), Fp2P324.one())
        val basis = TorsionBasis.checked(curve, 1, point(0), point(0, 1), difference)
        val other = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.one(), Fp2P324.one())

        val failure = assertFailsWith<IllegalArgumentException> { basis.requireCurve(other) }

        assertEquals("Torsion basis must be bound to the supplied curve", failure.message)
    }

    @Test
    fun aCheckedBasisAcceptsTheSameCurveWithIndependentProjectiveScale() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val difference =
            MontgomeryPoint.fromCoordinates(Fp2P324, element(0, 1).negate(), Fp2P324.one())
        val basis = TorsionBasis.checked(curve, 1, point(0), point(0, 1), difference)
        val scaled = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), element(3, 5))

        basis.requireCurve(scaled)

        assertTrue(basis.first.isEqualTo(point(0)))
    }

    private fun assertRejected(
        exponent: Int,
        points: List<MontgomeryPoint<Fp2P324>>,
        reason: String,
    ) {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())

        val failure =
            assertFailsWith<IllegalArgumentException> {
                TorsionBasis.checked(curve, exponent, points[0], points[1], points[2])
            }

        assertEquals(reason, failure.message)
    }

    private fun point(real: Int, imaginary: Int = 0): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(Fp2P324, element(real, imaginary), Fp2P324.one())

    private fun element(real: Int, imaginary: Int = 0): Fp2P324 =
        Fp2P324.fromLittleEndian(
            ByteArray(Fp2P324.parameters.extensionFieldBytes).also {
                it[0] = real.toByte()
                it[Fp2P324.parameters.fieldBytes] = imaginary.toByte()
            },
        )
}
