// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.fp2_500NonlinearVectors
import ch.trancee.sqisign.fp2_500Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Fp2P500Test {
    @Test
    fun encodingUsesCanonicalRealThenImaginaryComponents() {
        val value = Fp2P500.fromLittleEndian(componentEncoding(1, 2, 64))
        val expected = componentEncoding(1, 2, 64)

        assertContentEquals(expected, value.toLittleEndian())
        assertContentEquals(ByteArray(128), Fp2P500.zero().toLittleEndian())
        assertContentEquals(componentEncoding(1, 0, 64), Fp2P500.one().toLittleEndian())
        assertFailsWith<IllegalArgumentException> { Fp2P500.fromLittleEndian(ByteArray(127)) }
        val nonCanonical = ByteArray(128)
        ParameterSet.NIST_III.primeBytes().copyInto(nonCanonical)
        assertFailsWith<IllegalArgumentException> { Fp2P500.fromLittleEndian(nonCanonical) }
        nonCanonical.fill(0)
        ParameterSet.NIST_III.primeBytes().copyInto(nonCanonical, 64)
        assertFailsWith<IllegalArgumentException> { Fp2P500.fromLittleEndian(nonCanonical) }
    }

    @Test
    fun negationAndEqualityCoverBothComponents() {
        val value = Fp2P500.fromLittleEndian(componentEncoding(1, 2, 64))

        assertTrue(value.negate().add(value).isEqualTo(Fp2P500.zero()))
        assertFalse(value.isEqualTo(Fp2P500.fromLittleEndian(componentEncoding(3, 2, 64))))
        assertFalse(value.isEqualTo(Fp2P500.fromLittleEndian(componentEncoding(1, 3, 64))))
    }

    @Test
    fun canonicalOrderingSignSelectionAndSwapMatchTheEncodedOrder() {
        val zero = Fp2P500.zero()
        val one = Fp2P500.one()
        val two = Fp2P500.fromLittleEndian(componentEncoding(2, 0, 64))
        val imaginaryOne = Fp2P500.fromLittleEndian(componentEncoding(0, 1, 64))

        assertTrue(two.compareTo(imaginaryOne) < 0)
        assertEquals(0, one.compareTo(Fp2P500.one()))
        assertFalse(Fp2P500.isNegative(zero))
        assertFalse(Fp2P500.isNegative(one))
        assertTrue(Fp2P500.isNegative(one.negate()))
        assertContentEquals(one.toLittleEndian(), Fp2P500.select(one, two, false).toLittleEndian())
        assertContentEquals(two.toLittleEndian(), Fp2P500.select(one, two, true).toLittleEndian())

        val unchanged = Fp2P500.conditionalSwap(one, two, false)
        val swapped = Fp2P500.conditionalSwap(one, two, true)
        assertContentEquals(one.toLittleEndian(), unchanged.first.toLittleEndian())
        assertContentEquals(two.toLittleEndian(), unchanged.second.toLittleEndian())
        assertContentEquals(two.toLittleEndian(), swapped.first.toLittleEndian())
        assertContentEquals(one.toLittleEndian(), swapped.second.toLittleEndian())
    }

    @Test
    fun operationsMatchIndependentPythonVectors() {
        for ((index, record) in fp2_500Vectors.withIndex()) {
            val fields = record.split(':')
            val left = Fp2P500.fromLittleEndian(componentEncoding(fields[0], fields[1]))
            val right = Fp2P500.fromLittleEndian(componentEncoding(fields[2], fields[3]))

            assertContentEquals(
                componentEncoding(fields[4], fields[5]),
                left.add(right).toLittleEndian(),
                "add $index",
            )
            assertContentEquals(
                componentEncoding(fields[6], fields[7]),
                left.subtract(right).toLittleEndian(),
                "subtract $index",
            )
            assertContentEquals(
                componentEncoding(fields[8], fields[9]),
                left.multiply(right).toLittleEndian(),
                "multiply $index",
            )
            assertContentEquals(
                componentEncoding(fields[10], fields[11]),
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

    @Test
    fun inverseResidueAndCanonicalRootMatchIndependentPythonVectorsBatch3() =
        assertNonlinearVectors(3)

    private fun assertNonlinearVectors(batch: Int) {
        for (index in batch until fp2_500NonlinearVectors.size step 4) {
            val record = fp2_500NonlinearVectors[index]
            val fields = record.split(':')
            val value = Fp2P500.fromLittleEndian(componentEncoding(fields[0], fields[1]))

            if (fields[2] == "-") {
                assertFailsWith<IllegalArgumentException>("inverse $index") { value.inverse() }
            } else {
                assertContentEquals(
                    componentEncoding(fields[2], fields[3]),
                    value.inverse().toLittleEndian(),
                    "inverse $index",
                )
            }
            if (fields[5] == "-") {
                assertEquals("0", fields[4], "square status $index")
                assertFalse(value.isSquare(), "square $index")
                assertNull(value.squareRoot(), "root $index")
            } else {
                assertEquals("1", fields[4], "square status $index")
                assertTrue(value.isSquare(), "square $index")
                val root = assertNotNull(value.squareRoot(), "root $index")
                assertContentEquals(
                    componentEncoding(fields[5], fields[6]),
                    root.toLittleEndian(),
                    "root $index",
                )
                assertTrue(root.square().isEqualTo(value), "root verifies $index")
            }
        }
    }
}

private fun componentEncoding(real: Int, imaginary: Int, width: Int): ByteArray =
    ByteArray(width).also { it[0] = real.toByte() } +
        ByteArray(width).also { it[0] = imaginary.toByte() }

private fun componentEncoding(real: String, imaginary: String): ByteArray =
    real.hexToByteArray() + imaginary.hexToByteArray()
