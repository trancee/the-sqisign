// SPDX-License-Identifier: Apache-2.0

package org.sqisign

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal data class ReferenceVector(val parameters: ParameterSet, val publicKey: String, val signature: String)

class WireFormatTest {
    @Test
    fun sizesAndPrimesMatchSpecification() {
        val sizes = listOf(listOf(83, 270, 200), listOf(129, 417, 306), listOf(169, 549, 406))
        val topBytes = listOf(0x2f, 0x01, 0x10)
        for ((index, parameters) in ParameterSet.entries.withIndex()) {
            assertEquals(
                sizes[index],
                listOf(parameters.publicKeyBytes, parameters.secretKeyBytes, parameters.signatureBytes),
            )
            val prime = parameters.primeBytes()
            assertEquals(parameters.fieldBytes, prime.size)
            assertEquals(topBytes[index], prime.last().toInt() and 0xff)
            val hexadecimal = when (parameters) {
                ParameterSet.NIST_I -> "2" + "f".repeat(81)
                ParameterSet.NIST_III -> "01a" + "f".repeat(125)
                ParameterSet.NIST_V -> "10" + "f".repeat(166)
            }
            assertContentEquals(hexadecimal.hexToByteArray().reversedArray(), prime)
            prime.fill(0)
            assertEquals(-1, parameters.primeBytes()[0].toInt())
        }
    }

    @Test
    fun publicReferenceVectorsRoundTripWithoutAliasing() {
        for (vector in referenceVectors) {
            val parameters = vector.parameters
            val pkBytes = vector.publicKey.hexToByteArray()
            val sigBytes = vector.signature.hexToByteArray()
            val key = WireFormat.publicKey(parameters, pkBytes)
            val sig = WireFormat.signature(parameters, sigBytes)
            assertContentEquals(pkBytes, key.toByteArray())
            assertContentEquals(sigBytes, sig.toByteArray())
            assertContentEquals(pkBytes.copyOfRange(0, parameters.extensionFieldBytes), key.montgomeryCoefficient())
            assertContentEquals(
                sigBytes.copyOfRange(0, parameters.extensionFieldBytes),
                sig.auxiliaryMontgomeryCoefficient(),
            )
            for (row in 0..1) {
                for (column in 0..1) {
                    val start = parameters.extensionFieldBytes + (row * 2 + column) * parameters.responseBytes
                    assertContentEquals(
                        sigBytes.copyOfRange(start, start + parameters.responseBytes),
                        sig.responseEntry(row, column),
                    )
                }
            }
            val challengeStart = parameters.extensionFieldBytes + 4 * parameters.responseBytes
            assertContentEquals(
                sigBytes.copyOfRange(challengeStart, challengeStart + parameters.challengeBytes),
                sig.challenge(),
            )
            assertEquals(pkBytes.last().toInt() and 0xff, key.hint)
            assertEquals(sigBytes[sigBytes.lastIndex - 1].toInt() and 0xff, sig.auxiliaryHint)
            assertEquals(sigBytes.last().toInt() and 0xff, sig.challengeHint)
            pkBytes.fill(0)
            sigBytes.fill(0)
            key.toByteArray().fill(0)
            key.montgomeryCoefficient().fill(0)
            sig.toByteArray().fill(0)
            sig.auxiliaryMontgomeryCoefficient().fill(0)
            sig.responseEntry(0, 0).fill(0)
            sig.challenge().fill(0)
            assertEquals(vector.publicKey, key.toByteArray().toHexString())
            assertEquals(vector.signature, sig.toByteArray().toHexString())
            assertFailsWith<IllegalArgumentException> { sig.responseEntry(-1, 0) }
            assertFailsWith<IllegalArgumentException> { sig.responseEntry(0, 2) }
        }
    }

    @Test
    fun rejectsWrongLengthsAndNonCanonicalFields() {
        for (parameters in ParameterSet.entries) {
            for (length in listOf(0, parameters.publicKeyBytes - 1, parameters.publicKeyBytes + 1)) {
                assertFailsWith<IllegalArgumentException> { WireFormat.publicKey(parameters, ByteArray(length)) }
            }
            for (length in listOf(0, parameters.signatureBytes - 1, parameters.signatureBytes + 1)) {
                assertFailsWith<IllegalArgumentException> { WireFormat.signature(parameters, ByteArray(length)) }
            }
            val prime = parameters.primeBytes()
            val primeMinusOne = prime.copyOf().also { it[0] = (it[0] - 1).toByte() }
            for (component in 0..1) {
                val key = ByteArray(parameters.publicKeyBytes)
                val signature = ByteArray(parameters.signatureBytes)
                prime.copyInto(key, component * parameters.fieldBytes)
                prime.copyInto(signature, component * parameters.fieldBytes)
                assertFailsWith<IllegalArgumentException> { WireFormat.publicKey(parameters, key) }
                assertFailsWith<IllegalArgumentException> { WireFormat.signature(parameters, signature) }
                primeMinusOne.copyInto(key, component * parameters.fieldBytes)
                primeMinusOne.copyInto(signature, component * parameters.fieldBytes)
                WireFormat.publicKey(parameters, key)
                WireFormat.signature(parameters, signature)
                key[component * parameters.fieldBytes + parameters.fieldBytes - 1] = -1
                assertFailsWith<IllegalArgumentException> { WireFormat.publicKey(parameters, key) }
            }
        }
    }

    @Test
    fun rejectsUnusedResponseBitsButDoesNotPretendToVerify() {
        for (parameters in ParameterSet.entries) {
            // Zero is syntactically canonical but is not a valid SQIsign signature.
            val zero = ByteArray(parameters.signatureBytes)
            WireFormat.signature(parameters, zero)
            val scalarBits = parameters.responseBits + 2
            val remainder = scalarBits % 8
            for (entry in 0..3) {
                val input = zero.copyOf()
                val last = parameters.extensionFieldBytes + (entry + 1) * parameters.responseBytes - 1
                if (remainder != 0) {
                    input[last] = (1 shl remainder).toByte()
                    assertFailsWith<IllegalArgumentException> { WireFormat.signature(parameters, input) }
                    input[last] = ((1 shl remainder) - 1).toByte()
                } else {
                    input[last] = -1
                }
                WireFormat.signature(parameters, input)
            }
        }
    }
}
