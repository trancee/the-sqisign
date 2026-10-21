// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.UInt328

/**
 * Canonical element of the `p324_3` prime field.
 *
 * Arithmetic is portable and variable-time. This implementation does not provide a constant-time or
 * secret-input guarantee.
 */
internal class Fp324 private constructor(private val value: UInt328) {
    /** Returns a fresh canonical 41-byte little-endian encoding. */
    internal fun toLittleEndian(): ByteArray = value.toLittleEndian()

    /** Returns the canonical ordering of the two field elements. */
    internal fun compareTo(other: Fp324): Int = value.compareTo(other.value)

    /** Reports whether this field element is zero. */
    internal fun isZero(): Boolean = value.compareTo(ZERO_VALUE) == 0

    /** Returns the canonical sum modulo `p324_3`. */
    internal fun add(other: Fp324): Fp324 {
        val sum = value.add(other.value).value
        return if (sum >= MODULUS) Fp324(sum.subtract(MODULUS).value) else Fp324(sum)
    }

    /** Returns the canonical difference modulo `p324_3`. */
    internal fun subtract(other: Fp324): Fp324 =
        if (value >= other.value) {
            Fp324(value.subtract(other.value).value)
        } else {
            Fp324(MODULUS.subtract(other.value.subtract(value).value).value)
        }

    /** Returns the additive inverse modulo `p324_3`. */
    internal fun negate(): Fp324 =
        if (isZero()) {
            zero()
        } else {
            Fp324(MODULUS.subtract(value).value)
        }

    /** Returns the canonical product modulo `p324_3`. */
    internal fun multiply(other: Fp324): Fp324 =
        Fp324(
            UInt328.fromLittleEndian(
                FIELD.multiply(value.toLittleEndian(), other.value.toLittleEndian()),
            ),
        )

    /** Returns the canonical square modulo `p324_3`. */
    internal fun square(): Fp324 = multiply(this)

    /**
     * Returns the multiplicative inverse; zero is rejected.
     *
     * Exponentiation and multiplication are variable-time.
     */
    internal fun inverse(): Fp324 {
        require(!isZero()) { "The p324_3 field zero has no multiplicative inverse" }
        return exponentiate(INVERSE_EXPONENT)
    }

    /**
     * Returns the smaller square root, or null when this element is a quadratic non-residue.
     *
     * The canonical root choice and exponentiation are variable-time.
     */
    internal fun squareRoot(): Fp324? {
        if (isZero()) return zero()
        val candidate = exponentiate(SQUARE_ROOT_EXPONENT)
        return if (candidate.square().value.compareTo(value) == 0) {
            val otherRoot = MODULUS.subtract(candidate.value).value
            if (candidate.value <= otherRoot) candidate else Fp324(otherRoot)
        } else {
            null
        }
    }

    private fun exponentiate(exponent: UInt328): Fp324 =
        Fp324(
            UInt328.fromLittleEndian(
                FIELD.exponentiate(value.toLittleEndian(), exponent.toLittleEndian()),
            ),
        )

    internal companion object {
        /** Fixed exponent used by the reference-sign extension-field square root. */
        internal fun powPMinusThreeOverFour(value: Fp324): Fp324 =
            value.exponentiate(REFERENCE_ROOT_EXPONENT)

        internal const val ENCODED_BYTES: Int = UInt328.ENCODED_BYTES

        private val MODULUS = UInt328.fromLittleEndian(ParameterSet.NIST_I.primeBytes())
        private val FIELD = FieldModulus(ParameterSet.NIST_I.primeBytes())
        private val ZERO_VALUE = UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES))
        private val ONE_VALUE =
            UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 1 })
        private val TWO_VALUE =
            UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 2 })
        private val FOUR_VALUE =
            UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES).also { it[0] = 4 })
        private val INVERSE_EXPONENT = MODULUS.subtract(TWO_VALUE).value
        private val SQUARE_ROOT_EXPONENT =
            MODULUS.add(ONE_VALUE).value.divideAndRemainder(FOUR_VALUE).quotient
        private val REFERENCE_ROOT_EXPONENT = SQUARE_ROOT_EXPONENT.subtract(ONE_VALUE).value

        /**
         * Imports exactly 41 little-endian bytes, rejecting representatives greater than or equal
         * to p.
         */
        internal fun fromLittleEndian(encoded: ByteArray): Fp324 {
            val value = UInt328.fromLittleEndian(encoded)
            require(value < MODULUS) { "The p324_3 field encoding is not canonical" }
            return Fp324(value)
        }

        /** Creates a field element from a nonnegative small integer. */
        internal fun fromInt(value: Int): Fp324 {
            require(value >= 0) { "The p324_3 field integer must be nonnegative" }
            val encoded = ByteArray(UInt328.ENCODED_BYTES)
            for (index in 0 until Int.SIZE_BYTES) {
                encoded[index] = (value ushr (index * Byte.SIZE_BITS)).toByte()
            }
            return Fp324(UInt328.fromLittleEndian(encoded))
        }

        /** Returns field zero. */
        internal fun zero(): Fp324 = Fp324(ZERO_VALUE)

        /** Returns field one. */
        internal fun one(): Fp324 = Fp324(ONE_VALUE)

        /** Returns the two inputs in original or swapped order without mutating either value. */
        internal fun conditionalSwap(
            first: Fp324,
            second: Fp324,
            swap: Boolean,
        ): Pair<Fp324, Fp324> = if (swap) second to first else first to second

        internal fun isNegative(value: Fp324): Boolean = value.compareTo(value.negate()) > 0

        internal fun select(first: Fp324, second: Fp324, selectSecond: Boolean): Fp324 =
            if (selectSecond) second else first
    }
}
