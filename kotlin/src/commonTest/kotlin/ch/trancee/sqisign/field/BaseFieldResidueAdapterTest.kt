// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ec.baseResidueP324Vectors
import ch.trancee.sqisign.ec.baseResidueP500Vectors
import ch.trancee.sqisign.ec.baseResidueP664Vectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseFieldResidueAdapterTest {
    @Test fun p324MatchesIndependentBaseResidues() = assertResidues(Fp2P324, baseResidueP324Vectors)

    @Test fun p500MatchesIndependentBaseResidues() = assertResidues(Fp2P500, baseResidueP500Vectors)

    @Test fun p664MatchesIndependentBaseResidues() = assertResidues(Fp2P664, baseResidueP664Vectors)

    @Test
    fun extensionSquaresDoNotImplyBaseFieldSquares() {
        val negativeOne = Fp2P324.one().negate()

        val actual = Fp2P324.isSquareInBaseField(negativeOne)

        assertFalse(actual, "-1 is a base-field nonresidue because p=3 mod 4")
        assertTrue(negativeOne.isSquare(), "i is its extension-field square root")
    }

    @Test
    fun nonBaseInputsAreRejectedAtEveryParameter() {
        assertNonBaseRejected(Fp2P324)
        assertNonBaseRejected(Fp2P500)
        assertNonBaseRejected(Fp2P664)
    }

    private fun <Element : QuadraticFieldElement<Element>> assertResidues(
        field: QuadraticField<Element>,
        records: List<String>,
    ) {
        for ((index, record) in records.withIndex()) {
            val parts = record.split(':')
            val value = field.fromLittleEndian(parts[0].hexToByteArray())

            val actual = field.isSquareInBaseField(value)

            assertEquals(
                parts[1] == "1",
                actual,
                "${field.parameters.identifier} base residue $index",
            )
        }
    }

    private fun <Element : QuadraticFieldElement<Element>> assertNonBaseRejected(
        field: QuadraticField<Element>,
    ) {
        val value =
            field.fromLittleEndian(
                ByteArray(field.parameters.extensionFieldBytes).also {
                    it[field.parameters.fieldBytes] = 1
                },
            )

        val failure = assertFailsWith<IllegalArgumentException> { field.isSquareInBaseField(value) }

        assertEquals("Base-field residue test requires zero imaginary component", failure.message)
    }
}
