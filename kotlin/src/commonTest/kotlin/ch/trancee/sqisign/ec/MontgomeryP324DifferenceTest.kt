// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp324
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MontgomeryP324DifferenceTest {
    @Test
    fun deterministicDifferenceMatchesTheExactPythonAndPinnedCChoice() {
        for ((index, record) in curveP324Vectors.withIndex()) {
            val fields = record.split(':')
            val curve =
                MontgomeryCurve.fromCoefficients(Fp2P324, field(fields[0]), field(fields[1]))
            val left = point(fields, 2)
            val right = point(fields, 4)

            val actual = curve.deterministicDifference(left, right)

            assertContentEquals(
                fields[16].hexToByteArray(),
                assertNotNull(actual.affineX()).toLittleEndian(),
                "deterministic difference $index",
            )
        }
    }

    @Test
    fun deterministicChoiceIsIndependentOfAllThreeProjectiveScalesAndPreservesInputs() {
        for (record in curveP324Vectors.take(5)) {
            val fields = record.split(':')
            val coefficientScale = element(2, 3)
            val leftScale = element(3, 2)
            val rightScale = element(4, 1).negate()
            val left = point(fields, 2)
            val right = point(fields, 4)
            val original = listOf(left.x, left.z, right.x, right.z).map { it.toLittleEndian() }
            val curve =
                MontgomeryCurve.fromCoefficients(
                    Fp2P324,
                    field(fields[0]).multiply(coefficientScale),
                    field(fields[1]).multiply(coefficientScale),
                )

            val actual =
                curve.deterministicDifference(
                    MontgomeryPoint.fromCoordinates(
                        Fp2P324,
                        left.x.multiply(leftScale),
                        left.z.multiply(leftScale),
                    ),
                    MontgomeryPoint.fromCoordinates(
                        Fp2P324,
                        right.x.multiply(rightScale),
                        right.z.multiply(rightScale),
                    ),
                )

            assertContentEquals(
                fields[16].hexToByteArray(),
                assertNotNull(actual.affineX()).toLittleEndian(),
            )
            for ((expected, value) in original.zip(listOf(left.x, left.z, right.x, right.z))) {
                assertContentEquals(expected, value.toLittleEndian())
            }
        }
    }

    @Test
    fun swappingPointsPreservesTheDeterministicChoice() {
        for (record in curveP324Vectors) {
            val fields = record.split(':')
            val curve =
                MontgomeryCurve.fromCoefficients(Fp2P324, field(fields[0]), field(fields[1]))

            val actual = curve.deterministicDifference(point(fields, 4), point(fields, 2))

            assertContentEquals(
                fields[16].hexToByteArray(),
                assertNotNull(actual.affineX()).toLittleEndian(),
            )
        }
    }

    @Test
    fun equalXCoordinatesAreRejectedWithAnExplicitReason() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val ordinary = MontgomeryPoint.fromCoordinates(Fp2P324, element(2), Fp2P324.one())
        val scaled = MontgomeryPoint.fromCoordinates(Fp2P324, element(6), element(3))
        for ((left, right) in
            listOf(
                ordinary to scaled,
                MontgomeryPoint.infinity(Fp2P324) to MontgomeryPoint.infinity(Fp2P324),
            )) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    curve.deterministicDifference(left, right)
                }

            assertEquals(
                "Deterministic difference requires distinct x-coordinates",
                failure.message,
            )
        }
    }

    @Test
    fun pointsWithANonsquareDiscriminantAreRejectedRatherThanInventingADifference() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val left = MontgomeryPoint.fromCoordinates(Fp2P324, element(2), Fp2P324.one())
        val right = MontgomeryPoint.fromCoordinates(Fp2P324, element(1, 1), Fp2P324.one())

        val failure =
            assertFailsWith<IllegalArgumentException> {
                curve.deterministicDifference(left, right)
            }

        assertEquals("Deterministic difference requires a square discriminant", failure.message)
    }

    @Test
    fun zeroDiscriminantsAndDistinctInfinityInputsHaveTheirUniqueGroupLawResult() {
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val ordinary = MontgomeryPoint.fromCoordinates(Fp2P324, element(2), Fp2P324.one())
        val torsion = MontgomeryPoint.fromCoordinates(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        val infinity = MontgomeryPoint.infinity(Fp2P324)
        val translated = MontgomeryPoint.fromCoordinates(Fp2P324, Fp2P324.one(), element(2))
        val cases =
            listOf(
                Triple(ordinary, torsion, translated),
                Triple(ordinary, infinity, ordinary),
                Triple(infinity, ordinary, ordinary),
                Triple(torsion, infinity, torsion),
                Triple(infinity, torsion, torsion),
            )
        for ((left, right, expected) in cases) {
            val actual = curve.deterministicDifference(left, right)

            assertTrue(actual.isEqualTo(expected), "unique zero-discriminant root")
        }
    }

    private fun element(real: Int, imaginary: Int = 0): Fp2P324 =
        Fp2P324.fromComponents(Fp324.fromInt(real), Fp324.fromInt(imaginary))

    private fun point(fields: List<String>, offset: Int): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(Fp2P324, field(fields[offset]), field(fields[offset + 1]))

    private fun field(encoded: String): Fp2P324 = Fp2P324.fromLittleEndian(encoded.hexToByteArray())
}
