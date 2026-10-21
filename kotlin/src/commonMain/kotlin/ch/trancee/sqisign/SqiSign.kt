// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import ch.trancee.sqisign.field.Fp2P324
import ch.trancee.sqisign.field.Fp2P500
import ch.trancee.sqisign.field.Fp2P664

/** SQIsign v3 signature verification; every operation is variable-time. */
public object SqiSign {
    /**
     * Algorithm 3.24: whether [signature] is a valid signature of [message] under [publicKey].
     *
     * Returns `false` for every mathematically invalid key or signature, including singular or
     * non-supersingular curves, unusable hints, unnormalized response matrices and failed
     * isogenies. Throws [IllegalArgumentException] only when the key and signature belong to
     * different parameter sets. Not constant-time.
     */
    public fun verify(
        publicKey: EncodedPublicKey,
        message: ByteArray,
        signature: EncodedSignature,
    ): Boolean {
        require(publicKey.parameters == signature.parameters) {
            "Public key and signature must use the same parameter set"
        }
        return when (publicKey.parameters) {
            ParameterSet.NIST_I -> verifySignature(Fp2P324, publicKey, message, signature)
            ParameterSet.NIST_III -> verifySignature(Fp2P500, publicKey, message, signature)
            ParameterSet.NIST_V -> verifySignature(Fp2P664, publicKey, message, signature)
        }
    }
}
