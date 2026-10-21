// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.IbzRecord
import ch.trancee.sqisign.arithmetic.parse
import ch.trancee.sqisign.arithmetic.shiftLeft
import ch.trancee.sqisign.arithmetic.shiftRight
import ch.trancee.sqisign.arithmetic.toString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GaussianReductionTest {
    private val record = IbzRecord.all("gauss", GaussianVectors.records).first()
    private val config = record.lllConfig()
    private val p = record.ibz(HNF_P)
    private val bound = record.int(BOUND)

    @Test
    fun rejectsAPrimeBelowTheSquareOfTheStoredRoot() {
        val small = p.shiftRight(2)

        assertFailsWith<IllegalArgumentException> {
            config.reduceO0Ideal(record.idealHnf(), small, bound)
        }
    }

    @Test
    fun rejectsAPrimeAboveTheNextSquare() {
        val large = p.shiftLeft(2)

        assertFailsWith<IllegalArgumentException> {
            config.reduceO0Ideal(record.idealHnf(), large, bound)
        }
    }

    @Test
    fun rejectsABoundBeyondTheIntegerCapacity() {
        assertFailsWith<IllegalArgumentException> {
            config.reduceO0Ideal(record.idealHnf(), p, record.capacity)
        }
    }

    @Test
    fun orientationMovesANegativeRealKeyOntoThePositiveImaginaryAxis() {
        val key = Complex(small(-2), small(0))
        val other = Complex(small(3), small(-5))

        val oriented = orient(listOf(key, other))

        assertEquals(listOf("0 2", "-5 -3"), oriented.map(::text))
    }

    @Test
    fun orientationRotatesAKeyInTheFourthQuadrant() {
        val key = Complex(small(2), small(-1))

        val oriented = orient(listOf(key))

        assertEquals("1 2", text(oriented.single()))
    }

    private fun small(value: Int): Ibz =
        requireNotNull(Ibz.parse(record.capacity, value.toString(), DECIMAL))

    private fun text(value: Complex): String =
        "${value.re.toString(DECIMAL)} ${value.im.toString(DECIMAL)}"

    private companion object {
        const val BOUND = 6
        const val DECIMAL = 10
    }
}
