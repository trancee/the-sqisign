// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp324
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MontgomeryP324Test {
    @Test
    fun curveCoefficientIsIndependentOfProjectiveScale() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(15), field(3))

        val coefficient = curve.montgomeryCoefficient()

        assertTrue(coefficient.isEqualTo(field(5)))
    }

    @Test
    fun curvesRejectZeroDenominatorAndBothSingularCoefficients() {
        val cases =
            listOf(
                field(0) to field(0),
                field(1) to field(0),
                field(6) to field(3),
                field(6).negate() to field(3),
            )

        for ((numerator, denominator) in cases) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    MontgomeryCurve.fromCoefficients(Fp2P324, numerator, denominator)
                }

            assertEquals(
                if (denominator.isEqualTo(field(0))) "Montgomery curve denominator must be nonzero"
                else "Montgomery curve must be nonsingular",
                failure.message,
            )
        }
    }

    @Test
    fun pointsCompareProjectivelyIncludingScaledInfinity() {
        val point = MontgomeryPoint.fromCoordinates(Fp2P324, field(15), field(3))
        val infinity = MontgomeryPoint.fromCoordinates(Fp2P324, field(7), field(0))
        val cases =
            listOf(
                Triple(point, MontgomeryPoint.fromCoordinates(Fp2P324, field(5), field(1)), true),
                Triple(point, MontgomeryPoint.fromCoordinates(Fp2P324, field(6), field(1)), false),
                Triple(point, infinity, false),
                Triple(infinity, point, false),
                Triple(infinity, MontgomeryPoint.infinity(Fp2P324), true),
            )

        for ((left, right, expected) in cases) {
            val actual = left.isEqualTo(right)

            assertEquals(expected, actual)
        }
    }

    @Test
    fun finitePointsNormalizeToAnAffineCoordinate() {
        val point = MontgomeryPoint.fromCoordinates(Fp2P324, field(15), field(3))

        val actual = point.affineX()

        assertTrue(assertNotNull(actual).isEqualTo(field(5)))
    }

    @Test
    fun infinityHasNoAffineCoordinate() {
        val infinity = MontgomeryPoint.fromCoordinates(Fp2P324, field(7), field(0))

        val actual = infinity.affineX()

        assertNull(actual)
    }

    @Test
    fun infinityIsDeterminedOnlyByTheDenominator() {
        val points =
            listOf(
                MontgomeryPoint.fromCoordinates(Fp2P324, field(0), field(1)) to false,
                MontgomeryPoint.fromCoordinates(Fp2P324, field(1), field(1)) to false,
                MontgomeryPoint.fromCoordinates(Fp2P324, field(7), field(0)) to true,
            )

        for ((point, expected) in points) {
            val actual = point.isInfinity()

            assertEquals(expected, actual)
        }
    }

    @Test
    fun pointsRejectTheUndefinedZeroZeroPair() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                MontgomeryPoint.fromCoordinates(Fp2P324, field(0), field(0))
            }

        assertEquals("Projective point coordinates must not both be zero", failure.message)
    }

    @Test
    fun doublingHandlesInfinityAndEveryTwoTorsionPointOnTheBaseCurve() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(0), field(1))
        val imaginaryUnit = Fp2P324.fromComponents(Fp324.zero(), Fp324.one())
        val points =
            listOf(
                MontgomeryPoint.infinity(Fp2P324),
                MontgomeryPoint.fromCoordinates(Fp2P324, field(0), field(1)),
                MontgomeryPoint.fromCoordinates(Fp2P324, imaginaryUnit, field(1)),
                MontgomeryPoint.fromCoordinates(Fp2P324, imaginaryUnit.negate(), field(1)),
            )

        for (point in points) {
            val doubled = curve.double(point)

            assertTrue(doubled.isInfinity())
        }
    }

    @Test
    fun differentialAdditionRejectsInfinityAndZeroAsDifferences() {
        val point = MontgomeryPoint.fromCoordinates(Fp2P324, field(1), field(1))
        val differences =
            listOf(
                MontgomeryPoint.infinity(Fp2P324),
                MontgomeryPoint.fromCoordinates(Fp2P324, field(0), field(1)),
            )

        for (difference in differences) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    MontgomeryPoint.differentialAdd(point, point, difference)
                }

            assertEquals(
                "Differential addition requires a finite nonzero difference",
                failure.message,
            )
        }
    }

    @Test
    fun differentialAdditionPreservesInfinityIdentityAndCanReturnInfinity() {
        // On A=0, x=1 has order four and its double is (0:1).
        val point = MontgomeryPoint.fromCoordinates(Fp2P324, field(1), field(1))
        val twoTorsion = MontgomeryPoint.fromCoordinates(Fp2P324, field(0), field(1))
        val oppositePair = MontgomeryPoint.fromCoordinates(Fp2P324, field(2), field(1))
        val oppositeDifference = MontgomeryPoint.fromCoordinates(Fp2P324, field(9), field(40))

        val cases =
            listOf(
                Triple(MontgomeryPoint.infinity(Fp2P324), point, point) to point,
                Triple(twoTorsion, point, point) to point,
                Triple(oppositePair, oppositePair, oppositeDifference) to
                    MontgomeryPoint.infinity(Fp2P324),
            )

        for ((inputs, expected) in cases) {
            val (left, right, difference) = inputs

            val actual = MontgomeryPoint.differentialAdd(left, right, difference)

            assertTrue(actual.isEqualTo(expected))
        }
    }

    @Test
    fun ladderPreservesInfinityForAllScalarParities() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(0), field(1))
        val infinity = MontgomeryPoint.infinity(Fp2P324)

        for (scalar in listOf(0, 1, 2, 3, 8, 255)) {
            val product = curve.multiply(infinity, scalar(scalar))

            assertTrue(product.isInfinity(), "infinity scalar $scalar")
        }
    }

    @Test
    fun ladderHandlesTheExceptionalZeroXTwoTorsionPoint() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(0), field(1))
        val point = MontgomeryPoint.fromCoordinates(Fp2P324, field(0), field(3))

        for (scalar in listOf(0, 1, 2, 3, 8, 255)) {
            val product = curve.multiply(point, scalar(scalar))

            assertTrue(
                if (scalar % 2 == 0) product.isInfinity() else product.isEqualTo(point),
                "zero-x scalar $scalar",
            )
        }
    }

    @Test
    fun ladderHandlesOtherTwoTorsionPoints() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(0), field(1))
        val imaginaryUnit = Fp2P324.fromComponents(Fp324.zero(), Fp324.one())
        val points =
            listOf(imaginaryUnit, imaginaryUnit.negate()).map {
                MontgomeryPoint.fromCoordinates(Fp2P324, it, field(1))
            }

        for (point in points) {
            for (scalar in listOf(0, 1, 2, 3, 8, 255)) {
                val product = curve.multiply(point, scalar(scalar))

                assertTrue(
                    if (scalar % 2 == 0) product.isInfinity() else product.isEqualTo(point),
                    "nonzero two-torsion scalar $scalar",
                )
            }
        }
    }

    @Test
    fun ladderHandlesZeroAndOrderFourPoints() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(0), field(1))
        val point = MontgomeryPoint.fromCoordinates(Fp2P324, field(1), field(1))
        val twoTorsion = MontgomeryPoint.fromCoordinates(Fp2P324, field(0), field(3))

        for (scalar in listOf(0, 1, 2, 3, 8, 255)) {
            val multiplier = scalar(scalar)

            val product = curve.multiply(point, multiplier)

            assertTrue(
                when (scalar % 4) {
                    0 -> product.isInfinity()
                    2 -> product.isEqualTo(twoTorsion)
                    else -> product.isEqualTo(point)
                },
                "order-four scalar $scalar",
            )
        }
    }
}

private fun field(real: Int): Fp2P324 = Fp2P324.fromComponents(Fp324.fromInt(real), Fp324.zero())

private fun scalar(value: Int): ByteArray = ByteArray(41).also { it[0] = value.toByte() }
