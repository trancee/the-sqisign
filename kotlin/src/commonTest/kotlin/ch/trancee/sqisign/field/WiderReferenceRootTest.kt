// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ec.curveP500ReferenceRootVectors
import ch.trancee.sqisign.ec.curveP664ReferenceRootVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WiderReferenceRootTest {
    @Test
    fun levelThreeSpecialRootsMatchTheExactPythonAndCSign() =
        assertRoots(Fp2P500, curveP500ReferenceRootVectors, 0..5)

    @Test
    fun levelThreeIndependentRootsAndNonresiduesMatchPythonAndCFirstBatch() =
        assertRoots(Fp2P500, curveP500ReferenceRootVectors, 6..11)

    @Test
    fun levelThreeIndependentRootsAndNonresiduesMatchPythonAndCSecondBatch() =
        assertRoots(Fp2P500, curveP500ReferenceRootVectors, 12..17)

    @Test
    fun levelThreeSquaredInputsRetainTheReferenceSignFirstBatch() =
        assertRoots(Fp2P500, curveP500ReferenceRootVectors, 18..23)

    @Test
    fun levelThreeSquaredInputsRetainTheReferenceSignSecondBatch() =
        assertRoots(Fp2P500, curveP500ReferenceRootVectors, 24..29)

    @Test
    fun levelFiveSpecialRootsMatchTheExactPythonAndCSign() =
        assertRoots(Fp2P664, curveP664ReferenceRootVectors, 0..5)

    @Test
    fun levelFiveIndependentRootsAndNonresiduesMatchPythonAndCFirstBatch() =
        assertRoots(Fp2P664, curveP664ReferenceRootVectors, 6..11)

    @Test
    fun levelFiveIndependentRootsAndNonresiduesMatchPythonAndCSecondBatch() =
        assertRoots(Fp2P664, curveP664ReferenceRootVectors, 12..17)

    @Test
    fun levelFiveSquaredInputsRetainTheReferenceSignFirstBatch() =
        assertRoots(Fp2P664, curveP664ReferenceRootVectors, 18..23)

    @Test
    fun levelFiveSquaredInputsRetainTheReferenceSignSecondBatch() =
        assertRoots(Fp2P664, curveP664ReferenceRootVectors, 24..29)

    @Test fun levelThreeConjugationNegatesOnlyTheImaginaryComponent() = assertConjugate(Fp2P500)

    @Test fun levelFiveConjugationNegatesOnlyTheImaginaryComponent() = assertConjugate(Fp2P664)

    private fun <Element : QuadraticFieldElement<Element>> assertRoots(
        field: QuadraticField<Element>,
        records: List<String>,
        indices: IntRange,
    ) {
        for (index in indices) {
            val parts = records[index].split(':')
            val input = field.fromLittleEndian(parts[0].hexToByteArray())

            val actual = field.squareRootWithReferenceSign(input)

            if (parts[1] == "-") assertNull(actual, "${field.parameters.identifier} root $index")
            else {
                assertContentEquals(
                    parts[1].hexToByteArray(),
                    assertNotNull(actual).toLittleEndian(),
                    "${field.parameters.identifier} root $index",
                )
            }
            assertContentEquals(parts[0].hexToByteArray(), input.toLittleEndian())
        }
    }

    private fun <Element : QuadraticFieldElement<Element>> assertConjugate(
        field: QuadraticField<Element>,
    ) {
        val width = field.parameters.fieldBytes
        val encoded =
            ByteArray(2 * width).also {
                it[0] = 5
                it[width] = 12
            }
        val input = field.fromLittleEndian(encoded)
        val negativeTwelve = field.parameters.primeBytes().also { it[0] = 0xf3.toByte() }
        val expected = ByteArray(width).also { it[0] = 5 } + negativeTwelve

        val actual = field.conjugate(input)

        assertContentEquals(expected, actual.toLittleEndian())
        assertContentEquals(encoded, input.toLittleEndian())
    }
}
