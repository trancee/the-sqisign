// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet

/** Variable-time element of `Fp500P27[i]/(i^2 + 1)` with real-then-imaginary encoding. */
internal class Fp2P500 private constructor(private val value: QuadraticFieldBytes) :
    QuadraticFieldElement<Fp2P500> {
    override fun toLittleEndian(): ByteArray = value.toLittleEndian()

    override fun add(other: Fp2P500): Fp2P500 = Fp2P500(value.add(other.value))

    override fun subtract(other: Fp2P500): Fp2P500 = Fp2P500(value.subtract(other.value))

    override fun negate(): Fp2P500 = Fp2P500(value.negate())

    override fun compareTo(other: Fp2P500): Int = value.compareTo(other.value)

    override fun multiply(other: Fp2P500): Fp2P500 = Fp2P500(value.multiply(other.value))

    override fun square(): Fp2P500 = Fp2P500(value.square())

    override fun inverse(): Fp2P500 = Fp2P500(value.inverse())

    override fun isSquare(): Boolean = value.isSquare()

    override fun squareRoot(): Fp2P500? {
        val root = value.squareRoot()
        return if (root == null) null else Fp2P500(root)
    }

    override fun isEqualTo(other: Fp2P500): Boolean = value.isEqualTo(other.value)

    internal companion object : QuadraticField<Fp2P500> {
        override val parameters: ParameterSet = ParameterSet.NIST_III
        private val MODULUS = FieldModulus(ParameterSet.NIST_III.primeBytes())

        override fun fromLittleEndian(encoded: ByteArray): Fp2P500 =
            Fp2P500(QuadraticFieldBytes.fromLittleEndian(encoded, MODULUS))

        override fun zero(): Fp2P500 = Fp2P500(QuadraticFieldBytes.zero(MODULUS))

        override fun one(): Fp2P500 = Fp2P500(QuadraticFieldBytes.one(MODULUS))

        override fun conjugate(value: Fp2P500): Fp2P500 =
            Fp2P500(QuadraticFieldBytes.conjugate(value.value))

        override fun isSquareInBaseField(value: Fp2P500): Boolean =
            QuadraticFieldBytes.isSquareInBaseField(value.value)

        override fun squareRootWithReferenceSign(value: Fp2P500): Fp2P500? {
            val root = QuadraticFieldBytes.squareRootWithReferenceSign(value.value)
            return if (root == null) null else Fp2P500(root)
        }

        internal fun conditionalSwap(
            first: Fp2P500,
            second: Fp2P500,
            swap: Boolean,
        ): Pair<Fp2P500, Fp2P500> = if (swap) second to first else first to second

        internal fun isNegative(value: Fp2P500): Boolean = value.compareTo(value.negate()) > 0

        internal fun select(
            first: Fp2P500,
            second: Fp2P500,
            selectSecond: Boolean,
        ): Fp2P500 = if (selectSecond) second else first
    }
}
