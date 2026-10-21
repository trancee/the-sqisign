// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.Test
import kotlin.test.assertFailsWith

class IbzFixedPointTest {
    @Test
    fun signedShiftsRejectShiftsBeyondTheirBound() {
        val value = z("8:5")

        assertFailsWith<IllegalArgumentException> { value.shiftSigned(-3, 2, 8) }
        assertFailsWith<IllegalArgumentException> { value.shiftSigned(3, 2, 8) }
        assertFailsWith<IllegalArgumentException> { value.scaledProduct(value, -3, 2, 8) }
        assertFailsWith<IllegalArgumentException> { value.scaledProduct(value, 3, 2, 8) }
    }

    @Test
    fun narrowedResultsRejectBoundsOutsideCapacity() {
        val value = z("8:5")

        assertFailsWith<IllegalArgumentException> { value.shiftSigned(0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { value.shiftSigned(0, 0, CAPACITY + 1) }
    }

    @Test
    fun productsRejectBoundsOutsideTheWideProduct() {
        val value = z("8:5")

        assertFailsWith<IllegalArgumentException> { value.highProduct(value, -1, 8) }
        assertFailsWith<IllegalArgumentException> { value.highProduct(value, 0, 0) }
        assertFailsWith<IllegalArgumentException> { value.highProduct(value, 0, CAPACITY + 1) }
        assertFailsWith<IllegalArgumentException> { value.highProduct(value, 0, 193) }
        assertFailsWith<IllegalArgumentException> {
            value.highProduct(Ibz.one(CAPACITY + Long.SIZE_BITS), 0, 8)
        }
    }

    @Test
    fun reciprocalsNeedAPositiveValueAndWordPrecision() {
        assertFailsWith<IllegalArgumentException> { z("2:-1").fixedPointReciprocal(64, 1) }
        assertFailsWith<IllegalArgumentException> { z("1:0").fixedPointReciprocal(64, 1) }
        assertFailsWith<IllegalArgumentException> { z("2:1").fixedPointReciprocal(63, 1) }
        assertFailsWith<IllegalArgumentException> {
            z("2:1").fixedPointReciprocal(CAPACITY - 5, 1)
        }
    }

    @Test
    fun roundedShiftsNeedWholeWordsWithinCapacity() {
        val value = z("8:5")

        assertFailsWith<IllegalArgumentException> { value.roundShiftWords(-64, 64) }
        assertFailsWith<IllegalArgumentException> { value.roundShiftWords(1, 64) }
        assertFailsWith<IllegalArgumentException> { value.roundShiftWords(0, 0) }
        assertFailsWith<IllegalArgumentException> { value.roundShiftWords(0, 65) }
        assertFailsWith<IllegalArgumentException> { value.roundShiftWords(64, CAPACITY) }
    }

    private fun z(text: String): Ibz = IbzRecord.decodeIbz(CAPACITY, text)

    private companion object {
        const val CAPACITY = 256
    }
}
