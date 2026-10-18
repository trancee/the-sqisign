// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ec.curveReferenceRootVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Fp2P324ReferenceRootTest {
    @Test
    fun referenceRootPreservesTheSpecificationSignRatherThanCanonicalizing() {
        val input = Fp2P324.fromComponents(Fp324.fromInt(5).negate(), Fp324.fromInt(12))
        val expected = Fp2P324.fromComponents(Fp324.fromInt(2), Fp324.fromInt(3)).negate()

        val actual = Fp2P324.squareRootWithReferenceSign(input)

        assertContentEquals(expected.toLittleEndian(), assertNotNull(actual).toLittleEndian())
    }

    @Test
    fun referenceRootsMatchExactPythonAndPinnedCSignsIncludingNonResidues() {
        for ((index, record) in curveReferenceRootVectors.withIndex()) {
            val fields = record.split(':')
            val input = Fp2P324.fromLittleEndian(fields[0].hexToByteArray())

            val actual = Fp2P324.squareRootWithReferenceSign(input)

            assertContentEquals(fields[0].hexToByteArray(), input.toLittleEndian(), "input $index")
            if (fields[1] == "-") {
                assertNull(actual, "non-residue $index")
            } else {
                assertContentEquals(
                    fields[1].hexToByteArray(),
                    assertNotNull(actual, "root $index").toLittleEndian(),
                    "reference sign $index",
                )
            }
        }
    }
}
