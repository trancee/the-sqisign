// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

/** Canonical byte encodings only. These types do NOT establish curve or signature validity. */
public object WireFormat {
    /** Copies a canonical public-key encoding or rejects its length/field representatives. */
    public fun publicKey(parameters: ParameterSet, encoded: ByteArray): EncodedPublicKey {
        require(encoded.size == parameters.publicKeyBytes) { "Incorrect public key length" }
        checkExtensionField(parameters, encoded, 0)
        return EncodedPublicKey(parameters, encoded)
    }

    /** Copies a canonical signature encoding; this does not establish mathematical validity. */
    public fun signature(parameters: ParameterSet, encoded: ByteArray): EncodedSignature {
        require(encoded.size == parameters.signatureBytes) { "Incorrect signature length" }
        checkExtensionField(parameters, encoded, 0)
        val scalarBits = parameters.responseBits + 2
        val unusedBits = parameters.responseBytes * 8 - scalarBits
        if (unusedBits != 0) {
            for (entry in 0 until RESPONSE_MATRIX_ENTRIES) {
                val last =
                    parameters.extensionFieldBytes + (entry + 1) * parameters.responseBytes - 1
                require(
                    (encoded[last].toInt() and UNSIGNED_BYTE_MASK) ushr
                        (Byte.SIZE_BITS - unusedBits) == 0,
                ) {
                    "Response matrix entry exceeds its canonical bit width"
                }
            }
        }
        return EncodedSignature(parameters, encoded)
    }

    internal fun checkExtensionField(parameters: ParameterSet, encoded: ByteArray, offset: Int) {
        require(
            offset >= 0 &&
                offset <= encoded.size &&
                parameters.extensionFieldBytes <= encoded.size - offset,
        ) {
            "Incorrect extension field length"
        }
        val prime = parameters.primeBytes()
        for (component in 0..1) {
            val start = offset + component * parameters.fieldBytes
            var comparison = 0
            for (index in prime.lastIndex downTo 0) {
                comparison =
                    (encoded[start + index].toInt() and UNSIGNED_BYTE_MASK).compareTo(
                        prime[index].toInt() and UNSIGNED_BYTE_MASK,
                    )
                if (comparison != 0) break
            }
            require(comparison < 0) { "Field element is not a canonical representative below p" }
        }
    }
}

/**
 * Immutable canonical public-key bytes, not a validated curve.
 *
 * @property parameters Parameter set used to validate the encoding.
 */
public class EncodedPublicKey
internal constructor(
    public val parameters: ParameterSet,
    encoded: ByteArray,
) {
    private val encoding = encoded.copyOf()

    /** Unsigned basis hint; its mathematical validity is not checked. */
    public val hint: Int
        get() = encoding.last().toInt() and 0xff

    /** Returns an independent copy of the full encoding. */
    public fun toByteArray(): ByteArray = encoding.copyOf()

    /** Returns independent little-endian real and imaginary coefficients of Montgomery A. */
    public fun montgomeryCoefficient(): ByteArray =
        encoding.copyOfRange(0, parameters.extensionFieldBytes)
}

/**
 * Immutable canonical signature bytes, not a verified signature.
 *
 * @property parameters Parameter set used to validate the encoding.
 */
public class EncodedSignature
internal constructor(
    public val parameters: ParameterSet,
    encoded: ByteArray,
) {
    private val encoding = encoded.copyOf()

    /** Unsigned auxiliary basis hint; its mathematical validity is not checked. */
    public val auxiliaryHint: Int
        get() = encoding[encoding.lastIndex - 1].toInt() and 0xff

    /** Unsigned challenge basis hint; its mathematical validity is not checked. */
    public val challengeHint: Int
        get() = encoding.last().toInt() and 0xff

    /** Returns an independent copy of the full encoding. */
    public fun toByteArray(): ByteArray = encoding.copyOf()

    /** Returns independent little-endian coefficients of the auxiliary Montgomery A. */
    public fun auxiliaryMontgomeryCoefficient(): ByteArray =
        encoding.copyOfRange(0, parameters.extensionFieldBytes)

    /** Unsigned little-endian entries in row-major order: (0,0), (0,1), (1,0), (1,1). */
    public fun responseEntry(row: Int, column: Int): ByteArray {
        require(row in 0..1 && column in 0..1) { "Matrix indices must be 0 or 1" }
        val start = parameters.extensionFieldBytes + (2 * row + column) * parameters.responseBytes
        return encoding.copyOfRange(start, start + parameters.responseBytes)
    }

    /** Returns an independent copy of the encoded Fiat-Shamir challenge. */
    public fun challenge(): ByteArray {
        val start = parameters.extensionFieldBytes + 4 * parameters.responseBytes
        return encoding.copyOfRange(start, start + parameters.challengeBytes)
    }
}

private const val RESPONSE_MATRIX_ENTRIES = 4
private const val UNSIGNED_BYTE_MASK = 0xff
