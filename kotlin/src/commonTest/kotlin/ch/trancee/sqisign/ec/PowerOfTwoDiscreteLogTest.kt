// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.Fp2P324
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PowerOfTwoDiscreteLogTest {
    @Test
    fun logarithmsRetainBothLowAndHighDigitsOfAnOddPower() {
        val generator = Fp2P324.fromLittleEndian(ByteArray(82).also { it[41] = 1 })
        val solver = PowerOfTwoDiscreteLog.prepare(Fp2P324, 2, generator)
        val value = generator.negate()

        val logarithm = solver.solve(value)

        assertContentEquals(byteArrayOf(3), logarithm.toLittleEndian())
    }

    @Test
    fun invalidExponentsFailBeforeTheGeneratorIsInspected() {
        for (exponent in listOf(Int.MIN_VALUE, 0, 325, Int.MAX_VALUE)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    PowerOfTwoDiscreteLog.prepare(Fp2P324, exponent, Fp2P324.zero())
                }

            assertEquals(
                "Discrete logarithm exponent must be between one and the parameter maximum",
                failure.message,
            )
        }
    }

    @Test
    fun zeroAndLowerOrderGeneratorsAreRejected() {
        for (generator in listOf(Fp2P324.zero(), Fp2P324.one(), Fp2P324.one().negate())) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    PowerOfTwoDiscreteLog.prepare(Fp2P324, 2, generator)
                }

            assertEquals(
                "Discrete logarithm generator must have the exact requested power-of-two order",
                failure.message,
            )
        }
    }

    @Test
    fun aGeneratorOfExcessiveOrderIsAlsoRejected() {
        val generator = Fp2P324.fromLittleEndian(ByteArray(82).also { it[41] = 1 })

        val failure =
            assertFailsWith<IllegalArgumentException> {
                PowerOfTwoDiscreteLog.prepare(Fp2P324, 1, generator)
            }

        assertEquals(
            "Discrete logarithm generator must have the exact requested power-of-two order",
            failure.message,
        )
    }

    @Test
    fun zeroDoesNotBecomeASuccessShapedLogarithm() {
        val solver = PowerOfTwoDiscreteLog.prepare(Fp2P324, 1, Fp2P324.one().negate())

        val failure = assertFailsWith<IllegalArgumentException> { solver.solve(Fp2P324.zero()) }

        assertEquals(
            "Discrete logarithm value must belong to the prepared subgroup",
            failure.message,
        )
    }

    @Test
    fun recursiveSolvingRejectsValuesOutsideTheSubgroup() {
        val generator = Fp2P324.fromLittleEndian(ByteArray(82).also { it[41] = 1 })
        val solver = PowerOfTwoDiscreteLog.prepare(Fp2P324, 2, generator)
        val value = Fp2P324.one().add(Fp2P324.one())

        val failure = assertFailsWith<IllegalArgumentException> { solver.solve(value) }

        assertEquals(
            "Discrete logarithm value must belong to the prepared subgroup",
            failure.message,
        )
    }

    @Test
    fun reusingPreparedPowersDoesNotCarryPriorDigitsIntoTheNextLogarithm() {
        val generator = Fp2P324.fromLittleEndian(ByteArray(82).also { it[41] = 1 })
        val solver = PowerOfTwoDiscreteLog.prepare(Fp2P324, 2, generator)
        solver.solve(generator)

        val logarithm = solver.solve(generator.negate())

        assertContentEquals(byteArrayOf(3), logarithm.toLittleEndian())
    }
}
