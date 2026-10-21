// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TorsionScalarOracleTest {
    @Test
    fun sumsMatchIndependentPythonIntegers() =
        assertBinaryRecords(3) { first, second ->
            first.add(second)
        }

    @Test
    fun differencesMatchIndependentPythonIntegers() =
        assertBinaryRecords(4) { first, second ->
            first.subtract(second)
        }

    @Test
    fun productsMatchIndependentPythonIntegers() =
        assertBinaryRecords(5) { first, second ->
            first.multiply(second)
        }

    @Test
    fun additiveInversesMatchIndependentPythonIntegers() {
        for (record in TorsionRingVectors.scalars) {
            val fields = record.split(":")
            val first =
                TorsionScalar.fromLittleEndian(fields[0].toInt(), fields[1].hexToByteArray())

            val result = first.negate()

            assertContentEquals(fields[6].hexToByteArray(), result.toLittleEndian(), record)
            assertContentEquals(fields[1].hexToByteArray(), first.toLittleEndian(), record)
        }
    }

    @Test
    fun orderingMatchesIndependentPythonIntegers() {
        for (record in TorsionRingVectors.scalars) {
            val fields = record.split(":")
            val exponent = fields[0].toInt()
            val first = TorsionScalar.fromLittleEndian(exponent, fields[1].hexToByteArray())
            val second = TorsionScalar.fromLittleEndian(exponent, fields[2].hexToByteArray())

            val result = first.compareTo(second)

            assertEquals(fields[7].toInt(), result, record)
        }
    }

    @Test
    fun multiplicativeInversesMatchIndependentPythonIntegers() {
        for (record in TorsionRingVectors.scalars) {
            val fields = record.split(":")
            if (fields[8] == "-") continue
            val first =
                TorsionScalar.fromLittleEndian(fields[0].toInt(), fields[1].hexToByteArray())

            val result = first.inverse()

            assertContentEquals(fields[8].hexToByteArray(), result.toLittleEndian(), record)
            assertContentEquals(fields[1].hexToByteArray(), first.toLittleEndian(), record)
        }
    }

    @Test
    fun everyEvenOracleInputFailsWithTheSpecifiedReason() {
        for (record in TorsionRingVectors.scalars) {
            val fields = record.split(":")
            if (fields[8] != "-") continue
            val first =
                TorsionScalar.fromLittleEndian(fields[0].toInt(), fields[1].hexToByteArray())

            val failure = assertFailsWith<IllegalArgumentException> { first.inverse() }

            assertEquals("Torsion inverse requires an odd residue", failure.message, record)
        }
    }

    private fun assertBinaryRecords(
        resultIndex: Int,
        operation: (TorsionScalar, TorsionScalar) -> TorsionScalar,
    ) {
        for (record in TorsionRingVectors.scalars) {
            val fields = record.split(":")
            val exponent = fields[0].toInt()
            val first = TorsionScalar.fromLittleEndian(exponent, fields[1].hexToByteArray())
            val second = TorsionScalar.fromLittleEndian(exponent, fields[2].hexToByteArray())

            val result = operation(first, second)

            assertContentEquals(
                fields[resultIndex].hexToByteArray(),
                result.toLittleEndian(),
                record,
            )
            assertContentEquals(fields[1].hexToByteArray(), first.toLittleEndian(), record)
            assertContentEquals(fields[2].hexToByteArray(), second.toLittleEndian(), record)
        }
    }
}
