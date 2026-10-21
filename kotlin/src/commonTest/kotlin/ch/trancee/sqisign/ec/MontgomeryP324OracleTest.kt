// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MontgomeryP324OracleTest {
    @Test
    fun doublingMatchesIndependentAffineGroupLawAndPinnedC() {
        for ((index, record) in curveP324Vectors.withIndex()) {
            val fields = record.split(':')
            val curve = curve(fields)
            val point = point(fields, 2)

            val actual = curve.double(point)

            assertAffine(fields[9], actual, "double case $index")
        }
    }

    @Test
    fun differentialAdditionMatchesIndependentAffineGroupLawAndPinnedC() {
        for ((index, record) in curveP324Vectors.withIndex()) {
            val fields = record.split(':')
            val left = point(fields, 2)
            val right = point(fields, 4)
            val difference = point(fields, 6)

            val actual = MontgomeryPoint.differentialAdd(left, right, difference)

            assertAffine(fields[10], actual, "addition case $index")
        }
    }

    @Test
    fun projectiveCurvesNormalizeToTheIndependentAffineCoefficient() {
        for ((index, record) in curveP324Vectors.withIndex()) {
            val fields = record.split(':')
            val curve = curve(fields)

            val actual = curve.montgomeryCoefficient()

            assertContentEquals(
                fields[12].hexToByteArray(),
                actual.toLittleEndian(),
                "curve $index",
            )
        }
    }

    @Test
    fun ladderMatchesIndependentAffineMultiplicationForShortScalars() {
        for (index in 0..7) assertScalar(index)
    }

    @Test
    fun ladderPreservesBitsAboveTheChallengeWidth() {
        assertScalar(8)
    }

    @Test
    fun ladderPreservesTheHighestScalarBit() {
        assertScalar(9)
    }

    @Test
    fun ladderPreservesTheMaximumScalar() {
        assertScalar(10)
    }

    @Test
    fun ladderPreservesAlternatingScalarBits() {
        assertScalar(11)
    }

    private fun assertScalar(index: Int) {
        val fields = curveP324Vectors[index].split(':')
        val curve = curve(fields)
        val point = point(fields, 2)
        val scalar = fields[8].hexToByteArray()
        val originalX = point.x.toLittleEndian()
        val originalZ = point.z.toLittleEndian()

        val actual = curve.multiply(point, scalar)

        assertAffine(fields[11], actual, "multiply case $index")
        assertContentEquals(originalX, point.x.toLittleEndian(), "input x case $index")
        assertContentEquals(originalZ, point.z.toLittleEndian(), "input z case $index")
        assertContentEquals(
            fields[8].hexToByteArray(),
            scalar,
            "scalar case $index",
        )
    }

    private fun curve(fields: List<String>): MontgomeryCurve<Fp2P324> =
        MontgomeryCurve.fromCoefficients(Fp2P324, field(fields[0]), field(fields[1]))

    private fun point(fields: List<String>, offset: Int): MontgomeryPoint<Fp2P324> =
        MontgomeryPoint.fromCoordinates(Fp2P324, field(fields[offset]), field(fields[offset + 1]))

    private fun field(encoded: String): Fp2P324 = Fp2P324.fromLittleEndian(encoded.hexToByteArray())

    private fun assertAffine(expected: String, actual: MontgomeryPoint<Fp2P324>, context: String) {
        if (expected == "-") {
            assertTrue(actual.isInfinity(), context)
        } else {
            assertContentEquals(
                expected.hexToByteArray(),
                assertNotNull(actual.affineX(), context).toLittleEndian(),
                context,
            )
        }
    }
}
