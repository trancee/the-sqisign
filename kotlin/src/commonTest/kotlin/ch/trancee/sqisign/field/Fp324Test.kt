// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.fp324NonlinearVectors
import ch.trancee.sqisign.fp324Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Fp324Test {
    @Test
    fun canonicalEncodingAcceptsTheLargestResidueAndRejectsTheModulus() {
        val modulus = ParameterSet.NIST_I.primeBytes()
        val largest = modulus.copyOf()
        decrementLittleEndian(largest)
        val input = largest.copyOf()
        val value = Fp324.fromLittleEndian(input)
        input.fill(0)
        val output = value.toLittleEndian()

        assertContentEquals(largest, output)
        output.fill(0)
        assertContentEquals(largest, value.toLittleEndian())
        assertFailsWith<IllegalArgumentException> { Fp324.fromLittleEndian(modulus) }
        assertFailsWith<IllegalArgumentException> { Fp324.fromLittleEndian(ByteArray(40)) }
        assertFailsWith<IllegalArgumentException> { Fp324.fromInt(-1) }
    }

    @Test
    fun additionSubtractionAndNegationReduceToCanonicalResidues() {
        val modulus = ParameterSet.NIST_I.primeBytes()
        val largest = modulus.copyOf()
        decrementLittleEndian(largest)
        val largestResidue = Fp324.fromLittleEndian(largest)
        val zero = Fp324.fromInt(0)
        val one = Fp324.fromInt(1)

        assertContentEquals(zero.toLittleEndian(), largestResidue.add(one).toLittleEndian())
        assertContentEquals(
            Fp324.fromInt(2).toLittleEndian(),
            one.add(one).toLittleEndian(),
        )
        assertContentEquals(
            largestResidue.toLittleEndian(),
            zero.subtract(one).toLittleEndian(),
        )
        assertContentEquals(zero.toLittleEndian(), zero.negate().toLittleEndian())
        assertContentEquals(one.toLittleEndian(), one.negate().negate().toLittleEndian())
    }

    @Test
    fun multiplicationAndSquaringMatchSmallIntegerProducts() {
        val two = Fp324.fromInt(2)
        val three = Fp324.fromInt(3)

        assertContentEquals(Fp324.fromInt(6).toLittleEndian(), two.multiply(three).toLittleEndian())
        assertContentEquals(Fp324.fromInt(4).toLittleEndian(), two.square().toLittleEndian())
    }

    @Test
    fun canonicalSignSelectionAndConditionalSwapUseBooleanControls() {
        val zero = Fp324.zero()
        val one = Fp324.one()
        val two = Fp324.fromInt(2)

        assertFalse(Fp324.isNegative(zero))
        assertFalse(Fp324.isNegative(one))
        assertTrue(Fp324.isNegative(one.negate()))
        assertContentEquals(one.toLittleEndian(), Fp324.select(one, two, false).toLittleEndian())
        assertContentEquals(two.toLittleEndian(), Fp324.select(one, two, true).toLittleEndian())

        val unchanged = Fp324.conditionalSwap(one, two, false)
        val swapped = Fp324.conditionalSwap(one, two, true)
        assertContentEquals(one.toLittleEndian(), unchanged.first.toLittleEndian())
        assertContentEquals(two.toLittleEndian(), unchanged.second.toLittleEndian())
        assertContentEquals(two.toLittleEndian(), swapped.first.toLittleEndian())
        assertContentEquals(one.toLittleEndian(), swapped.second.toLittleEndian())
    }

    @Test
    fun fieldOperationsMatchIndependentPythonAndPinnedCModularVectors() {
        for ((index, record) in fp324Vectors.withIndex()) {
            val fields = record.split(':')
            val left = Fp324.fromLittleEndian(fields[0].hexToByteArray())
            val right = Fp324.fromLittleEndian(fields[1].hexToByteArray())

            assertContentEquals(
                fields[2].hexToByteArray(),
                left.add(right).toLittleEndian(),
                "addition case $index",
            )
            assertContentEquals(
                fields[3].hexToByteArray(),
                left.subtract(right).toLittleEndian(),
                "subtraction case $index",
            )
            assertContentEquals(
                fields[4].hexToByteArray(),
                left.multiply(right).toLittleEndian(),
                "multiplication case $index",
            )
        }
    }

    @Test
    fun inversionReturnsMultiplicativeIdentityAndRejectsZero() {
        val two = Fp324.fromInt(2)

        assertContentEquals(
            Fp324.one().toLittleEndian(),
            two.multiply(two.inverse()).toLittleEndian(),
        )
        assertFailsWith<IllegalArgumentException> { Fp324.zero().inverse() }
    }

    @Test
    fun squareRootReturnsTheSmallerRootAndRejectsNonResidues() {
        val four = Fp324.fromInt(4)
        val root = assertNotNull(four.squareRoot())

        assertContentEquals(Fp324.fromInt(2).toLittleEndian(), root.toLittleEndian())
        assertContentEquals(four.toLittleEndian(), root.square().toLittleEndian())
        assertNull(Fp324.fromInt(5).squareRoot())
        assertContentEquals(
            Fp324.zero().toLittleEndian(),
            assertNotNull(Fp324.zero().squareRoot()).toLittleEndian(),
        )
    }

    @Test
    fun inverseResidueAndCanonicalRootMatchIndependentVectors() {
        for ((index, record) in fp324NonlinearVectors.withIndex()) {
            val fields = record.split(':')
            val value = Fp324.fromLittleEndian(fields[0].hexToByteArray())

            if (fields[1] == "-") {
                assertFailsWith<IllegalArgumentException>("inverse case $index") { value.inverse() }
            } else {
                assertContentEquals(
                    fields[1].hexToByteArray(),
                    value.inverse().toLittleEndian(),
                    "inverse case $index",
                )
            }
            if (fields[3] == "-") {
                assertNull(value.squareRoot(), "square-root case $index")
            } else {
                assertContentEquals(
                    fields[3].hexToByteArray(),
                    assertNotNull(value.squareRoot(), "square-root case $index").toLittleEndian(),
                    "square-root case $index",
                )
            }
        }
    }
}

private fun decrementLittleEndian(bytes: ByteArray) {
    for (index in bytes.indices) {
        val value = bytes[index].toInt() and 0xff
        if (value != 0) {
            bytes[index] = (value - 1).toByte()
            return
        }
        bytes[index] = 0xff.toByte()
    }
}
