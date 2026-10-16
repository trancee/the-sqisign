// SPDX-License-Identifier: Apache-2.0

package org.sqisign

/** SQIsign v3.0 parameters; the prime is [cofactor] * 2^[torsionExponent] - 1. */
public enum class ParameterSet(
    public val identifier: String,
    public val securityBits: Int,
    public val torsionExponent: Int,
    public val cofactor: Int,
    public val responseBits: Int,
    public val fieldBytes: Int,
) {
    NIST_I("p324_3", 128, 324, 3, 196, 41),
    NIST_III("p500_27", 192, 500, 27, 302, 64),
    NIST_V("p664_17", 256, 664, 17, 400, 84);

    public val extensionFieldBytes: Int get() = 2 * fieldBytes
    public val challengeBytes: Int get() = securityBits / 8
    public val responseBytes: Int get() = (responseBits + 2 + 7) / 8
    public val publicKeyBytes: Int get() = extensionFieldBytes + 1
    public val secretKeyBytes: Int get() = publicKeyBytes + 3 * fieldBytes + 4 * challengeBytes
    public val signatureBytes: Int get() = extensionFieldBytes + 4 * responseBytes + challengeBytes + 2

    /** Returns a fresh little-endian encoding of p, not a field element. */
    public fun primeBytes(): ByteArray {
        val prime = ByteArray(fieldBytes)
        for (bit in 0 until torsionExponent) {
            val index = bit / 8
            prime[index] = (prime[index].toInt() or (1 shl (bit % 8))).toByte()
        }
        var high = cofactor - 1
        var bit = torsionExponent
        while (high != 0) {
            if (high and 1 != 0) {
                val index = bit / 8
                prime[index] = (prime[index].toInt() or (1 shl (bit % 8))).toByte()
            }
            high = high ushr 1
            bit++
        }
        return prime
    }
}
