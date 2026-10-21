// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzRecord.Companion.encode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Dim2LatticeStructureTest {
    @Test
    fun gaussianGcdWithZeroFirstOperandReturnsTheSecond() {
        val b = listOf(Ibz.of(CAPACITY, 3, 3), Ibz.of(CAPACITY, -1, 2))

        val gcd = gaussianGcd(listOf(zero, zero), b)

        assertEquals(listOf("3:3", "2:-1"), gcd.map(::encode))
    }

    @Test
    fun latticeBasisNeedsANonzeroDiagonal() {
        val lattice =
            QuaternionLattice(one, List(DIMENSION) { i -> unit(i, if (i == 2) zero else one) })

        assertFailsWith<IllegalArgumentException> { lattice.coordinatesOf(element) }
    }

    @Test
    fun latticeBasisMustBeUpperTriangular() {
        val basis = List(DIMENSION) { i -> unit(i, one) }.toMutableList()
        basis[3] = listOf(zero, one, zero, one)

        assertFailsWith<IllegalArgumentException> {
            QuaternionLattice(one, basis).coordinatesOf(element)
        }
    }

    @Test
    fun primitiveElementsMustLieInTheOrder() {
        val lattice =
            QuaternionLattice(one, List(DIMENSION) { i -> unit(i, Ibz.of(CAPACITY, 2, 3)) })

        assertFailsWith<IllegalArgumentException> { element.primitiveIn(lattice) }
    }

    private fun unit(index: Int, diagonal: Ibz): List<Ibz> =
        List(DIMENSION) { if (it == index) diagonal else zero }

    private companion object {
        const val CAPACITY = 1920
        const val DIMENSION = 4
        val zero = Ibz.zero(CAPACITY)
        val one = Ibz.one(CAPACITY)
        val element = QuaternionElement(one, List(DIMENSION) { one })
    }
}
