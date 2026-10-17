// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet

/** Variable-time element of `Fp664P17[i]/(i^2 + 1)` with real-then-imaginary encoding. */
internal class Fp2P664 private constructor(private val value: QuadraticFieldBytes) {
    internal fun toLittleEndian(): ByteArray = value.toLittleEndian()

    internal fun add(other: Fp2P664): Fp2P664 = Fp2P664(value.add(other.value))

    internal fun subtract(other: Fp2P664): Fp2P664 = Fp2P664(value.subtract(other.value))

    internal fun negate(): Fp2P664 = Fp2P664(value.negate())

    internal fun compareTo(other: Fp2P664): Int = value.compareTo(other.value)

    internal fun multiply(other: Fp2P664): Fp2P664 = Fp2P664(value.multiply(other.value))

    internal fun square(): Fp2P664 = Fp2P664(value.square())

    internal fun inverse(): Fp2P664 = Fp2P664(value.inverse())

    internal fun isSquare(): Boolean = value.isSquare()

    internal fun squareRoot(): Fp2P664? {
        val root = value.squareRoot()
        return if (root == null) null else Fp2P664(root)
    }

    internal fun isEqualTo(other: Fp2P664): Boolean = value.isEqualTo(other.value)

    internal companion object {
        private val MODULUS = ParameterSet.NIST_V.primeBytes()

        internal fun fromLittleEndian(encoded: ByteArray): Fp2P664 =
            Fp2P664(QuadraticFieldBytes.fromLittleEndian(encoded, MODULUS))

        internal fun zero(): Fp2P664 = Fp2P664(QuadraticFieldBytes.zero(MODULUS))

        internal fun one(): Fp2P664 = Fp2P664(QuadraticFieldBytes.one(MODULUS))

        internal fun conditionalSwap(
            first: Fp2P664,
            second: Fp2P664,
            swap: Boolean,
        ): Pair<Fp2P664, Fp2P664> = if (swap) second to first else first to second

        internal fun isNegative(value: Fp2P664): Boolean = value.compareTo(value.negate()) > 0

        internal fun select(
            first: Fp2P664,
            second: Fp2P664,
            selectSecond: Boolean,
        ): Fp2P664 = if (selectSecond) second else first
    }
}
