// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.field

/** Canonical variable-time `Fp[i]/(i^2 + 1)` operations for byte-backed prime fields. */
internal class QuadraticFieldBytes
private constructor(
    private val real: ByteArray,
    private val imaginary: ByteArray,
    private val modulus: FieldModulus,
) {
    internal fun toLittleEndian(): ByteArray = real.copyOf() + imaginary.copyOf()

    internal fun add(other: QuadraticFieldBytes): QuadraticFieldBytes =
        QuadraticFieldBytes(
            addFieldModulo(real, other.real, modulus),
            addFieldModulo(imaginary, other.imaginary, modulus),
            modulus,
        )

    internal fun subtract(other: QuadraticFieldBytes): QuadraticFieldBytes =
        QuadraticFieldBytes(
            subtractFieldModulo(real, other.real, modulus),
            subtractFieldModulo(imaginary, other.imaginary, modulus),
            modulus,
        )

    internal fun negate(): QuadraticFieldBytes =
        QuadraticFieldBytes(
            negateFieldModulo(real, modulus),
            negateFieldModulo(imaginary, modulus),
            modulus,
        )

    internal fun multiply(other: QuadraticFieldBytes): QuadraticFieldBytes {
        val realProduct = multiplyFieldModulo(real, other.real, modulus)
        val imaginaryProduct = multiplyFieldModulo(imaginary, other.imaginary, modulus)
        val crossProduct =
            multiplyFieldModulo(
                addFieldModulo(real, imaginary, modulus),
                addFieldModulo(other.real, other.imaginary, modulus),
                modulus,
            )
        return QuadraticFieldBytes(
            subtractFieldModulo(realProduct, imaginaryProduct, modulus),
            subtractFieldModulo(
                subtractFieldModulo(crossProduct, realProduct, modulus),
                imaginaryProduct,
                modulus,
            ),
            modulus,
        )
    }

    internal fun square(): QuadraticFieldBytes {
        val realSquare = multiplyFieldModulo(real, real, modulus)
        val imaginarySquare = multiplyFieldModulo(imaginary, imaginary, modulus)
        val crossProduct = multiplyFieldModulo(real, imaginary, modulus)
        return QuadraticFieldBytes(
            subtractFieldModulo(realSquare, imaginarySquare, modulus),
            addFieldModulo(crossProduct, crossProduct, modulus),
            modulus,
        )
    }

    internal fun inverse(): QuadraticFieldBytes =
        fromLittleEndian(QuadraticFieldBytesRoots.inverse(toLittleEndian(), modulus), modulus)

    internal fun isSquare(): Boolean = QuadraticFieldBytesRoots.isSquare(toLittleEndian(), modulus)

    internal fun squareRoot(): QuadraticFieldBytes? {
        val root = QuadraticFieldBytesRoots.squareRoot(toLittleEndian(), modulus)
        return if (root == null) null else fromLittleEndian(root, modulus)
    }

    internal fun isEqualTo(other: QuadraticFieldBytes): Boolean =
        compareFieldBytes(real, other.real) == 0 &&
            compareFieldBytes(imaginary, other.imaginary) == 0

    internal fun compareTo(other: QuadraticFieldBytes): Int {
        val imaginaryOrder = compareFieldBytes(imaginary, other.imaginary)
        return if (imaginaryOrder != 0) imaginaryOrder else compareFieldBytes(real, other.real)
    }

    internal companion object {
        internal fun isSquareInBaseField(value: QuadraticFieldBytes): Boolean {
            require(isZeroFieldBytes(value.imaginary)) {
                "Base-field residue test requires zero imaginary component"
            }
            val root =
                exponentiateField(value.real, value.modulus.squareRootExponent, value.modulus)
            return compareFieldBytes(
                multiplyFieldModulo(root, root, value.modulus),
                value.real,
            ) == 0
        }

        internal fun conjugate(value: QuadraticFieldBytes): QuadraticFieldBytes =
            QuadraticFieldBytes(
                value.real,
                negateFieldModulo(value.imaginary, value.modulus),
                value.modulus,
            )

        internal fun squareRootWithReferenceSign(value: QuadraticFieldBytes): QuadraticFieldBytes? {
            val (real, imaginary) =
                quadraticReferenceRootComponents(value.real, value.imaginary, value.modulus)
            val candidate = QuadraticFieldBytes(real, imaginary, value.modulus)
            return if (candidate.square().isEqualTo(value)) candidate else null
        }

        internal fun fromLittleEndian(
            encoded: ByteArray,
            modulus: FieldModulus,
        ): QuadraticFieldBytes {
            val width = modulus.size
            require(encoded.size == 2 * width) {
                "Extension-field values require exactly ${2 * width} bytes"
            }
            val real = encoded.copyOfRange(0, width)
            val imaginary = encoded.copyOfRange(width, 2 * width)
            require(
                compareFieldBytes(real, modulus.bytes) < 0 &&
                    compareFieldBytes(imaginary, modulus.bytes) < 0,
            ) {
                "The extension-field encoding is not canonical"
            }
            return QuadraticFieldBytes(real, imaginary, modulus)
        }

        internal fun zero(modulus: FieldModulus): QuadraticFieldBytes =
            QuadraticFieldBytes(ByteArray(modulus.size), ByteArray(modulus.size), modulus)

        internal fun one(modulus: FieldModulus): QuadraticFieldBytes =
            QuadraticFieldBytes(
                ByteArray(modulus.size).also { it[0] = 1 },
                ByteArray(modulus.size),
                modulus,
            )
    }
}

private object QuadraticFieldBytesRoots {
    fun inverse(encoded: ByteArray, modulus: FieldModulus): ByteArray {
        val (real, imaginary) = components(encoded, modulus.size)
        require(!isZeroFieldBytes(real) || !isZeroFieldBytes(imaginary)) {
            "The extension-field zero has no multiplicative inverse"
        }
        val normInverse = inverseField(norm(real, imaginary, modulus), modulus)
        return encode(
            multiplyFieldModulo(real, normInverse, modulus),
            negateFieldModulo(multiplyFieldModulo(imaginary, normInverse, modulus), modulus),
        )
    }

    fun isSquare(encoded: ByteArray, modulus: FieldModulus): Boolean =
        isZero(encoded, modulus.size) || squareRoot(encoded, modulus) != null

    fun squareRoot(encoded: ByteArray, modulus: FieldModulus): ByteArray? {
        val (real, imaginary) = components(encoded, modulus.size)
        val normRoot = squareRootField(norm(real, imaginary, modulus), modulus)
        if (normRoot == null) return null
        val inverseTwo = inverseField(ByteArray(modulus.size).also { it[0] = 2 }, modulus)
        val candidate =
            squareRootCandidate(real, imaginary, normRoot, inverseTwo, modulus)
                ?: squareRootCandidate(
                    real,
                    imaginary,
                    negateFieldModulo(normRoot, modulus),
                    inverseTwo,
                    modulus,
                )
        // A square norm guarantees one delta candidate has verified component roots.
        @Suppress("UnsafeCallOnNullableType") val root = candidate!!
        return canonicalRoot(root, modulus.size, modulus)
    }

    private fun squareRootCandidate(
        real: ByteArray,
        imaginary: ByteArray,
        delta: ByteArray,
        inverseTwo: ByteArray,
        modulus: FieldModulus,
    ): ByteArray? {
        val realSquare =
            multiplyFieldModulo(addFieldModulo(delta, real, modulus), inverseTwo, modulus)
        val rootReal = squareRootField(realSquare, modulus) ?: return null
        val rootImaginary =
            if (isZeroFieldBytes(rootReal)) {
                // A zero real root forces a zero input imaginary part and a square -real.
                @Suppress("UnsafeCallOnNullableType")
                squareRootField(negateFieldModulo(real, modulus), modulus)!!
            } else {
                val denominator = addFieldModulo(rootReal, rootReal, modulus)
                val inverseDenominator = inverseField(denominator, modulus)
                multiplyFieldModulo(imaginary, inverseDenominator, modulus)
            }
        return encode(rootReal, rootImaginary)
    }

    private fun canonicalRoot(encoded: ByteArray, width: Int, modulus: FieldModulus): ByteArray {
        val (real, imaginary) = components(encoded, width)
        val negativeReal = negateFieldModulo(real, modulus)
        val negativeImaginary = negateFieldModulo(imaginary, modulus)
        val imaginaryOrder = compareFieldBytes(imaginary, negativeImaginary)
        val order =
            if (imaginaryOrder != 0) imaginaryOrder else compareFieldBytes(real, negativeReal)
        return if (order <= 0) encoded else encode(negativeReal, negativeImaginary)
    }

    private fun norm(real: ByteArray, imaginary: ByteArray, modulus: FieldModulus): ByteArray =
        addFieldModulo(
            multiplyFieldModulo(real, real, modulus),
            multiplyFieldModulo(imaginary, imaginary, modulus),
            modulus,
        )

    private fun squareRootField(value: ByteArray, modulus: FieldModulus): ByteArray? {
        val root = exponentiateField(value, modulus.squareRootExponent, modulus)
        return if (compareFieldBytes(multiplyFieldModulo(root, root, modulus), value) == 0) {
            root
        } else {
            null
        }
    }

    private fun inverseField(value: ByteArray, modulus: FieldModulus): ByteArray =
        exponentiateField(value, modulus.inverseExponent, modulus)

    private fun isZero(encoded: ByteArray, width: Int): Boolean {
        val (real, imaginary) = components(encoded, width)
        return isZeroFieldBytes(real) && isZeroFieldBytes(imaginary)
    }

    private fun components(encoded: ByteArray, width: Int): Pair<ByteArray, ByteArray> =
        encoded.copyOfRange(0, width) to encoded.copyOfRange(width, 2 * width)

    private fun encode(real: ByteArray, imaginary: ByteArray): ByteArray =
        real.copyOf() + imaginary.copyOf()
}
