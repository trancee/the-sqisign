// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet

/** Canonical, variable-time element of the `p664_17` prime field. */
internal class Fp664P17 private constructor(private val value: ByteArray) {
    internal fun toLittleEndian(): ByteArray = value.copyOf()

    internal fun compareTo(other: Fp664P17): Int = compareFieldBytes(value, other.value)

    internal fun add(other: Fp664P17): Fp664P17 =
        Fp664P17(addFieldModulo(value, other.value, MODULUS))

    internal fun subtract(other: Fp664P17): Fp664P17 =
        Fp664P17(subtractFieldModulo(value, other.value, MODULUS))

    internal fun negate(): Fp664P17 = Fp664P17(negateFieldModulo(value, MODULUS))

    internal fun multiply(other: Fp664P17): Fp664P17 =
        Fp664P17(multiplyFieldModulo(value, other.value, MODULUS))

    internal fun square(): Fp664P17 = multiply(this)

    internal fun inverse(): Fp664P17 {
        require(!isZeroFieldBytes(value)) { "The p664_17 field zero has no multiplicative inverse" }
        return Fp664P17(exponentiateField(value, MODULUS.inverseExponent, MODULUS))
    }

    internal fun isSquare(): Boolean = squareRoot() != null

    internal fun squareRoot(): Fp664P17? {
        val candidate =
            if (isZeroFieldBytes(value)) zero()
            else Fp664P17(exponentiateField(value, MODULUS.squareRootExponent, MODULUS))
        val isRoot = candidate.square().compareTo(this) == 0
        val negative = candidate.negate()
        val root = if (candidate.compareTo(negative) <= 0) candidate else negative
        return if (isRoot) root else null
    }

    internal companion object {
        private val MODULUS = FieldModulus(ParameterSet.NIST_V.primeBytes())

        internal fun fromLittleEndian(encoded: ByteArray): Fp664P17 {
            require(encoded.size == ENCODED_BYTES) {
                "p664_17 field values require exactly $ENCODED_BYTES bytes"
            }
            require(compareFieldBytes(encoded, MODULUS.bytes) < 0) {
                "The p664_17 field encoding is not canonical"
            }
            return Fp664P17(encoded.copyOf())
        }

        internal fun zero(): Fp664P17 = Fp664P17(ByteArray(ENCODED_BYTES))

        internal fun one(): Fp664P17 = Fp664P17(ByteArray(ENCODED_BYTES).also { it[0] = 1 })

        internal fun conditionalSwap(
            first: Fp664P17,
            second: Fp664P17,
            swap: Boolean,
        ): Pair<Fp664P17, Fp664P17> = if (swap) second to first else first to second

        internal fun isNegative(value: Fp664P17): Boolean = value.compareTo(value.negate()) > 0

        internal fun select(
            first: Fp664P17,
            second: Fp664P17,
            selectSecond: Boolean,
        ): Fp664P17 = if (selectSecond) second else first

        private const val ENCODED_BYTES: Int = 84
    }
}
