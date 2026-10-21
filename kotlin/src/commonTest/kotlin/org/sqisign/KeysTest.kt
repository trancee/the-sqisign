package org.sqisign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeysTest {
    private val set = ParameterSet.P324_3

    private fun publicKey(fill: Byte, parameterSet: ParameterSet = set) =
        SqiSignPublicKey(parameterSet, ByteArray(parameterSet.publicKeyBytes) { fill })

    private fun secretKey(parameterSet: ParameterSet = set) =
        SqiSignSecretKey(parameterSet, ByteArray(parameterSet.secretKeyBytes) { 7 })

    @Test
    fun publicKeysWithSameSetAndEncodingAreEqual() {
        val first = publicKey(fill = 1)
        val second = publicKey(fill = 1)

        val equal = first == second

        assertTrue(equal)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun publicKeysWithDifferentEncodingsAreNotEqual() {
        val first = publicKey(fill = 1)
        val second = publicKey(fill = 2)

        val equal = first == second

        assertFalse(equal)
    }

    @Test
    fun publicKeysOfDifferentParameterSetsAreNotEqual() {
        val first = publicKey(fill = 1, parameterSet = ParameterSet.P324_3)
        val second = publicKey(fill = 1, parameterSet = ParameterSet.P500_27)

        val equal = first == second

        assertFalse(equal)
    }

    @Test
    fun publicKeyIsNotEqualToItsEncoding() {
        val key = publicKey(fill = 1)

        val equal = key.equals(key.encoded)

        assertFalse(equal)
    }

    @Test
    fun publicKeyEncodingIsADefensiveCopy() {
        val key = publicKey(fill = 1)

        key.encoded[0] = 9

        assertEquals(1, key.encoded[0])
    }

    @Test
    fun publicKeyDescriptionNamesAlgorithmAndSize() {
        val key = publicKey(fill = 1)

        val description = key.toString()

        assertEquals("SqiSignPublicKey(${set.algorithmName}, ${set.publicKeyBytes} bytes)", description)
    }

    @Test
    fun keyPairExposesTheSharedParameterSet() {
        val keyPair =
            SqiSignKeyPair(publicKey(fill = 1, parameterSet = ParameterSet.P500_27), secretKey(ParameterSet.P500_27))

        val parameterSet = keyPair.parameterSet

        assertEquals(ParameterSet.P500_27, parameterSet)
    }

    @Test
    fun keyPairWithDifferentParameterSetsIsRejected() {
        val publicKey = publicKey(fill = 1, parameterSet = ParameterSet.P324_3)
        val secretKey = secretKey(ParameterSet.P500_27)

        val failure = assertFailsWith<IllegalArgumentException> { SqiSignKeyPair(publicKey, secretKey) }

        assertEquals("Key parameter sets differ", failure.message)
    }

    @Test
    fun keyPairDescriptionRedactsTheSecretKey() {
        val keyPair = SqiSignKeyPair(publicKey(fill = 1), secretKey())

        val description = keyPair.toString()

        assertEquals(
            "SqiSignKeyPair(SqiSignPublicKey(${set.algorithmName}, ${set.publicKeyBytes} bytes), " +
                "SqiSignSecretKey(${set.algorithmName}, <redacted>))",
            description,
        )
        assertFalse("7, 7" in description)
    }

    @Test
    fun nativeFailureCodeBecomesSqiSignException() {
        val failure = assertFailsWith<SqiSignException> { SqiSign.checkNativeResult(-1, "signing") }

        assertEquals("SQIsign signing failed (code -1)", failure.message)
    }

    @Test
    fun nativeSuccessCodeIsAccepted() {
        SqiSign.checkNativeResult(0, "signing")
    }
}
