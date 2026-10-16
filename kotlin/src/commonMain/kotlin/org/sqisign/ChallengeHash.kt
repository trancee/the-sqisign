// SPDX-License-Identifier: Apache-2.0

package org.sqisign

/** SQIsign v3 Fiat-Shamir transcript hashing; this does not verify a signature. */
public object ChallengeHash {
    /**
     * Algorithm 3.3: SHAKE256("SQI" || encoded public key || encoded commitment A || message,
     * lambda). The caller must supply the normalized Montgomery A, NOT the curve's j-invariant.
     * Canonical encoding is checked, but curve membership and supersingularity are not.
     */
    public fun compute(
        publicKey: EncodedPublicKey,
        commitmentMontgomeryCoefficient: ByteArray,
        message: ByteArray,
    ): ByteArray {
        val parameters = publicKey.parameters
        require(commitmentMontgomeryCoefficient.size == parameters.extensionFieldBytes) {
            "Incorrect commitment coefficient length"
        }
        WireFormat.checkExtensionField(parameters, commitmentMontgomeryCoefficient, 0)
        val context = Shake256()
        return try {
            context
                .absorb("SQI".encodeToByteArray())
                .absorb(publicKey.toByteArray())
                .absorb(commitmentMontgomeryCoefficient)
                .absorb(message)
                .squeeze(parameters.challengeBytes)
        } finally {
            context.clear()
        }
    }
}
