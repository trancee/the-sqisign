// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TorsionMatrixTest {
    @Test
    fun normalizationChoosesOneGlobalSignAfterSelfNegativeEntries() {
        val entries =
            listOf("0001", "0000", "0101", "0100").map {
                TorsionScalar.fromLittleEndian(9, it.hexToByteArray())
            }
        val matrix = TorsionMatrix.of(entries[0], entries[1], entries[2], entries[3])

        val normalized = matrix.normalized()

        assertEquals(
            listOf("0001", "0000", "ff00", "ff01"),
            normalized.rowMajor().map { it.toLittleEndian().toHexString() },
        )
    }

    @Test
    fun inversePreservesTheRowMajorColumnActionConvention() {
        val entries =
            listOf("0300", "0400", "0200", "0300").map {
                TorsionScalar.fromLittleEndian(9, it.hexToByteArray())
            }
        val matrix = TorsionMatrix.of(entries[0], entries[1], entries[2], entries[3])

        val inverse = matrix.inverse()

        assertEquals(
            listOf("0300", "fc01", "fe01", "0300"),
            inverse.rowMajor().map { it.toLittleEndian().toHexString() },
        )
    }

    @Test
    fun eachMatrixEntryMustUseTheSameRing() {
        for (position in 1..3) {
            val entries =
                List(4) {
                    TorsionScalar.fromLittleEndian(if (it == position) 2 else 1, byteArrayOf(1))
                }

            val failure =
                assertFailsWith<IllegalArgumentException> {
                    TorsionMatrix.of(entries[0], entries[1], entries[2], entries[3])
                }

            assertEquals("Torsion matrix entries must have the same exponent", failure.message)
        }
    }
}
