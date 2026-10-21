// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BasisHintTest {
    @Test
    fun theLargestDirectHintRetainsBothTheCounterAndResidueBit() {
        val hint = BasisHint.fromSearchIndex(byteArrayOf(127), true)

        assertEquals(0xff.toByte(), hint.encoded)
        assertEquals(127, hint.searchIndex)
        assertTrue(hint.claimsSquareCoefficient)
        assertFalse(hint.requiresRegeneration)
    }

    @Test
    fun everyWireByteHasASyntacticInterpretationWithoutLosingTheHighBit() {
        for (encoded in 0..255) {
            val hint = BasisHint.fromEncoded(encoded.toByte())

            assertEquals(encoded.toByte(), hint.encoded)
            assertEquals(encoded / 2, hint.searchIndex)
            assertEquals(encoded % 2 == 1, hint.claimsSquareCoefficient)
            assertEquals(encoded == 0, hint.requiresRegeneration)
        }
    }

    @Test
    fun directIndicesUseTheSpecifiedLowResidueBit() {
        val cases =
            listOf(
                Triple(1, false, 2),
                Triple(1, true, 3),
                Triple(64, false, 128),
                Triple(64, true, 129),
                Triple(127, false, 254),
                Triple(127, true, 255),
            )
        for ((index, square, expected) in cases) {
            val hint = BasisHint.fromSearchIndex(byteArrayOf(index.toByte()), square)

            assertEquals(expected.toByte(), hint.encoded)
        }
    }

    @Test
    fun everyIndexAtOrBeyond128UsesZeroEvenForASquareCoefficient() {
        val indices =
            listOf(
                byteArrayOf(128.toByte()),
                byteArrayOf(255.toByte()),
                byteArrayOf(0, 1),
                byteArrayOf(127, 1),
                byteArrayOf(1, 0, 0, 1),
                ByteArray(84) { 0xff.toByte() },
            )
        for (index in indices) {
            for (square in listOf(false, true)) {
                val hint = BasisHint.fromSearchIndex(index, square)

                assertEquals(0.toByte(), hint.encoded)
                assertTrue(hint.requiresRegeneration)
            }
        }
    }

    @Test
    fun paddedIndicesRemainDirectAndDoNotAliasCallerStorage() {
        val index = byteArrayOf(127, 0, 0)

        val hint = BasisHint.fromSearchIndex(index, true)

        index[0] = 0
        assertEquals(0xff.toByte(), hint.encoded)
        assertEquals(127, hint.searchIndex)
    }

    @Test
    fun emptyAndZeroSearchIndicesAreNotGeneratedHints() {
        for (index in listOf(byteArrayOf(), byteArrayOf(0), ByteArray(84))) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    BasisHint.fromSearchIndex(index, true)
                }

            assertEquals(
                "Basis search index must be positive unsigned little-endian bytes",
                failure.message,
            )
        }
    }
}
