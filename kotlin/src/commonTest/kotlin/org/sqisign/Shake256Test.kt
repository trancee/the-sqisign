// SPDX-License-Identifier: Apache-2.0

package org.sqisign

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Shake256Test {
    @Test
    fun independentVectorsCoverAbsorbAndSqueezeBoundaries() {
        for ((length, expected) in shakeVectors) {
            val input = ByteArray(length) { it.toByte() }

            val output = Shake256.digest(input, 512)

            assertEquals(expected, output.toHexString(), "Input length $length")
        }
    }

    @Test
    fun abcMatchesKnownVector() {
        val input = "abc".encodeToByteArray()

        val output = Shake256.digest(input, 64)

        assertEquals(
            "483366601360a8771c6863080cc4114d8db44530f8f1e1ee4f94ea37e78b5739d5" +
                "a15bef186a5386c75744c0527e1faa9f8726e462a12a4feb06bd8801e751e4",
            output.toHexString(),
        )
    }

    @Test
    fun incrementalAbsorptionAndSqueezingMatchIndependentVectors() {
        for ((length, expected) in shakeVectors) {
            val input = ByteArray(length) { it.toByte() }
            for (chunkSize in listOf(1, 7, 135, 136, 137, 300)) {
                val shake = Shake256()
                var offset = 0
                while (offset < length) {
                    val count = minOf(chunkSize, length - offset)
                    shake.absorb(input, offset, count)
                    offset += count
                }
                val output = ByteArray(514) { 0x55 }
                offset = 1
                while (offset < 513) {
                    val count = minOf(chunkSize, 513 - offset)
                    shake.squeezeInto(output, offset, count)
                    offset += count
                }
                assertEquals(
                    expected,
                    output.copyOfRange(1, 513).toHexString(),
                    "Length $length, chunk $chunkSize",
                )
                assertEquals(0x55.toByte(), output.first())
                assertEquals(0x55.toByte(), output.last())
                shake.clear()
            }
        }
    }

    @Test
    fun rejectsInvalidAbsorptionRanges() {
        val bytes = ByteArray(5)
        val invalidRanges = listOf(-1 to 1, 1 to Int.MAX_VALUE, Int.MAX_VALUE to 0, 0 to -1)
        for ((offset, length) in invalidRanges) {
            val shake = Shake256()

            val error =
                assertFailsWith<IllegalArgumentException> {
                    shake.absorb(bytes, offset, length)
                }

            assertEquals("Invalid byte array range", error.message, "($offset, $length)")
        }
    }

    @Test
    fun rejectsInvalidOutputRanges() {
        val bytes = ByteArray(5)
        val invalidRanges = listOf(0 to Int.MAX_VALUE, -1 to 0, 6 to 0, 0 to -1)
        for ((offset, length) in invalidRanges) {
            val shake = Shake256()

            val error =
                assertFailsWith<IllegalArgumentException> {
                    shake.squeezeInto(bytes, offset, length)
                }

            assertEquals("Invalid byte array range", error.message, "($offset, $length)")
        }
    }

    @Test
    fun rejectsNegativeOutputLength() {
        val shake = Shake256()

        val error = assertFailsWith<IllegalArgumentException> { shake.squeeze(-1) }

        assertEquals("Output length must be non-negative", error.message)
    }

    @Test
    fun zeroLengthSqueezingFinalizesAbsorption() {
        val shake = Shake256()
        val bytes = ByteArray(5)
        shake.absorb(bytes, bytes.size, 0)

        assertContentEquals(ByteArray(0), shake.squeeze(0))
        val error = assertFailsWith<IllegalStateException> { shake.absorb(bytes) }

        assertEquals("Cannot absorb after squeezing", error.message)
    }

    @Test
    fun clearedStateRejectsAllOperationsEvenAfterRepeatedClearing() {
        val operations: List<(Shake256) -> Unit> =
            listOf(
                { it.absorb(ByteArray(0)) },
                { it.squeeze(0) },
                { it.squeezeInto(ByteArray(0)) },
            )
        for (operation in operations) {
            val shake = Shake256()
            shake.clear()
            shake.clear()

            val error = assertFailsWith<IllegalStateException> { operation(shake) }

            assertEquals("SHAKE256 state has been cleared", error.message)
        }
    }
}
