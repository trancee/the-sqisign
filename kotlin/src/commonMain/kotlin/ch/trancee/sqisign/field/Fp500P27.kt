// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet

/** Canonical, variable-time element of the `p500_27` prime field. */
internal class Fp500P27 private constructor(private val value: ByteArray) {
    internal fun toLittleEndian(): ByteArray = value.copyOf()

    internal fun compareTo(other: Fp500P27): Int = compareFieldBytes(value, other.value)

    internal fun add(other: Fp500P27): Fp500P27 =
        Fp500P27(addFieldModulo(value, other.value, MODULUS))

    internal fun subtract(other: Fp500P27): Fp500P27 =
        Fp500P27(subtractFieldModulo(value, other.value, MODULUS))

    internal fun negate(): Fp500P27 = Fp500P27(negateFieldModulo(value, MODULUS))

    internal fun multiply(other: Fp500P27): Fp500P27 =
        Fp500P27(multiplyFieldModulo(value, other.value, MODULUS))

    internal fun square(): Fp500P27 = multiply(this)

    internal fun inverse(): Fp500P27 {
        require(!isZeroFieldBytes(value)) { "The p500_27 field zero has no multiplicative inverse" }
        return Fp500P27(exponentiateField(value, MODULUS.inverseExponent, MODULUS))
    }

    internal fun isSquare(): Boolean = squareRoot() != null

    internal fun squareRoot(): Fp500P27? {
        val candidate =
            if (isZeroFieldBytes(value)) zero()
            else Fp500P27(exponentiateField(value, MODULUS.squareRootExponent, MODULUS))
        val isRoot = candidate.square().compareTo(this) == 0
        val negative = candidate.negate()
        val root = if (candidate.compareTo(negative) <= 0) candidate else negative
        return if (isRoot) root else null
    }

    internal companion object {
        private val MODULUS = FieldModulus(ParameterSet.NIST_III.primeBytes())

        internal fun fromLittleEndian(encoded: ByteArray): Fp500P27 {
            require(encoded.size == ENCODED_BYTES) {
                "p500_27 field values require exactly $ENCODED_BYTES bytes"
            }
            require(compareFieldBytes(encoded, MODULUS.bytes) < 0) {
                "The p500_27 field encoding is not canonical"
            }
            return Fp500P27(encoded.copyOf())
        }

        internal fun zero(): Fp500P27 = Fp500P27(ByteArray(ENCODED_BYTES))

        internal fun one(): Fp500P27 = Fp500P27(ByteArray(ENCODED_BYTES).also { it[0] = 1 })

        internal fun conditionalSwap(
            first: Fp500P27,
            second: Fp500P27,
            swap: Boolean,
        ): Pair<Fp500P27, Fp500P27> = if (swap) second to first else first to second

        internal fun isNegative(value: Fp500P27): Boolean = value.compareTo(value.negate()) > 0

        internal fun select(
            first: Fp500P27,
            second: Fp500P27,
            selectSecond: Boolean,
        ): Fp500P27 = if (selectSecond) second else first

        private const val ENCODED_BYTES: Int = 64
    }
}
