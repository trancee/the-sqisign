// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.fp2_324NonlinearVectors
import ch.trancee.sqisign.fp2_324Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Fp2P324Test {
    @Test
    fun encodingUsesCanonicalRealThenImaginaryComponents() {
        val value =
            Fp2P324.fromComponents(
                Fp324.fromInt(1),
                Fp324.fromInt(2),
            )
        val expected = Fp324.fromInt(1).toLittleEndian() + Fp324.fromInt(2).toLittleEndian()

        assertEquals(82, expected.size)
        assertContentEquals(expected, value.toLittleEndian())
        assertTrue(Fp2P324.fromLittleEndian(expected).isEqualTo(value))
        assertFailsWith<IllegalArgumentException> { Fp2P324.fromLittleEndian(ByteArray(81)) }
        val nonCanonical = expected.copyOf().also { ParameterSet.NIST_I.primeBytes().copyInto(it) }
        assertFailsWith<IllegalArgumentException> { Fp2P324.fromLittleEndian(nonCanonical) }
    }

    @Test
    fun additionSubtractionMultiplicationAndSquaringFollowI2EqualsNegativeOne() {
        val left = Fp2P324.fromComponents(Fp324.fromInt(1), Fp324.fromInt(1))
        val right = Fp2P324.fromComponents(Fp324.fromInt(2), Fp324.fromInt(3))

        assertContentEquals(
            Fp2P324.fromComponents(Fp324.fromInt(3), Fp324.fromInt(4)).toLittleEndian(),
            left.add(right).toLittleEndian(),
        )
        assertContentEquals(
            Fp2P324.fromComponents(
                    Fp324.fromInt(0).subtract(Fp324.fromInt(1)),
                    Fp324.fromInt(0).subtract(Fp324.fromInt(2)),
                )
                .toLittleEndian(),
            left.subtract(right).toLittleEndian(),
        )
        assertContentEquals(
            Fp2P324.fromComponents(
                    Fp324.fromInt(0).subtract(Fp324.fromInt(1)),
                    Fp324.fromInt(5),
                )
                .toLittleEndian(),
            left.multiply(right).toLittleEndian(),
        )
        assertContentEquals(
            Fp2P324.fromComponents(Fp324.zero(), Fp324.fromInt(2)).toLittleEndian(),
            left.square().toLittleEndian(),
        )
    }

    @Test
    fun equalityChecksBothComponentsIndependently() {
        val value = Fp2P324.fromComponents(Fp324.fromInt(1), Fp324.fromInt(2))

        assertTrue(value.isEqualTo(Fp2P324.fromComponents(Fp324.fromInt(1), Fp324.fromInt(2))))
        assertFalse(value.isEqualTo(Fp2P324.fromComponents(Fp324.fromInt(3), Fp324.fromInt(2))))
        assertFalse(value.isEqualTo(Fp2P324.fromComponents(Fp324.fromInt(1), Fp324.fromInt(3))))
    }

    @Test
    fun canonicalOrderingSignSelectionAndSwapMatchTheEncodedOrder() {
        val zero = Fp2P324.zero()
        val one = Fp2P324.one()
        val two = Fp2P324.fromComponents(Fp324.fromInt(2), Fp324.zero())
        val imaginaryOne = Fp2P324.fromComponents(Fp324.zero(), Fp324.one())

        assertTrue(two.compareTo(imaginaryOne) < 0)
        assertEquals(0, one.compareTo(Fp2P324.one()))
        assertFalse(Fp2P324.isNegative(zero))
        assertFalse(Fp2P324.isNegative(one))
        assertTrue(Fp2P324.isNegative(one.negate()))
        assertContentEquals(one.toLittleEndian(), Fp2P324.select(one, two, false).toLittleEndian())
        assertContentEquals(two.toLittleEndian(), Fp2P324.select(one, two, true).toLittleEndian())

        val unchanged = Fp2P324.conditionalSwap(one, two, false)
        val swapped = Fp2P324.conditionalSwap(one, two, true)
        assertContentEquals(one.toLittleEndian(), unchanged.first.toLittleEndian())
        assertContentEquals(two.toLittleEndian(), unchanged.second.toLittleEndian())
        assertContentEquals(two.toLittleEndian(), swapped.first.toLittleEndian())
        assertContentEquals(one.toLittleEndian(), swapped.second.toLittleEndian())
    }

    @Test
    fun extensionOperationsMatchIndependentPythonAndPinnedCFieldVectors() {
        for ((index, record) in fp2_324Vectors.withIndex()) {
            val fields = record.split(':')
            val left =
                Fp2P324.fromComponents(
                    Fp324.fromLittleEndian(fields[0].hexToByteArray()),
                    Fp324.fromLittleEndian(fields[1].hexToByteArray()),
                )
            val right =
                Fp2P324.fromComponents(
                    Fp324.fromLittleEndian(fields[2].hexToByteArray()),
                    Fp324.fromLittleEndian(fields[3].hexToByteArray()),
                )

            assertContentEquals(
                componentEncoding(fields[4], fields[5]),
                left.add(right).toLittleEndian(),
                "addition case $index",
            )
            assertContentEquals(
                componentEncoding(fields[6], fields[7]),
                left.subtract(right).toLittleEndian(),
                "subtraction case $index",
            )
            assertContentEquals(
                componentEncoding(fields[8], fields[9]),
                left.multiply(right).toLittleEndian(),
                "multiplication case $index",
            )
            assertContentEquals(
                componentEncoding(fields[10], fields[11]),
                left.square().toLittleEndian(),
                "square case $index",
            )
        }
    }

    @Test
    fun inversionAndSquareRootsAreVerifiedAndRejectInvalidInputs() {
        val onePlusI = Fp2P324.fromComponents(Fp324.one(), Fp324.one())
        val inverse = onePlusI.inverse()
        val root = assertNotNull(onePlusI.squareRoot())
        val nonsquare = Fp2P324.fromComponents(Fp324.one(), Fp324.fromInt(2))

        assertTrue(onePlusI.multiply(inverse).isEqualTo(Fp2P324.one()))
        assertTrue(root.square().isEqualTo(onePlusI))
        assertTrue(root.isSquare())
        assertFalse(nonsquare.isSquare())
        assertNull(nonsquare.squareRoot())
        assertFailsWith<IllegalArgumentException> { Fp2P324.zero().inverse() }
        assertTrue(
            assertNotNull(Fp2P324.zero().squareRoot()).isEqualTo(Fp2P324.zero()),
        )
    }

    @Test
    fun inverseResidueAndCanonicalRootMatchIndependentVectors() {
        for ((index, record) in fp2_324NonlinearVectors.withIndex()) {
            val fields = record.split(':')
            val value =
                Fp2P324.fromComponents(
                    Fp324.fromLittleEndian(fields[0].hexToByteArray()),
                    Fp324.fromLittleEndian(fields[1].hexToByteArray()),
                )

            if (fields[2] == "-") {
                assertFailsWith<IllegalArgumentException>("inverse case $index") { value.inverse() }
            } else {
                assertContentEquals(
                    componentEncoding(fields[2], fields[3]),
                    value.inverse().toLittleEndian(),
                    "inverse case $index",
                )
            }
            if (fields[5] == "-") {
                assertEquals("0", fields[4], "square status case $index")
                assertFalse(value.isSquare(), "square case $index")
                assertNull(value.squareRoot(), "square-root case $index")
            } else {
                assertEquals("1", fields[4], "square status case $index")
                assertTrue(value.isSquare(), "square case $index")
                assertContentEquals(
                    componentEncoding(fields[5], fields[6]),
                    assertNotNull(value.squareRoot(), "square-root case $index").toLittleEndian(),
                    "square-root case $index",
                )
            }
        }
    }
}

private fun componentEncoding(real: String, imaginary: String): ByteArray =
    real.hexToByteArray() + imaginary.hexToByteArray()
