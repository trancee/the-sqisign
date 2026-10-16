// SPDX-License-Identifier: Apache-2.0

package org.sqisign

/**
 * SQIsign v3.0 parameters; the prime is [cofactor] * 2^[torsionExponent] - 1.
 *
 * @property identifier Reference implementation's stable parameter-set name.
 * @property securityBits Classical security parameter in bits.
 * @property torsionExponent Exponent of the power-of-two factor in p + 1.
 * @property cofactor Odd factor in p + 1.
 * @property responseBits Specification's response exponent, before two encoding slack bits.
 * @property fieldBytes Width of an unsigned, little-endian base-field encoding.
 */
public enum class ParameterSet(
    public val identifier: String,
    public val securityBits: Int,
    public val torsionExponent: Int,
    public val cofactor: Int,
    public val responseBits: Int,
    public val fieldBytes: Int,
) {
    /** NIST security level I. */
    NIST_I(
        identifier = "p324_3",
        securityBits = 128,
        torsionExponent = 324,
        cofactor = 3,
        responseBits = 196,
        fieldBytes = 41,
    ),
    /** NIST security level III. */
    NIST_III(
        identifier = "p500_27",
        securityBits = 192,
        torsionExponent = 500,
        cofactor = 27,
        responseBits = 302,
        fieldBytes = 64,
    ),
    /** NIST security level V. */
    NIST_V(
        identifier = "p664_17",
        securityBits = 256,
        torsionExponent = 664,
        cofactor = 17,
        responseBits = 400,
        fieldBytes = 84,
    );

    /** Width of two consecutive base-field coefficients. */
    public val extensionFieldBytes: Int
        get() = 2 * fieldBytes

    /** Width of the Fiat-Shamir challenge. */
    public val challengeBytes: Int
        get() = securityBits / 8

    /** Width of one response matrix entry, including two slack bits. */
    public val responseBytes: Int
        get() = (responseBits + 2 + 7) / 8

    /** Width of the Montgomery coefficient and public basis hint. */
    public val publicKeyBytes: Int
        get() = extensionFieldBytes + 1

    /** Specification's secret-key width; secret-key parsing is not implemented. */
    public val secretKeyBytes: Int
        get() = publicKeyBytes + 3 * fieldBytes + 4 * challengeBytes

    /** Width of the auxiliary curve, response matrix, challenge, and two hints. */
    public val signatureBytes: Int
        get() = extensionFieldBytes + 4 * responseBytes + challengeBytes + 2

    /** Returns a fresh little-endian encoding of p, not a field element. */
    public fun primeBytes(): ByteArray {
        val prime = ByteArray(fieldBytes)
        for (bit in 0 until torsionExponent) {
            val index = bit / Byte.SIZE_BITS
            prime[index] = (prime[index].toInt() or (1 shl (bit % Byte.SIZE_BITS))).toByte()
        }
        var high = cofactor - 1
        var bit = torsionExponent
        while (high != 0) {
            if (high and 1 != 0) {
                val index = bit / Byte.SIZE_BITS
                prime[index] = (prime[index].toInt() or (1 shl (bit % Byte.SIZE_BITS))).toByte()
            }
            high = high ushr 1
            bit++
        }
        return prime
    }
}
