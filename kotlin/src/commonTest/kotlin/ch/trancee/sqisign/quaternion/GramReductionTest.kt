// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GramReductionTest {
    private val config = LllConfig(ParameterSet.NIST_I)

    @Test fun negativeFirstDiagonalIsRejected() = assertRejected(dk = -1, dk1 = 1)

    @Test fun zeroFirstDiagonalIsRejected() = assertRejected(dk = 0, dk1 = 1)

    @Test fun negativeSecondDiagonalIsRejected() = assertRejected(dk = 1, dk1 = -1)

    @Test fun zeroSecondDiagonalIsRejected() = assertRejected(dk = 1, dk1 = 0)

    private fun assertRejected(dk: Int, dk1: Int) {
        val mu = Ibz.zero(CAPACITY).withBound(MU_BITS)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                config.materialiseBlock(small(dk), small(dk1), mu, config.precisionBits)
            }

        assertEquals("Block form is not positive", failure.message)
    }

    private fun small(value: Int): Ibz = Ibz.of(CAPACITY, value, BITS).withBound(DIAGONAL_BITS)

    private companion object {
        const val CAPACITY = 1920
        const val BITS = 3

        /** NIST I `FP_E_BITS` and `FP_L_BITS`. */
        const val DIAGONAL_BITS = 826
        const val MU_BITS = 896
    }
}
