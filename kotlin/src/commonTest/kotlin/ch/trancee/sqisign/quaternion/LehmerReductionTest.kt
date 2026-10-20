// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.low32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LehmerReductionTest {
    @Test
    fun sumOfSquaresRejectsDeterminantsWithoutWorkHeadroom() {
        val p = Ibz.of(CAPACITY, 5, 4).withBound(CAPACITY - WORK_OVERHEAD)
        val root = Ibz.of(CAPACITY, 2, 3)

        assertFailsWith<IllegalArgumentException> { sumOfSquares(p, root) }
    }

    @Test
    fun sumOfSquaresAcceptsTheWidestDeterminant() {
        val p = Ibz.of(CAPACITY, 5, 4).withBound(CAPACITY - WORK_OVERHEAD - 1)
        val root = Ibz.of(CAPACITY, 2, 3)

        val squares = sumOfSquares(p, root)

        assertEquals("2 1", "${squares.x.low32()} ${squares.y.low32()}")
    }

    @Test
    fun zeroWindowsStopTheTransformBeforeSwapping() {
        val identity = intArrayOf(1, 0, 0, 1)

        assertContentEquals(identity, msbTransform(0L, 1L shl 40, 0L, swapAllowed = true))
        assertContentEquals(identity, msbTransform(1L shl 40, 0L, 0L, swapAllowed = true))
    }

    @Test
    fun rootsThatMissTheNormYieldNoRepresentation() {
        val p = Ibz.of(CAPACITY, 13, 5)
        val notARoot = Ibz.of(CAPACITY, 2, 3)

        assertNull(p.twoSquaresFromRoot(notARoot))
    }

    private companion object {
        const val CAPACITY = 256

        /** `value bits + 1 + 32` must stay strictly below the capacity. */
        const val WORK_OVERHEAD = 33
    }
}
