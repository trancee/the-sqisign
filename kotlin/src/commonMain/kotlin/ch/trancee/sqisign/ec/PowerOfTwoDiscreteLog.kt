// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

import ch.trancee.sqisign.field.QuadraticField
import ch.trancee.sqisign.field.QuadraticFieldElement

/** Prepared variable-time algorithm 4.37 for a positive-order torsion subgroup. */
internal class PowerOfTwoDiscreteLog<Element : QuadraticFieldElement<Element>>
private constructor(
    private val exponent: Int,
    private val one: Element,
    private val inversePowers: List<Element>,
) {
    private val minusOne = one.negate()

    internal fun solve(value: Element): TorsionScalar =
        TorsionScalar.fromLittleEndian(exponent, solveRange(value, 0, exponent))

    private fun solveRange(value: Element, offset: Int, length: Int): ByteArray {
        if (length == 1) {
            val isOne = value.isEqualTo(one)
            require(isOne || value.isEqualTo(minusOne)) {
                "Discrete logarithm value must belong to the prepared subgroup"
            }
            return byteArrayOf(if (isOne) 0 else 1)
        }
        val lowBits = length / 2
        val highBits = length - lowBits
        var lowValue = value
        repeat(highBits) { lowValue = lowValue.square() }
        val low = solveRange(lowValue, offset + highBits, lowBits)
        var residual = value
        for (bit in 0 until lowBits) {
            if (low[bit / Byte.SIZE_BITS].toInt() ushr (bit % Byte.SIZE_BITS) and 1 != 0) {
                residual = residual.multiply(inversePowers[offset + bit])
            }
        }
        val high = solveRange(residual, offset + lowBits, highBits)
        val output = ByteArray((length + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS)
        for (bit in 0 until length) {
            val source = if (bit < lowBits) low else high
            val position = if (bit < lowBits) bit else bit - lowBits
            val digit =
                source[position / Byte.SIZE_BITS].toInt() ushr (position % Byte.SIZE_BITS) and 1
            output[bit / Byte.SIZE_BITS] =
                (output[bit / Byte.SIZE_BITS].toInt() or (digit shl (bit % Byte.SIZE_BITS)))
                    .toByte()
        }
        return output
    }

    internal companion object {
        internal fun <Element : QuadraticFieldElement<Element>> prepare(
            field: QuadraticField<Element>,
            exponent: Int,
            generator: Element,
        ): PowerOfTwoDiscreteLog<Element> {
            require(exponent in 1..field.parameters.torsionExponent) {
                "Discrete logarithm exponent must be between one and the parameter maximum"
            }
            var power = generator
            val inversePowers =
                List(exponent) {
                    val inverse = field.conjugate(power)
                    power = power.square()
                    inverse
                }
            val one = field.one()
            require(inversePowers.last().isEqualTo(one.negate())) {
                "Discrete logarithm generator must have the exact requested power-of-two order"
            }
            return PowerOfTwoDiscreteLog(exponent, one, inversePowers)
        }
    }
}
