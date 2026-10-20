package org.sqisign

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SqiSignTest {
    private val message = "SQIsign over Kotlin Multiplatform".encodeToByteArray()

    @Test
    fun sizesMatchNativeCore() {
        for (set in ParameterSet.entries) {
            val sizes = IntArray(3)
            assertEquals(0, nativeSizes(set.nativeId, sizes))
            assertContentEquals(intArrayOf(set.publicKeyBytes, set.secretKeyBytes, set.signatureBytes), sizes, set.name)
        }
    }

    @Test
    fun invalidNativeSetIsRejected() {
        assertEquals(-2, nativeSizes(3, IntArray(3)))
    }

    @Test
    fun signAndVerifyRoundTripForEveryParameterSet() {
        for (set in ParameterSet.entries) {
            val keyPair = SqiSign.generateKeyPair(set)
            val signature = SqiSign.sign(message, keyPair.secretKey)

            assertEquals(set.signatureBytes, signature.size)
            assertTrue(SqiSign.verify(message, signature, keyPair.publicKey), set.name)
        }
    }

    @Test
    fun tamperedInputsAreRejected() {
        val keyPair = SqiSign.generateKeyPair()
        val signature = SqiSign.sign(message, keyPair.secretKey)
        val otherKey = SqiSign.generateKeyPair().publicKey

        val tamperedMessage = message.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val tamperedSignature = signature.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }

        assertFalse(SqiSign.verify(tamperedMessage, signature, keyPair.publicKey))
        assertFalse(SqiSign.verify(message, tamperedSignature, keyPair.publicKey))
        assertFalse(SqiSign.verify(message, signature, otherKey))
        assertFalse(SqiSign.verify(message, signature.copyOf(signature.size - 1), keyPair.publicKey))
    }

    @Test
    fun emptyMessageIsSupported() {
        val keyPair = SqiSign.generateKeyPair()
        val signature = SqiSign.sign(ByteArray(0), keyPair.secretKey)

        assertTrue(SqiSign.verify(ByteArray(0), signature, keyPair.publicKey))
        assertFalse(SqiSign.verify(byteArrayOf(0), signature, keyPair.publicKey))
    }

    @Test
    fun keysAreRoundTrippedThroughTheirEncoding() {
        val keyPair = SqiSign.generateKeyPair()
        val restoredSecret = SqiSignSecretKey(ParameterSet.P324_3, keyPair.secretKey.encoded)
        val restoredPublic = SqiSignPublicKey(ParameterSet.P324_3, keyPair.publicKey.encoded)

        val signature = SqiSign.sign(message, restoredSecret)

        assertEquals(keyPair.publicKey, restoredPublic)
        assertTrue(SqiSign.verify(message, signature, restoredPublic))
    }

    @Test
    fun wrongKeySizesAreRejected() {
        assertFailsWith<IllegalArgumentException> { SqiSignPublicKey(ParameterSet.P324_3, ByteArray(82)) }
        assertFailsWith<IllegalArgumentException> { SqiSignSecretKey(ParameterSet.P500_27, ByteArray(270)) }
    }

    @Test
    fun closedSecretKeyIsWipedAndUnusable() {
        val secretKey = SqiSign.generateKeyPair().secretKey
        val before = secretKey.encoded

        secretKey.close()

        assertTrue(secretKey.isClosed)
        assertNotEquals(0, before.count { it != 0.toByte() })
        assertFailsWith<IllegalStateException> { secretKey.encoded }
        assertFailsWith<IllegalStateException> { SqiSign.sign(message, secretKey) }
        assertTrue("<redacted>" in secretKey.toString())
    }

    @Test
    fun knownAnswerSignaturesVerify() {
        assertEquals(3 * ParameterSet.entries.size, KAT_VECTORS.size)
        for (kat in KAT_VECTORS) {
            val set = kat.parameterSet
            val signedMessage = kat.signedMessageHex.hexToByteArray()
            val signature = signedMessage.copyOfRange(0, set.signatureBytes)
            val message = kat.messageHex.hexToByteArray()
            val publicKey = SqiSignPublicKey(set, kat.publicKeyHex.hexToByteArray())

            assertEquals(kat.messageLength, message.size)
            assertContentEquals(message, signedMessage.copyOfRange(set.signatureBytes, signedMessage.size))
            assertTrue(SqiSign.verify(message, signature, publicKey), "${set.name} count=${kat.count}")
            assertFalse(SqiSign.verify(message + 0.toByte(), signature, publicKey), "${set.name} count=${kat.count}")
        }
    }
}
