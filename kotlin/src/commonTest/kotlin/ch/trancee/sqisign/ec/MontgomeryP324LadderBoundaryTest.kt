// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp324
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MontgomeryP324LadderBoundaryTest {
    @Test
    fun biscalarMultiplicationHandlesAllTwoTorsionBasesAndFullWidthParities() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val imaginary = Fp2P324.fromComponents(Fp324.zero(), Fp324.one())
        val torsion = listOf(point(Fp2P324.zero()), point(imaginary), point(imaginary.negate()))
        val scalars = listOf(scalar(0), scalar(1), scalar(2), scalar(255, fullWidth = true))
        val orderings = listOf(0 to 1, 0 to 2, 1 to 0, 1 to 2, 2 to 0, 2 to 1)
        for ((leftIndex, rightIndex) in orderings) {
            val left = torsion[leftIndex]
            val right = torsion[rightIndex]
            val difference = torsion[3 - leftIndex - rightIndex]
            for (scalarLeft in scalars) {
                for (scalarRight in scalars) {
                    val leftOdd = scalarLeft[0].toInt() and 1 == 1
                    val rightOdd = scalarRight[0].toInt() and 1 == 1
                    val expected =
                        if (leftOdd && rightOdd) difference
                        else if (leftOdd) left
                        else if (rightOdd) right else MontgomeryPoint.infinity(Fp2P324)

                    val actual =
                        curve.biscalarMultiply(left, right, difference, scalarLeft, scalarRight)

                    assertTrue(actual.isEqualTo(expected), "two-torsion $leftIndex/$rightIndex")
                }
            }
        }
    }

    @Test
    fun biscalarLadderRejectsInfinityInEveryOperandEvenForZeroScalars() {
        val fields = curveP324Vectors[0].split(':')
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(fields[0]), field(fields[1]))
        val regular = listOf(coordinates(fields, 2), coordinates(fields, 4), coordinates(fields, 6))
        for (index in regular.indices) {
            val points =
                regular.toMutableList().also { it[index] = MontgomeryPoint.infinity(Fp2P324) }

            val failure =
                assertFailsWith<IllegalArgumentException> {
                    curve.biscalarMultiply(points[0], points[1], points[2], scalar(0), scalar(0))
                }

            assertEquals("Biscalar ladder requires finite operands", failure.message)
        }
    }

    @Test
    fun exceptionalBiscalarCoordinatesRequireADistinctTwoTorsionBasis() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val zero = point(Fp2P324.zero())
        val imaginary = point(Fp2P324.fromComponents(Fp324.zero(), Fp324.one()))
        val ordinary = point(Fp2P324.fromComponents(Fp324.fromInt(2), Fp324.zero()))
        val cases =
            listOf(
                Triple(ordinary, imaginary, zero),
                Triple(imaginary, ordinary, zero),
                Triple(imaginary, zero, ordinary),
                Triple(imaginary, imaginary, zero),
                Triple(imaginary, zero, imaginary),
                Triple(zero, imaginary, imaginary),
            )
        for ((left, right, difference) in cases) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    curve.biscalarMultiply(left, right, difference, scalar(1), scalar(1))
                }

            assertEquals(
                "Exceptional biscalar operands must form a distinct two-torsion basis",
                failure.message,
            )
        }
    }

    @Test
    fun positiveBiscalarCoefficientsRejectExceptionalSums() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val left = point(Fp2P324.fromComponents(Fp324.fromInt(2), Fp324.zero()))
        val cases =
            listOf(
                left to rationalPoint(9, 40),
                rationalPoint(1, 2) to rationalPoint(40, 9),
            )
        for ((right, difference) in cases) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    curve.biscalarMultiply(left, right, difference, scalar(1), scalar(1))
                }

            assertEquals("Biscalar ladder requires a finite nonzero sum", failure.message)
        }
    }

    private fun coordinates(fields: List<String>, offset: Int): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(Fp2P324, field(fields[offset]), field(fields[offset + 1]))

    private fun rationalPoint(numerator: Int, denominator: Int): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(
            Fp2P324,
            Fp2P324.fromComponents(Fp324.fromInt(numerator), Fp324.zero()),
            Fp2P324.fromComponents(Fp324.fromInt(denominator), Fp324.zero()),
        )

    private fun point(x: Fp2P324): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(Fp2P324, x, Fp2P324.one())

    private fun field(encoded: String): Fp2P324 = Fp2P324.fromLittleEndian(encoded.hexToByteArray())

    private fun scalar(value: Int, fullWidth: Boolean = false): ByteArray =
        ByteArray(41) { if (fullWidth) value.toByte() else 0 }.also { it[0] = value.toByte() }
}
