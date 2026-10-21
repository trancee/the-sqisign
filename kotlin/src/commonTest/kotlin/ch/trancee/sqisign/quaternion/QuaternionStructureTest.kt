// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuaternionStructureTest {
    @Test
    fun identityUsesTheReferenceBitLengths() {
        val identity = identityMatrix(CAPACITY)

        val expected =
            List(DIMENSION) { i -> List(DIMENSION) { j -> if (i == j) "2:1" else "1:0" } }
        assertEquals(expected, identity.map { row -> row.map(::encode) })
    }

    @Test
    fun zeroMatrixHoldsOneBitZeros() {
        val zero = zeroMatrix(CAPACITY)

        assertEquals(List(DIMENSION) { List(DIMENSION) { "1:0" } }, zero.map { it.map(::encode) })
    }

    @Test
    fun zeroTestsInspectEveryCoordinate() {
        val zero = Ibz.zero(CAPACITY)
        val one = Ibz.one(CAPACITY)
        val lastOnly = listOf(zero, zero, zero, one)

        assertTrue(QuaternionElement(one, List(DIMENSION) { zero }).isZero)
        assertFalse(QuaternionElement(one, lastOnly).isZero)
        assertFalse(lastOnly.isZeroVector())
    }

    @Test
    fun elementsHaveFourCoordinates() {
        val one = Ibz.one(CAPACITY)

        assertFailsWith<IllegalArgumentException> { QuaternionElement(one, listOf(one, one, one)) }
    }

    private companion object {
        const val CAPACITY = 1920
    }
}
