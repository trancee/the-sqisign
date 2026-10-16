// SPDX-License-Identifier: Apache-2.0

package org.sqisign

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ProtocolPrimitivesTest {
    @Test
    fun seededDomainsMatchIndependentVectors() {
        for ((index, domain) in PrngDomain.entries.withIndex()) {
            val seed = ByteArray(48) { it.toByte() }
            val prng = SqisignPrng(seed, domain)
            seed.fill(0)
            val output = prng.bytes(17) + prng.bytes(47)
            assertEquals(prngVectors[index], output.toHexString())
            prng.clear()
            assertFailsWith<IllegalStateException> { prng.bytes(1) }
        }
        for (length in listOf(0, 47, 49)) {
            assertFailsWith<IllegalArgumentException> { SqisignPrng(ByteArray(length), PrngDomain.DEFAULT) }
        }
    }

    @Test
    fun prngOutputBuffersPreserveStreamAndBounds() {
        val prng = SqisignPrng(ByteArray(48) { it.toByte() }, PrngDomain.KEY_GENERATION)
        assertFailsWith<IllegalArgumentException> { prng.bytes(-1) }
        assertFailsWith<IllegalArgumentException> { prng.bytesInto(ByteArray(3), 1, 3) }
        val output = ByteArray(66) { 0x55 }
        prng.bytesInto(output, 1, 64)
        assertEquals(prngVectors[0], output.copyOfRange(1, 65).toHexString())
        assertEquals(0x55.toByte(), output.first())
        assertEquals(0x55.toByte(), output.last())
        prng.clear()
    }

    @Test
    fun challengeTranscriptMatchesIndependentVectorsForAllParameters() {
        val message = "Kotlin SQIsign".encodeToByteArray() + byteArrayOf(0, -1)
        for ((index, parameters) in ParameterSet.entries.withIndex()) {
            val pk = ByteArray(parameters.publicKeyBytes)
            pk[0] = 1
            pk[pk.lastIndex] = -1
            val key = WireFormat.publicKey(parameters, pk)
            val commitment = ByteArray(parameters.extensionFieldBytes)
            commitment[0] = 2
            val challenge = ChallengeHash.compute(key, commitment, message)
            assertEquals(challengeVectors[index], challenge.toHexString())
            assertFalse(challenge.contentEquals(ChallengeHash.compute(key, commitment, message + 0.toByte())))
            pk[pk.lastIndex] = 0
            assertFalse(
                challenge.contentEquals(
                    ChallengeHash.compute(WireFormat.publicKey(parameters, pk), commitment, message),
                ),
            )
            assertFailsWith<IllegalArgumentException> {
                ChallengeHash.compute(key, commitment.copyOf(commitment.size - 1), message)
            }
            assertFailsWith<IllegalArgumentException> {
                ChallengeHash.compute(key, parameters.primeBytes() + ByteArray(parameters.fieldBytes), message)
            }
        }
    }

    @Test
    fun domainStreamsAreIndependentOfConsumptionOrder() {
        val seed = ByteArray(48) { it.toByte() }
        val key = SqisignPrng(seed, PrngDomain.KEY_GENERATION)
        val response = SqisignPrng(seed, PrngDomain.RESPONSE)
        key.bytes(4096)
        assertContentEquals(prngVectors[2].hexToByteArray(), response.bytes(64))
        key.clear()
        response.clear()
    }
}
