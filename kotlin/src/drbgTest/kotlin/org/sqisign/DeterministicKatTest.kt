package org.sqisign

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Seeds the test-only native library with the NIST CTR-DRBG, then runs keygen and sign; returns 0 on success. The
 * library is built with `-DSQISIGN_KMP_KAT=ON` and never ships in the published artifacts.
 */
internal expect fun nativeKatKeypairSign(
    set: Int,
    seed: ByteArray,
    msg: ByteArray,
    pk: ByteArray,
    sk: ByteArray,
    sig: ByteArray,
): Int

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

            assertEquals(0, nativeKatKeypairSign(set.nativeId, kat.seedHex.hexToByteArray(), message, pk, sk, sig), label)
            assertContentEquals(kat.publicKeyHex.hexToByteArray(), pk, "$label pk")
            assertContentEquals(kat.secretKeyHex.hexToByteArray(), sk, "$label sk")
            assertContentEquals(signedMessage.copyOfRange(0, set.signatureBytes), sig, "$label sig")
        }
    }
}
