// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.fp500_27NonlinearVectors
import ch.trancee.sqisign.fp500_27Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Fp500P27Test {
    @Test
    fun zeroAndOneUseCanonicalEncodings() {
        val expectedOne = ByteArray(64).also { it[0] = 1 }

        assertContentEquals(ByteArray(64), Fp500P27.zero().toLittleEndian())
        assertContentEquals(expectedOne, Fp500P27.one().toLittleEndian())
    }

    @Test
    fun canonicalSignSelectionAndConditionalSwapUseBooleanControls() {
        val zero = Fp500P27.zero()
        val oneBytes = ByteArray(64).also { it[0] = 1 }
        val twoBytes = ByteArray(64).also { it[0] = 2 }
        val one = Fp500P27.fromLittleEndian(oneBytes)
        val two = Fp500P27.fromLittleEndian(twoBytes)

        assertFalse(Fp500P27.isNegative(zero))
        assertFalse(Fp500P27.isNegative(one))
        assertTrue(Fp500P27.isNegative(one.negate()))
        assertContentEquals(oneBytes, Fp500P27.select(one, two, false).toLittleEndian())
        assertContentEquals(twoBytes, Fp500P27.select(one, two, true).toLittleEndian())

        val unchanged = Fp500P27.conditionalSwap(one, two, false)
        val swapped = Fp500P27.conditionalSwap(one, two, true)
        assertContentEquals(oneBytes, unchanged.first.toLittleEndian())
        assertContentEquals(twoBytes, unchanged.second.toLittleEndian())
        assertContentEquals(twoBytes, swapped.first.toLittleEndian())
        assertContentEquals(oneBytes, swapped.second.toLittleEndian())
    }

    @Test
    fun encodingRejectsWrongWidthAndNoncanonicalRepresentatives() {
        val encoded = ByteArray(64)
        assertContentEquals(encoded, Fp500P27.fromLittleEndian(encoded).toLittleEndian())
        assertFailsWith<IllegalArgumentException> { Fp500P27.fromLittleEndian(ByteArray(63)) }

        val nonCanonical = ParameterSet.NIST_III.primeBytes()
        assertFailsWith<IllegalArgumentException> {
            Fp500P27.fromLittleEndian(nonCanonical)
        }
    }

    @Test
    fun arithmeticMatchesIndependentPythonVectors() {
        for ((index, record) in fp500_27Vectors.withIndex()) {
            val fields = record.split(':')
            val left = Fp500P27.fromLittleEndian(fields[0].hexToByteArray())
            val right = Fp500P27.fromLittleEndian(fields[1].hexToByteArray())

            assertContentEquals(
                fields[2].hexToByteArray(),
                left.add(right).toLittleEndian(),
                "add $index",
            )
            assertContentEquals(
                fields[3].hexToByteArray(),
                left.subtract(right).toLittleEndian(),
                "subtract $index",
            )
            assertContentEquals(
                fields[4].hexToByteArray(),
                left.multiply(right).toLittleEndian(),
                "multiply $index",
            )
            assertContentEquals(
                fields[5].hexToByteArray(),
                left.square().toLittleEndian(),
                "square $index",
            )
        }
    }

    @Test
    fun inverseResidueAndCanonicalRootMatchIndependentPythonVectorsBatch0() =
        assertNonlinearVectors(0)

    @Test
    fun inverseResidueAndCanonicalRootMatchIndependentPythonVectorsBatch1() =
        assertNonlinearVectors(1)

    @Test
    fun inverseResidueAndCanonicalRootMatchIndependentPythonVectorsBatch2() =
        assertNonlinearVectors(2)

    private fun assertNonlinearVectors(batch: Int) {
        for (index in batch until fp500_27NonlinearVectors.size step 3) {
            val record = fp500_27NonlinearVectors[index]
            val fields = record.split(':')
            val value = Fp500P27.fromLittleEndian(fields[0].hexToByteArray())

            if (fields[1] == "-") {
                assertFailsWith<IllegalArgumentException>("inverse $index") { value.inverse() }
            } else {
                assertContentEquals(
                    fields[1].hexToByteArray(),
                    value.inverse().toLittleEndian(),
                    "inverse $index",
                )
            }
            if (fields[3] == "-") {
                assertEquals("0", fields[2], "square status $index")
                assertFalse(value.isSquare(), "square $index")
                assertNull(value.squareRoot(), "root $index")
            } else {
                assertEquals("1", fields[2], "square status $index")
                assertTrue(value.isSquare(), "square $index")
                assertContentEquals(
                    fields[3].hexToByteArray(),
                    assertNotNull(value.squareRoot(), "root $index").toLittleEndian(),
                    "root $index",
                )
            }
        }
    }
}
