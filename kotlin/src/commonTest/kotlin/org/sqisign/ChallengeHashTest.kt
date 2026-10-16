// SPDX-License-Identifier: Apache-2.0

package org.sqisign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ChallengeHashTest {
    @Test
    fun transcriptMatchesIndependentVectorsForAllParameters() {
        val message = "Kotlin SQIsign".encodeToByteArray() + byteArrayOf(0, -1)
        for ((index, parameters) in ParameterSet.entries.withIndex()) {
            val publicKey = referencePublicKey(parameters)
            val commitment = referenceCommitment(parameters)

            val challenge = ChallengeHash.compute(publicKey, commitment, message)

            assertEquals(challengeVectors[index], challenge.toHexString(), parameters.identifier)
        }
    }

    @Test
    fun appendingZeroToMessageChangesChallenge() {
        val message = "Kotlin SQIsign".encodeToByteArray() + byteArrayOf(0, -1)
        for ((index, parameters) in ParameterSet.entries.withIndex()) {
            val publicKey = referencePublicKey(parameters)
            val commitment = referenceCommitment(parameters)

            val challenge = ChallengeHash.compute(publicKey, commitment, message + 0.toByte())

            assertFalse(
                challenge.contentEquals(challengeVectors[index].hexToByteArray()),
                parameters.identifier,
            )
        }
    }

    @Test
    fun changingPublicKeyHintChangesChallenge() {
        val message = "Kotlin SQIsign".encodeToByteArray() + byteArrayOf(0, -1)
        for ((index, parameters) in ParameterSet.entries.withIndex()) {
            val publicKey = referencePublicKey(parameters).toByteArray()
            publicKey[publicKey.lastIndex] = 0
            val key = WireFormat.publicKey(parameters, publicKey)
            val commitment = referenceCommitment(parameters)

            val challenge = ChallengeHash.compute(key, commitment, message)

            assertFalse(
                challenge.contentEquals(challengeVectors[index].hexToByteArray()),
                parameters.identifier,
            )
        }
    }

    @Test
    fun rejectsIncorrectCommitmentLength() {
        for (parameters in ParameterSet.entries) {
            val key = referencePublicKey(parameters)
            for (length in
                listOf(0, parameters.extensionFieldBytes - 1, parameters.extensionFieldBytes + 1)) {
                val commitment = ByteArray(length)

                val error =
                    assertFailsWith<IllegalArgumentException> {
                        ChallengeHash.compute(key, commitment, ByteArray(0))
                    }

                assertEquals("Incorrect commitment coefficient length", error.message)
            }
        }
    }

    @Test
    fun rejectsNonCanonicalCommitment() {
        for (parameters in ParameterSet.entries) {
            val key = referencePublicKey(parameters)
            val commitment = parameters.primeBytes() + ByteArray(parameters.fieldBytes)

            val error =
                assertFailsWith<IllegalArgumentException> {
                    ChallengeHash.compute(key, commitment, ByteArray(0))
                }

            assertEquals("Field element is not a canonical representative below p", error.message)
        }
    }

    private fun referencePublicKey(parameters: ParameterSet): EncodedPublicKey {
        val encoded = ByteArray(parameters.publicKeyBytes)
        encoded[0] = 1
        encoded[encoded.lastIndex] = -1
        return WireFormat.publicKey(parameters, encoded)
    }

    private fun referenceCommitment(parameters: ParameterSet): ByteArray =
        ByteArray(parameters.extensionFieldBytes).also { it[0] = 2 }
}
