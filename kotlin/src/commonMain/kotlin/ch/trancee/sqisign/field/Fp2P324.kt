// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

import ch.trancee.sqisign.ParameterSet

/**
 * Element of `GF(p324_3^2) = Fp324[i] / (i^2 + 1)`.
 *
 * Components encode as real then imaginary, each in canonical little-endian form. Arithmetic and
 * root selection are variable-time and do not provide a secret-input guarantee.
 */
internal class Fp2P324
private constructor(
    private val real: Fp324,
    private val imaginary: Fp324,
) : QuadraticFieldElement<Fp2P324> {
    /** Returns the canonical real-then-imaginary encoding. */
    override fun toLittleEndian(): ByteArray {
        val encoded = ByteArray(ENCODED_BYTES)
        real.toLittleEndian().copyInto(encoded)
        imaginary.toLittleEndian().copyInto(encoded, Fp324.ENCODED_BYTES)
        return encoded
    }

    /** Returns the component-wise sum. */
    override fun add(other: Fp2P324): Fp2P324 =
        Fp2P324(real.add(other.real), imaginary.add(other.imaginary))

    /** Returns the component-wise difference. */
    override fun subtract(other: Fp2P324): Fp2P324 =
        Fp2P324(real.subtract(other.real), imaginary.subtract(other.imaginary))

    /** Returns the additive inverse. */
    override fun negate(): Fp2P324 = Fp2P324(real.negate(), imaginary.negate())

    /** Orders values by encoded imaginary component first, then real component. */
    override fun compareTo(other: Fp2P324): Int {
        val imaginaryOrder = imaginary.compareTo(other.imaginary)
        return if (imaginaryOrder != 0) imaginaryOrder else real.compareTo(other.real)
    }

    /** Returns the product modulo `i^2 + 1`. */
    override fun multiply(other: Fp2P324): Fp2P324 {
        val realProduct = real.multiply(other.real)
        val imaginaryProduct = imaginary.multiply(other.imaginary)
        val crossProduct = real.add(imaginary).multiply(other.real.add(other.imaginary))
        return Fp2P324(
            realProduct.subtract(imaginaryProduct),
            crossProduct.subtract(realProduct).subtract(imaginaryProduct),
        )
    }

    /** Returns the square modulo `i^2 + 1`. */
    override fun square(): Fp2P324 {
        val realSquare = real.square()
        val imaginarySquare = imaginary.square()
        val crossProduct = real.multiply(imaginary)
        return Fp2P324(
            realSquare.subtract(imaginarySquare),
            crossProduct.add(crossProduct),
        )
    }

    /** Returns the multiplicative inverse; zero is rejected. */
    override fun inverse(): Fp2P324 {
        require(!isZero(this)) { "The p324_3 extension-field zero has no multiplicative inverse" }
        val normInverse = real.square().add(imaginary.square()).inverse()
        return Fp2P324(real.multiply(normInverse), imaginary.negate().multiply(normInverse))
    }

    /** Reports whether this element is a quadratic residue in the extension field. */
    override fun isSquare(): Boolean =
        isZero(this) || real.square().add(imaginary.square()).squareRoot() != null

    /**
     * Returns a root derived from verified base-field roots, canonicalized by encoding, or null.
     */
    override fun squareRoot(): Fp2P324? {
        val normRoot = real.square().add(imaginary.square()).squareRoot() ?: return null
        val inverseTwo = Fp324.fromInt(2).inverse()
        val firstCandidate = squareRootCandidate(real, imaginary, normRoot, inverseTwo)
        val candidate =
            firstCandidate ?: squareRootCandidate(real, imaginary, normRoot.negate(), inverseTwo)
        // A square norm guarantees one of the two delta candidates has verified base roots.
        @Suppress("UnsafeCallOnNullableType") val root = candidate!!
        return canonicalRoot(root)
    }

    /** Reports component-wise equality. */
    override fun isEqualTo(other: Fp2P324): Boolean =
        real.compareTo(other.real) == 0 && imaginary.compareTo(other.imaginary) == 0

    internal companion object : QuadraticField<Fp2P324> {
        override val parameters: ParameterSet = ParameterSet.NIST_I

        override fun conjugate(value: Fp2P324): Fp2P324 =
            Fp2P324(value.real, value.imaginary.negate())

        override fun isSquareInBaseField(value: Fp2P324): Boolean {
            require(value.imaginary.isZero()) {
                "Base-field residue test requires zero imaginary component"
            }
            return value.real.squareRoot() != null
        }

        /** Verified algorithm 4.35 root, without canonicalizing its reference-selected sign. */
        override fun squareRootWithReferenceSign(value: Fp2P324): Fp2P324? {
            val real = value.real
            val imaginary = value.imaginary
            val norm = real.square().add(imaginary.square())
            val delta =
                if (imaginary.isZero()) real else norm.multiply(Fp324.powPMinusThreeOverFour(norm))
            val sum = real.add(delta)
            val twiceSum = sum.add(sum)
            val power = Fp324.powPMinusThreeOverFour(twiceSum)
            val realCandidate = sum.multiply(power)
            val imaginaryCandidate = imaginary.multiply(power)
            val twiceReal = realCandidate.add(realCandidate)
            val candidate =
                if (twiceReal.square().compareTo(twiceSum) == 0) {
                    Fp2P324(realCandidate, imaginaryCandidate)
                } else {
                    Fp2P324(imaginaryCandidate, realCandidate.negate())
                }
            return if (candidate.square().isEqualTo(value)) candidate else null
        }

        private const val ENCODED_BYTES: Int = 2 * Fp324.ENCODED_BYTES

        private fun isZero(value: Fp2P324): Boolean =
            value.real.isZero() && value.imaginary.isZero()

        /** Imports exactly two canonical 41-byte components, real first. */
        override fun fromLittleEndian(encoded: ByteArray): Fp2P324 {
            require(encoded.size == ENCODED_BYTES) {
                "p324_3 extension-field values require exactly 82 bytes"
            }
            return Fp2P324(
                Fp324.fromLittleEndian(encoded.copyOfRange(0, Fp324.ENCODED_BYTES)),
                Fp324.fromLittleEndian(
                    encoded.copyOfRange(Fp324.ENCODED_BYTES, ENCODED_BYTES),
                ),
            )
        }

        /** Creates an extension-field value from canonical components. */
        internal fun fromComponents(real: Fp324, imaginary: Fp324): Fp2P324 =
            Fp2P324(real, imaginary)

        /** Returns extension-field zero. */
        override fun zero(): Fp2P324 = Fp2P324(Fp324.zero(), Fp324.zero())

        /** Returns extension-field one. */
        override fun one(): Fp2P324 = Fp2P324(Fp324.one(), Fp324.zero())

        internal fun conditionalSwap(
            first: Fp2P324,
            second: Fp2P324,
            swap: Boolean,
        ): Pair<Fp2P324, Fp2P324> = if (swap) second to first else first to second

        internal fun isNegative(value: Fp2P324): Boolean = value.compareTo(value.negate()) > 0

        internal fun select(
            first: Fp2P324,
            second: Fp2P324,
            selectSecond: Boolean,
        ): Fp2P324 = if (selectSecond) second else first
    }
}

private fun canonicalRoot(value: Fp2P324): Fp2P324 {
    val negative = value.negate()
    return if (value.compareTo(negative) <= 0) value else negative
}

private fun squareRootCandidate(
    real: Fp324,
    imaginary: Fp324,
    delta: Fp324,
    inverseTwo: Fp324,
): Fp2P324? {
    val realRoot = delta.add(real).multiply(inverseTwo).squareRoot() ?: return null
    val imaginaryRoot =
        if (realRoot.isZero()) {
            real.negate().squareRoot()
        } else {
            imaginary.multiply(realRoot.add(realRoot).inverse())
        }
    val candidate = imaginaryRoot?.let { Fp2P324.fromComponents(realRoot, it) }
    return candidate
}
