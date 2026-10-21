// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

/** Mutable copy of the public KAT record 0 of [parameters]. */
internal class KatRecordZero(val parameters: ParameterSet) {
    private val vector = referenceVectors.single { it.parameters == parameters }
    val publicKeyBytes = vector.publicKey.hexToByteArray()
    val message = vector.message.hexToByteArray()
    val signature = vector.signature.hexToByteArray()

    fun publicKey(): EncodedPublicKey = WireFormat.publicKey(parameters, publicKeyBytes)

    fun verify(): Boolean =
        SqiSign.verify(publicKey(), message, WireFormat.signature(parameters, signature))
}
