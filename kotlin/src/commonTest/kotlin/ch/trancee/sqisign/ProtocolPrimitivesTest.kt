// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtocolPrimitivesTest {
    @Test
    fun prngDefaultOutputSliceMatchesIndependentVector() {
        val prng = SqiSignPrng(ByteArray(48) { it.toByte() }, PrngDomain.KEY_GENERATION)
        val output = ByteArray(64)

        try {
            prng.bytesInto(output)

            assertEquals(prngVectors[0], output.toHexString())
        } finally {
            prng.clear()
        }
    }

    @Test
    fun seededDomainsMatchIndependentVectors() {
        for ((index, domain) in PrngDomain.entries.withIndex()) {
            val seed = ByteArray(48) { it.toByte() }
            val prng = SqiSignPrng(seed, domain)
            seed.fill(0)

            val output = prng.bytes(17) + prng.bytes(47)

            assertEquals(prngVectors[index], output.toHexString())
            prng.clear()
        }
    }

    @Test
    fun rejectsIncorrectSeedLengths() {
        for (length in listOf(0, 47, 49)) {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    SqiSignPrng(ByteArray(length), PrngDomain.DEFAULT)
                }

            assertEquals("SQIsign PRNG seeds must contain exactly 48 bytes", error.message)
        }
    }

    @Test
    fun clearedPrngRejectsOutput() {
        val prng = SqiSignPrng(ByteArray(48), PrngDomain.DEFAULT)
        prng.clear()

        val error = assertFailsWith<IllegalStateException> { prng.bytes(1) }

        assertEquals("SHAKE256 state has been cleared", error.message)
    }

    @Test
    fun rejectsNegativePrngOutputLength() {
        val prng = SqiSignPrng(ByteArray(48), PrngDomain.DEFAULT)

        val error = assertFailsWith<IllegalArgumentException> { prng.bytes(-1) }

        assertEquals("Output length must be non-negative", error.message)
        prng.clear()
    }

    @Test
    fun rejectsInvalidPrngOutputSlice() {
        val prng = SqiSignPrng(ByteArray(48), PrngDomain.DEFAULT)

        val error = assertFailsWith<IllegalArgumentException> { prng.bytesInto(ByteArray(3), 1, 3) }

        assertEquals("Invalid byte array range", error.message)
        prng.clear()
    }

    @Test
    fun prngOutputBuffersPreserveStreamAndBounds() {
        val prng = SqiSignPrng(ByteArray(48) { it.toByte() }, PrngDomain.KEY_GENERATION)
        val output = ByteArray(66) { 0x55 }

        prng.bytesInto(output, 1, 64)

        assertEquals(prngVectors[0], output.copyOfRange(1, 65).toHexString())
        assertEquals(0x55.toByte(), output.first())
        assertEquals(0x55.toByte(), output.last())
        prng.clear()
    }

    @Test
    fun domainStreamsAreIndependentOfConsumptionOrder() {
        val seed = ByteArray(48) { it.toByte() }
        val key = SqiSignPrng(seed, PrngDomain.KEY_GENERATION)
        val response = SqiSignPrng(seed, PrngDomain.RESPONSE)
        key.bytes(4096)

        val output = response.bytes(64)

        assertContentEquals(prngVectors[2].hexToByteArray(), output)
        key.clear()
        response.clear()
    }
}
