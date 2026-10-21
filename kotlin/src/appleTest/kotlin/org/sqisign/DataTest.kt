package org.sqisign

import platform.Foundation.NSData
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DataTest {
    @Test
    fun bytesRoundTripThroughNSData() {
        val bytes = ByteArray(300) { it.toByte() }
        val data = nsData(bytes)
        assertEquals(bytes.size.toULong(), data.length)
        assertContentEquals(bytes, byteArray(data))
    }

    @Test
    fun emptyBytesRoundTrip() {
        assertEquals(0UL, nsData(ByteArray(0)).length)
        assertContentEquals(ByteArray(0), byteArray(NSData()))
    }

    @Test
    fun conversionsCopy() {
        val bytes = byteArrayOf(1, 2, 3)
        val data = nsData(bytes)
        bytes[0] = 9
        val copy = byteArray(data)
        copy[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), byteArray(data))
    }

    @Test
    fun signaturesSurviveTheConversion() {
        val keyPair = SqiSign.generateKeyPair(ParameterSet.P324_3)
        val message = nsData("SQIsign".encodeToByteArray())
        val signature = nsData(SqiSign.sign(byteArray(message), keyPair.secretKey))
        assertEquals(true, SqiSign.verify(byteArray(message), byteArray(signature), keyPair.publicKey))
    }
}
