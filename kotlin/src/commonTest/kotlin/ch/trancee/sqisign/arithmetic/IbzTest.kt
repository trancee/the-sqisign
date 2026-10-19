// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IbzTest {
    @Test
    fun valuesCompareEqualOnlyWithSameCapacityBoundAndWords() {
        val value = Ibz.of(CAPACITY, 5, 4)

        assertEquals(Ibz.of(CAPACITY, 5, 4), value)
        assertEquals(Ibz.of(CAPACITY, 5, 4).hashCode(), value.hashCode())
        assertNotEquals(Ibz.of(CAPACITY * 2, 5, 4), value)
        assertNotEquals(Ibz.of(CAPACITY, 5, 5), value)
        assertNotEquals(Ibz.of(CAPACITY, 6, 4), value)
        assertFalse(value.equals(TEXT))
    }

    @Test
    fun defaultTextIsSignedDecimal() {
        val value = Ibz.of(CAPACITY, -12345, 32)

        assertEquals("-12345", value.toString())
    }

    @Test
    fun onlyTheSingleLowBitIsOne() {
        val highBit = Ibz.fromWords(CAPACITY, 65, longArrayOf(1L, 1L))

        assertFalse(highBit.isOne)
        assertTrue(Ibz.one(CAPACITY).isOne)
    }

    @Test
    fun invalidShapesAreRejected() {
        assertFailsWith<IllegalArgumentException> { Ibz.of(0, 1, 2) }
        assertFailsWith<IllegalArgumentException> { Ibz.of(100, 1, 2) }
        assertFailsWith<IllegalArgumentException> { Ibz.of(CAPACITY, 1, 33) }
        assertFailsWith<IllegalArgumentException> { Ibz.of(CAPACITY, 1, -1) }
        assertFailsWith<IllegalArgumentException> { Ibz.of(CAPACITY, 1, 1) }
        assertFailsWith<IllegalArgumentException> { Ibz.fromWords(CAPACITY, 0, LongArray(0)) }
        assertFailsWith<IllegalArgumentException> {
            Ibz.fromWords(CAPACITY, CAPACITY + 1, LongArray(31))
        }
        assertFailsWith<IllegalArgumentException> { Ibz.fromWords(CAPACITY, 64, LongArray(2)) }
        assertFailsWith<IllegalArgumentException> {
            Ibz.copyBits(CAPACITY, LongArray(30), CAPACITY)
        }
        assertFailsWith<IllegalArgumentException> { Ibz.copyBits(CAPACITY, LongArray(1), -1) }
    }

    @Test
    fun invalidOperandsAreRejected() {
        val value = Ibz.of(CAPACITY, 3, 3)
        val other = Ibz.of(CAPACITY * 2, 3, 3)

        assertFailsWith<IllegalArgumentException> { value + other }
        assertFailsWith<IllegalArgumentException> { value * other }
        assertFailsWith<IllegalArgumentException> { value.compareTo(other) }
        assertFailsWith<IllegalArgumentException> { value.divRem(other) }
        assertFailsWith<IllegalArgumentException> { value.compareTo(Int.MIN_VALUE) }
        assertFailsWith<IllegalArgumentException> { value.shiftRight(-1) }
        assertFailsWith<IllegalArgumentException> { value.shiftLeft(-1) }
        assertFailsWith<IllegalArgumentException> { value.mod2exp(-1) }
        assertFailsWith<IllegalArgumentException> { value.mod2exp(CAPACITY) }
        assertFailsWith<IllegalArgumentException> { value.withBound(CAPACITY + 1) }
        assertFailsWith<IllegalArgumentException> { value.timesIntWithBound(2, 0) }
        assertFailsWith<IllegalArgumentException> { value.timesIntWithBound(2, CAPACITY + 1) }
        assertFailsWith<IllegalArgumentException> { value.pow(-1) }
        assertFailsWith<IllegalArgumentException> { value.extractUnsigned64(-1) }
        assertFailsWith<IllegalArgumentException> { (-value).toDigits() }
    }

    @Test
    fun divisionByZeroIsArithmetic() {
        val value = Ibz.of(CAPACITY, 3, 3)

        assertFailsWith<ArithmeticException> { value.divRem(Ibz.zero(CAPACITY)) }
        assertFailsWith<ArithmeticException> { value.modUnsigned(0UL) }
    }

    @Test
    fun leftShiftIntoTheCapacitySignBitIsRejected() {
        val almostTop = Ibz.of(CAPACITY, 1, 2).shiftLeft(CAPACITY - 2)

        assertEquals(CAPACITY, almostTop.bitlen)
        assertFailsWith<ArithmeticException> { almostTop.shiftLeft(1) }
        assertEquals(CAPACITY, (-almostTop).shiftLeft(1).bitlen)
    }

    @Test
    fun capacitySumsKeepTheNegativeMinimumOnly() {
        val half = -Ibz.of(CAPACITY, 1, 2).shiftLeft(CAPACITY - 2)

        assertEquals(CAPACITY, (half + half).bitlen)
        assertFailsWith<ArithmeticException> { half + half - Ibz.of(CAPACITY, 1, 2) }
        assertFailsWith<ArithmeticException> { (half + half) + (half + half) }
    }

    @Test
    fun parsingRejectsNonDigitsRadixAndCapacityOverflow() {
        val tooLarge = "8" + "0".repeat(CAPACITY / 4 - 1)

        for (text in listOf("/", ":", "A", "G", "a", "z", "\u0663", "-")) {
            assertNull(Ibz.parse(CAPACITY, text, 10), text)
        }
        assertNull(Ibz.parse(CAPACITY, "@", 16))
        assertNull(Ibz.parse(CAPACITY, "g", 16))
        assertNull(Ibz.parse(CAPACITY, tooLarge, 16))
        assertEquals(CAPACITY, Ibz.parse(CAPACITY, "7" + "f".repeat(CAPACITY / 4 - 1), 16)?.bitlen)
        assertFailsWith<IllegalArgumentException> { Ibz.parse(CAPACITY, "1", 8) }
        assertFailsWith<IllegalArgumentException> { Ibz.of(CAPACITY, 1, 2).toString(2) }
    }

    @Test
    fun randomSamplingRejectsInvalidRequests() {
        val one = Ibz.one(CAPACITY)
        val zero = Ibz.zero(CAPACITY)

        assertFailsWith<IllegalArgumentException> {
            Ibz.randomInterval(one, zero) { ByteArray(it) }
        }
        assertFailsWith<IllegalArgumentException> {
            Ibz.randomInterval(zero, one) { ByteArray(it - 1) }
        }
        assertFailsWith<IllegalArgumentException> {
            Ibz.randomSymmetric(CAPACITY, 0) { ByteArray(it) }
        }
    }

    private companion object {
        const val CAPACITY = 1920
        const val TEXT = "5"
    }
}
