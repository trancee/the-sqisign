// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.fp664_17Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Fp664P17Test {
    @Test
    fun zeroAndOneUseCanonicalEncodings() {
        val expectedOne = ByteArray(84).also { it[0] = 1 }

        assertContentEquals(ByteArray(84), Fp664P17.zero().toLittleEndian())
        assertContentEquals(expectedOne, Fp664P17.one().toLittleEndian())
    }

    @Test
    fun canonicalSignSelectionAndConditionalSwapUseBooleanControls() {
        val zero = Fp664P17.zero()
        val oneBytes = ByteArray(84).also { it[0] = 1 }
        val twoBytes = ByteArray(84).also { it[0] = 2 }
        val one = Fp664P17.fromLittleEndian(oneBytes)
        val two = Fp664P17.fromLittleEndian(twoBytes)

        assertFalse(Fp664P17.isNegative(zero))
        assertFalse(Fp664P17.isNegative(one))
        assertTrue(Fp664P17.isNegative(one.negate()))
        assertContentEquals(oneBytes, Fp664P17.select(one, two, false).toLittleEndian())
        assertContentEquals(twoBytes, Fp664P17.select(one, two, true).toLittleEndian())

        val unchanged = Fp664P17.conditionalSwap(one, two, false)
        val swapped = Fp664P17.conditionalSwap(one, two, true)
        assertContentEquals(oneBytes, unchanged.first.toLittleEndian())
        assertContentEquals(twoBytes, unchanged.second.toLittleEndian())
        assertContentEquals(twoBytes, swapped.first.toLittleEndian())
        assertContentEquals(oneBytes, swapped.second.toLittleEndian())
    }

    @Test
    fun encodingRejectsWrongWidthAndNoncanonicalRepresentatives() {
        val encoded = ByteArray(84)
        assertContentEquals(encoded, Fp664P17.fromLittleEndian(encoded).toLittleEndian())
        assertFailsWith<IllegalArgumentException> { Fp664P17.fromLittleEndian(ByteArray(83)) }
        assertFailsWith<IllegalArgumentException> {
            Fp664P17.fromLittleEndian(ParameterSet.NIST_V.primeBytes())
        }
    }

    @Test
    fun arithmeticMatchesIndependentPythonVectors() {
        for ((index, record) in fp664_17Vectors.withIndex()) {
            val fields = record.split(':')
            val left = Fp664P17.fromLittleEndian(fields[0].hexToByteArray())
            val right = Fp664P17.fromLittleEndian(fields[1].hexToByteArray())

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
}
