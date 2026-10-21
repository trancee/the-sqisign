package org.sqisign

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DeterministicKatTest {
    @Test
    fun keygenAndSignReproduceKnownAnswers() {
        assertEquals(3 * ParameterSet.entries.size, KAT_VECTORS.size)
        for (kat in KAT_VECTORS) {
            val set = kat.parameterSet
            val label = "${set.name} count=${kat.count}"
            val message = kat.messageHex.hexToByteArray()
            val signedMessage = kat.signedMessageHex.hexToByteArray()
            val pk = ByteArray(set.publicKeyBytes)
            val sk = ByteArray(set.secretKeyBytes)
            val sig = ByteArray(set.signatureBytes)

            val seed = kat.seedHex.hexToByteArray()

            assertEquals(0, nativeKatKeypairSign(set.nativeId, seed, message, pk, sk, sig), label)
            assertContentEquals(kat.publicKeyHex.hexToByteArray(), pk, "$label pk")
            assertContentEquals(kat.secretKeyHex.hexToByteArray(), sk, "$label sk")
            assertContentEquals(signedMessage.copyOfRange(0, set.signatureBytes), sig, "$label sig")
        }
    }

    @Test
    fun unknownParameterSetIsRejected() {
        val seed = ByteArray(KAT_SEED_BYTES)

        val result =
            nativeKatKeypairSign(
                ParameterSet.entries.size,
                seed,
                ByteArray(1),
                ByteArray(1),
                ByteArray(1),
                ByteArray(1),
            )

        assertEquals(-2, result)
    }

    private companion object {
        /** `SQISIGN_KMP_KAT_SEED_BYTES` in native/include/sqisign_kmp_kat.h. */
        const val KAT_SEED_BYTES = 48
    }
}
