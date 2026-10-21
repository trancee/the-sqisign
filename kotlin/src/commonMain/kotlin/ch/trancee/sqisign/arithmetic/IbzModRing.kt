// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * Variable-time residues modulo an odd positive [modulus] on trimmed natural words, held in
 * Montgomery form `a R mod m` so multiplication needs no division and squares never meet an [Ibz]
 * capacity. [enter] and [exit] convert; addition, halving, comparison and zero tests act on the
 * form directly. Every operand must be a ring element.
 */
internal class IbzModRing(internal val modulus: LongArray) {
    init {
        require(modulus.isNotEmpty() && IbzNatural.bit(modulus, 0)) {
            "Modulus must be odd and positive"
        }
    }

    private val montgomery = IbzMontgomery(modulus)

    internal val one: LongArray = enter(IbzNatural.ONE)

    /** Any natural [value], reduced and converted into the ring. */
    internal fun enter(value: LongArray): LongArray =
        montgomery.enter(IbzNatural.trim(IbzNatural.mod(value, modulus)))

    /** The reduced natural an element represents. */
    internal fun exit(value: LongArray): LongArray = montgomery.exit(value)

    /** A signed small value reduced into the ring. */
    internal fun ofSigned(value: Long): LongArray {
        val magnitude = enter(IbzNatural.of(if (value < 0) -value else value))
        return if (value < 0) subtract(LongArray(0), magnitude) else magnitude
    }

    internal fun add(left: LongArray, right: LongArray): LongArray {
        val sum = IbzNatural.add(left, right)
        return if (IbzWords.compare(sum, modulus) >= 0) IbzNatural.subtract(sum, modulus) else sum
    }

    internal fun subtract(left: LongArray, right: LongArray): LongArray =
        if (IbzWords.compare(left, right) >= 0) IbzNatural.subtract(left, right)
        else IbzNatural.subtract(IbzNatural.add(left, modulus), right)

    internal fun multiply(left: LongArray, right: LongArray): LongArray =
        montgomery.multiply(left, right)

    /** Division by two, which commutes with the Montgomery form. */
    internal fun half(value: LongArray): LongArray =
        IbzNatural.shiftRight(
            if (IbzNatural.bit(value, 0)) IbzNatural.add(value, modulus) else value,
            1,
        )

    /** Left-to-right square and multiply over the bits of a natural [exponent]. */
    internal fun pow(base: LongArray, exponent: LongArray): LongArray {
        var result = one
        for (index in IbzWords.bitLength(exponent) - 1 downTo 0) {
            result = multiply(result, result)
            if (IbzNatural.bit(exponent, index)) {
                result = multiply(result, base)
            }
        }
        return result
    }

    internal fun isOne(value: LongArray): Boolean = value.contentEquals(one)
}
