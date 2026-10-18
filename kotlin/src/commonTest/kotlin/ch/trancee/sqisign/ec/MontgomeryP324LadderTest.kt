// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MontgomeryP324LadderTest {
    @Test
    fun biscalarLadderMatchesIndependentAffineMultiplesAndTheirSum() {
        assertBiscalar(3)
    }

    @Test
    fun biscalarLadderPreservesZeroScalarsAndAllParityCombinations() {
        for (index in 0..7) assertBiscalar(index)
        assertBiscalar(14)
        assertBiscalar(15)
    }

    @Test
    fun biscalarLadderPreservesUnequalFullWidthScalars() {
        assertBiscalar(8)
    }

    @Test
    fun biscalarLadderPreservesTheHighestBitsOfBothScalars() {
        assertBiscalar(9)
    }

    @Test
    fun biscalarLadderPreservesBothMaximumScalars() {
        assertBiscalar(10)
    }

    @Test
    fun biscalarLadderPreservesAlternatingScalarBits() {
        assertBiscalar(11)
    }

    @Test
    fun threePointLadderMatchesIndependentAffineAdditionOfAMultiple() {
        assertThreePoint(3)
    }

    @Test
    fun threePointLadderPreservesZeroAndShortScalars() {
        for (index in 0..7) assertThreePoint(index)
    }

    @Test
    fun threePointLadderPreservesBitsAboveTheChallengeWidth() {
        assertThreePoint(8)
    }

    @Test
    fun threePointLadderPreservesTheHighestBit() {
        assertThreePoint(9)
    }

    @Test
    fun threePointLadderPreservesTheMaximumScalar() {
        assertThreePoint(10)
    }

    @Test
    fun threePointLadderPreservesAlternatingScalarBits() {
        assertThreePoint(11)
    }

    @Test
    fun threePointLadderRejectsExceptionalInputCoordinatesEvenForZeroScalar() {
        val fields = curveP324Vectors[0].split(':')
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(fields[0]), field(fields[1]))
        val regular = listOf(point(fields, 2), point(fields, 4), point(fields, 6))
        val zeroPoint = MontgomeryPoint.fromCoordinates(Fp2P324, Fp2P324.zero(), Fp2P324.one())
        for (exceptional in listOf(MontgomeryPoint.infinity(Fp2P324), zeroPoint)) {
            for (index in regular.indices) {
                val inputs = regular.toMutableList().also { it[index] = exceptional }

                val failure =
                    assertFailsWith<IllegalArgumentException> {
                        curve.threePointMultiply(
                            inputs[0],
                            inputs[1],
                            inputs[2],
                            ByteArray(41),
                        )
                    }

                assertEquals("Three-point ladder requires finite nonzero operands", failure.message)
            }
        }
    }

    private fun assertThreePoint(index: Int) {
        val fields = curveP324Vectors[index].split(':')
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(fields[0]), field(fields[1]))
        val left = point(fields, 2)
        val right = point(fields, 4)
        val difference = point(fields, 6)
        val scalar = fields[8].hexToByteArray()

        val actual = curve.threePointMultiply(left, right, difference, scalar)

        assertInputsUnchanged(fields, listOf(left, right, difference))
        assertContentEquals(fields[8].hexToByteArray(), scalar)
        if (fields[13] == "-") assertTrue(actual.isInfinity(), "three-point $index")
        else
            assertContentEquals(
                fields[13].hexToByteArray(),
                assertNotNull(actual.affineX()).toLittleEndian(),
                "three-point $index",
            )
    }

    private fun assertBiscalar(index: Int) {
        val fields = curveP324Vectors[index].split(':')
        val curve = MontgomeryCurve.fromCoefficients(Fp2P324, field(fields[0]), field(fields[1]))
        val left = point(fields, 2)
        val right = point(fields, 4)
        val difference = point(fields, 6)
        val scalarLeft = fields[8].hexToByteArray()
        val scalarRight = fields[14].hexToByteArray()

        val actual = curve.biscalarMultiply(left, right, difference, scalarLeft, scalarRight)

        assertInputsUnchanged(fields, listOf(left, right, difference))
        assertContentEquals(fields[8].hexToByteArray(), scalarLeft)
        assertContentEquals(fields[14].hexToByteArray(), scalarRight)
        if (fields[15] == "-") assertTrue(actual.isInfinity(), "biscalar $index")
        else
            assertContentEquals(
                fields[15].hexToByteArray(),
                assertNotNull(actual.affineX()).toLittleEndian(),
                "biscalar $index",
            )
    }

    private fun assertInputsUnchanged(
        fields: List<String>,
        points: List<MontgomeryPoint<Fp2P324>>,
    ) {
        for ((index, point) in points.withIndex()) {
            val offset = 2 + 2 * index
            assertContentEquals(fields[offset].hexToByteArray(), point.x.toLittleEndian())
            assertContentEquals(fields[offset + 1].hexToByteArray(), point.z.toLittleEndian())
        }
    }

    private fun point(fields: List<String>, offset: Int): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(Fp2P324, field(fields[offset]), field(fields[offset + 1]))

    private fun field(encoded: String): Fp2P324 = Fp2P324.fromLittleEndian(encoded.hexToByteArray())
}
