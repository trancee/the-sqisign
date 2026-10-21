// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.fp2_664Vectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Fp2P664Test {
    @Test
    fun encodingUsesCanonicalRealThenImaginaryComponents() {
        val value = Fp2P664.fromLittleEndian(componentEncoding(1, 2, 84))
        val expected = componentEncoding(1, 2, 84)

        assertContentEquals(expected, value.toLittleEndian())
        assertContentEquals(ByteArray(168), Fp2P664.zero().toLittleEndian())
        assertContentEquals(componentEncoding(1, 0, 84), Fp2P664.one().toLittleEndian())
        assertFailsWith<IllegalArgumentException> { Fp2P664.fromLittleEndian(ByteArray(167)) }
        val nonCanonical = ByteArray(168)
        ParameterSet.NIST_V.primeBytes().copyInto(nonCanonical)
        assertFailsWith<IllegalArgumentException> { Fp2P664.fromLittleEndian(nonCanonical) }
        nonCanonical.fill(0)
        ParameterSet.NIST_V.primeBytes().copyInto(nonCanonical, 84)
        assertFailsWith<IllegalArgumentException> { Fp2P664.fromLittleEndian(nonCanonical) }
    }

    @Test
    fun negationAndEqualityCoverBothComponents() {
        val value = Fp2P664.fromLittleEndian(componentEncoding(1, 2, 84))

        assertTrue(value.negate().add(value).isEqualTo(Fp2P664.zero()))
        assertFalse(value.isEqualTo(Fp2P664.fromLittleEndian(componentEncoding(3, 2, 84))))
        assertFalse(value.isEqualTo(Fp2P664.fromLittleEndian(componentEncoding(1, 3, 84))))
    }

    @Test
    fun canonicalOrderingSignSelectionAndSwapMatchTheEncodedOrder() {
        val zero = Fp2P664.zero()
        val one = Fp2P664.one()
        val two = Fp2P664.fromLittleEndian(componentEncoding(2, 0, 84))
        val imaginaryOne = Fp2P664.fromLittleEndian(componentEncoding(0, 1, 84))

        assertTrue(two.compareTo(imaginaryOne) < 0)
        assertEquals(0, one.compareTo(Fp2P664.one()))
        assertFalse(Fp2P664.isNegative(zero))
        assertFalse(Fp2P664.isNegative(one))
        assertTrue(Fp2P664.isNegative(one.negate()))
        assertContentEquals(one.toLittleEndian(), Fp2P664.select(one, two, false).toLittleEndian())
        assertContentEquals(two.toLittleEndian(), Fp2P664.select(one, two, true).toLittleEndian())

        val unchanged = Fp2P664.conditionalSwap(one, two, false)
        val swapped = Fp2P664.conditionalSwap(one, two, true)
        assertContentEquals(one.toLittleEndian(), unchanged.first.toLittleEndian())
        assertContentEquals(two.toLittleEndian(), unchanged.second.toLittleEndian())
        assertContentEquals(two.toLittleEndian(), swapped.first.toLittleEndian())
        assertContentEquals(one.toLittleEndian(), swapped.second.toLittleEndian())
    }

    @Test
    fun operationsMatchIndependentPythonVectors() {
        for ((index, record) in fp2_664Vectors.withIndex()) {
            val fields = record.split(':')
            val left = Fp2P664.fromLittleEndian(componentEncoding(fields[0], fields[1]))
            val right = Fp2P664.fromLittleEndian(componentEncoding(fields[2], fields[3]))

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
}

private fun componentEncoding(real: Int, imaginary: Int, width: Int): ByteArray =
    ByteArray(width).also { it[0] = real.toByte() } +
        ByteArray(width).also { it[0] = imaginary.toByte() }

private fun componentEncoding(real: String, imaginary: String): ByteArray =
    real.hexToByteArray() + imaginary.hexToByteArray()
