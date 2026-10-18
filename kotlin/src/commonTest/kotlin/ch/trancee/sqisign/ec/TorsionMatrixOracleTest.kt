// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TorsionMatrixOracleTest {
    @Test
    fun determinantsMatchIndependentPythonIntegers() {
        for (record in TorsionRingVectors.matrices) {
            val fields = record.split(":")
            val matrix = decodeMatrix(fields)

            val determinant = matrix.determinant()

            assertContentEquals(fields[5].hexToByteArray(), determinant.toLittleEndian(), record)
        }
    }

    @Test
    fun globalSignsMatchIndependentPythonLexicographicOrdering() {
        for (record in TorsionRingVectors.matrices) {
            val fields = record.split(":")
            val matrix = decodeMatrix(fields)

            val normalized = matrix.normalized()

            assertEquals(fields.subList(6, 10), encodeMatrix(normalized), record)
            assertEquals(fields.subList(1, 5), encodeMatrix(matrix), record)
            assertEquals(fields[0].toInt(), normalized.exponent, record)
        }
    }

    @Test
    fun invertibleMatricesMatchIndependentPythonAdjugates() {
        for (record in TorsionRingVectors.matrices) {
            val fields = record.split(":")
            if (fields[10] == "-") continue
            val matrix = decodeMatrix(fields)

            val inverse = matrix.inverse()

            assertEquals(fields.subList(10, 14), encodeMatrix(inverse), record)
            assertEquals(fields.subList(1, 5), encodeMatrix(matrix), record)
        }
    }

    @Test
    fun evenDeterminantsFailBeforeReturningAnInverse() {
        for (record in TorsionRingVectors.matrices) {
            val fields = record.split(":")
            if (fields[10] != "-") continue
            val matrix = decodeMatrix(fields)

            val failure = assertFailsWith<IllegalArgumentException> { matrix.inverse() }

            assertEquals("Torsion matrix must have odd determinant", failure.message, record)
        }
    }

    private fun decodeMatrix(fields: List<String>): TorsionMatrix {
        val entries =
            fields.subList(1, 5).map {
                TorsionScalar.fromLittleEndian(fields[0].toInt(), it.hexToByteArray())
            }
        return TorsionMatrix.of(entries[0], entries[1], entries[2], entries[3])
    }

    private fun encodeMatrix(matrix: TorsionMatrix): List<String> =
        matrix.rowMajor().map { it.toLittleEndian().toHexString() }
}
