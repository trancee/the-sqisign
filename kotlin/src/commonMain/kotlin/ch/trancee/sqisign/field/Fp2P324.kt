// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

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
) {
    /** Returns the canonical real-then-imaginary encoding. */
    internal fun toLittleEndian(): ByteArray {
        val encoded = ByteArray(ENCODED_BYTES)
        real.toLittleEndian().copyInto(encoded)
        imaginary.toLittleEndian().copyInto(encoded, Fp324.ENCODED_BYTES)
        return encoded
    }

    /** Returns the component-wise sum. */
    internal fun add(other: Fp2P324): Fp2P324 =
        Fp2P324(real.add(other.real), imaginary.add(other.imaginary))

    /** Returns the component-wise difference. */
    internal fun subtract(other: Fp2P324): Fp2P324 =
        Fp2P324(real.subtract(other.real), imaginary.subtract(other.imaginary))

    /** Returns the additive inverse. */
    internal fun negate(): Fp2P324 = Fp2P324(real.negate(), imaginary.negate())

    /** Orders values by encoded imaginary component first, then real component. */
    internal fun compareTo(other: Fp2P324): Int {
        val imaginaryOrder = imaginary.compareTo(other.imaginary)
        return if (imaginaryOrder != 0) imaginaryOrder else real.compareTo(other.real)
    }

    /** Returns the product modulo `i^2 + 1`. */
    internal fun multiply(other: Fp2P324): Fp2P324 {
        val realProduct = real.multiply(other.real)
        val imaginaryProduct = imaginary.multiply(other.imaginary)
        val crossProduct = real.add(imaginary).multiply(other.real.add(other.imaginary))
        return Fp2P324(
            realProduct.subtract(imaginaryProduct),
            crossProduct.subtract(realProduct).subtract(imaginaryProduct),
        )
    }

    /** Returns the square modulo `i^2 + 1`. */
    internal fun square(): Fp2P324 {
        val realSquare = real.square()
        val imaginarySquare = imaginary.square()
        val crossProduct = real.multiply(imaginary)
        return Fp2P324(
            realSquare.subtract(imaginarySquare),
            crossProduct.add(crossProduct),
        )
    }

    /** Returns the multiplicative inverse; zero is rejected. */
    internal fun inverse(): Fp2P324 {
        require(!isZero(this)) { "The p324_3 extension-field zero has no multiplicative inverse" }
        val normInverse = real.square().add(imaginary.square()).inverse()
        return Fp2P324(real.multiply(normInverse), imaginary.negate().multiply(normInverse))
    }

    /** Reports whether this element is a quadratic residue in the extension field. */
    internal fun isSquare(): Boolean =
        isZero(this) || real.square().add(imaginary.square()).squareRoot() != null

    /**
     * Returns a root derived from verified base-field roots, canonicalized by encoding, or null.
     */
    internal fun squareRoot(): Fp2P324? {
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
    internal fun isEqualTo(other: Fp2P324): Boolean =
        real.compareTo(other.real) == 0 && imaginary.compareTo(other.imaginary) == 0

    internal companion object {
        private const val ENCODED_BYTES: Int = 2 * Fp324.ENCODED_BYTES

        private fun isZero(value: Fp2P324): Boolean =
            value.real.isZero() && value.imaginary.isZero()

        private fun canonicalRoot(value: Fp2P324): Fp2P324 {
            val negative = value.negate()
            return if (value.compareTo(negative) <= 0) value else negative
        }

        /** Imports exactly two canonical 41-byte components, real first. */
        internal fun fromLittleEndian(encoded: ByteArray): Fp2P324 {
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
        internal fun zero(): Fp2P324 = Fp2P324(Fp324.zero(), Fp324.zero())

        /** Returns extension-field one. */
        internal fun one(): Fp2P324 = Fp2P324(Fp324.one(), Fp324.zero())

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
