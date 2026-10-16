// SPDX-License-Identifier: Apache-2.0

package org.sqisign

/** Canonical byte encodings only. These types do NOT establish curve or signature validity. */
public object WireFormat {
    public fun publicKey(parameters: ParameterSet, encoded: ByteArray): EncodedPublicKey {
        require(encoded.size == parameters.publicKeyBytes) { "Incorrect public key length" }
        checkExtensionField(parameters, encoded, 0)
        return EncodedPublicKey(parameters, encoded)
    }

    public fun signature(parameters: ParameterSet, encoded: ByteArray): EncodedSignature {
        require(encoded.size == parameters.signatureBytes) { "Incorrect signature length" }
        checkExtensionField(parameters, encoded, 0)
        val scalarBits = parameters.responseBits + 2
        val unusedBits = parameters.responseBytes * 8 - scalarBits
        if (unusedBits != 0) {
            for (entry in 0..3) {
                val last = parameters.extensionFieldBytes + (entry + 1) * parameters.responseBytes - 1
                require((encoded[last].toInt() and 0xff) ushr (8 - unusedBits) == 0) {
                    "Response matrix entry exceeds its canonical bit width"
                }
            }
        }
        return EncodedSignature(parameters, encoded)
    }

    internal fun checkExtensionField(parameters: ParameterSet, encoded: ByteArray, offset: Int) {
        require(offset >= 0 && offset <= encoded.size &&
            parameters.extensionFieldBytes <= encoded.size - offset) { "Incorrect extension field length" }
        val prime = parameters.primeBytes()
        for (component in 0..1) {
            val start = offset + component * parameters.fieldBytes
            var comparison = 0
            for (index in prime.lastIndex downTo 0) {
                comparison = (encoded[start + index].toInt() and 0xff).compareTo(prime[index].toInt() and 0xff)
                if (comparison != 0) break
            }
            require(comparison < 0) { "Field element is not a canonical representative below p" }
        }
    }
}

public class EncodedPublicKey internal constructor(
    public val parameters: ParameterSet,
    encoded: ByteArray,
) {
    private val encoding = encoded.copyOf()

    public val hint: Int get() = encoding.last().toInt() and 0xff
    public fun toByteArray(): ByteArray = encoding.copyOf()
    public fun montgomeryCoefficient(): ByteArray = encoding.copyOfRange(0, parameters.extensionFieldBytes)
}

public class EncodedSignature internal constructor(
    public val parameters: ParameterSet,
    encoded: ByteArray,
) {
    private val encoding = encoded.copyOf()

    public val auxiliaryHint: Int get() = encoding[encoding.lastIndex - 1].toInt() and 0xff
    public val challengeHint: Int get() = encoding.last().toInt() and 0xff
    public fun toByteArray(): ByteArray = encoding.copyOf()
    public fun auxiliaryMontgomeryCoefficient(): ByteArray = encoding.copyOfRange(0, parameters.extensionFieldBytes)

    /** Unsigned little-endian entries in row-major order: (0,0), (0,1), (1,0), (1,1). */
    public fun responseEntry(row: Int, column: Int): ByteArray {
        require(row in 0..1 && column in 0..1) { "Matrix indices must be 0 or 1" }
        val start = parameters.extensionFieldBytes + (2 * row + column) * parameters.responseBytes
        return encoding.copyOfRange(start, start + parameters.responseBytes)
    }

    public fun challenge(): ByteArray {
        val start = parameters.extensionFieldBytes + 4 * parameters.responseBytes
        return encoding.copyOfRange(start, start + parameters.challengeBytes)
    }
}
