// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.signedVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Int328VectorTest {
    @Test
    fun comparisonsMatchIndependentSignedVectors() {
        for ((index, record) in signedVectors.withIndex()) {
            val fields = record.split(':')
            val left = Int328.fromLittleEndian(fields[0].hexToByteArray())
            val right = Int328.fromLittleEndian(fields[1].hexToByteArray())

            val comparison = left.compareTo(right)

            assertEquals(fields[2].toInt(), comparison, "Comparison case $index")
            assertContentEquals(
                fields[0].hexToByteArray(),
                left.toLittleEndian(),
                "Left case $index",
            )
            assertContentEquals(
                fields[1].hexToByteArray(),
                right.toLittleEndian(),
                "Right case $index",
            )
        }
    }

    @Test
    fun sumsMatchIndependentSignedVectorsOrRejectOverflow() {
        for ((index, record) in signedVectors.withIndex()) {
            val fields = record.split(':')
            val leftBytes = fields[0].hexToByteArray()
            val rightBytes = fields[1].hexToByteArray()
            val left = Int328.fromLittleEndian(leftBytes)
            val right = Int328.fromLittleEndian(rightBytes)
            if (fields[3] == "overflow") {
                val error = assertFailsWith<ArithmeticException> { left.add(right) }
                assertEquals(
                    "Signed 328-bit addition overflow",
                    error.message,
                    "Overflow case $index",
                )
            } else {
                val sum = left.add(right)

                assertContentEquals(
                    fields[3].hexToByteArray(),
                    sum.toLittleEndian(),
                    "Sum case $index",
                )
            }
            assertContentEquals(leftBytes, left.toLittleEndian(), "Left case $index")
            assertContentEquals(rightBytes, right.toLittleEndian(), "Right case $index")
        }
    }

    @Test
    fun differencesMatchIndependentSignedVectorsOrRejectOverflow() {
        for ((index, record) in signedVectors.withIndex()) {
            val fields = record.split(':')
            val leftBytes = fields[0].hexToByteArray()
            val rightBytes = fields[1].hexToByteArray()
            val left = Int328.fromLittleEndian(leftBytes)
            val right = Int328.fromLittleEndian(rightBytes)
            if (fields[4] == "overflow") {
                val error = assertFailsWith<ArithmeticException> { left.subtract(right) }
                assertEquals(
                    "Signed 328-bit subtraction overflow",
                    error.message,
                    "Overflow case $index",
                )
            } else {
                val difference = left.subtract(right)

                assertContentEquals(
                    fields[4].hexToByteArray(),
                    difference.toLittleEndian(),
                    "Difference case $index",
                )
            }
            assertContentEquals(leftBytes, left.toLittleEndian(), "Left case $index")
            assertContentEquals(rightBytes, right.toLittleEndian(), "Right case $index")
        }
    }
}
