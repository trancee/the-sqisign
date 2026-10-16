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
            assertEquals(expected, Shake256.digest(input, 512).toHexString(), "Input length $length")
        }
        assertEquals(
            "483366601360a8771c6863080cc4114d8db44530f8f1e1ee4f94ea37e78b5739d5" +
                "a15bef186a5386c75744c0527e1faa9f8726e462a12a4feb06bd8801e751e4",
            Shake256.digest("abc".encodeToByteArray(), 64).toHexString(),
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
                assertEquals(expected, output.copyOfRange(1, 513).toHexString())
                assertEquals(0x55.toByte(), output.first())
                assertEquals(0x55.toByte(), output.last())
                shake.clear()
            }
        }
    }

    @Test
    fun rejectsInvalidRangesAndStateTransitions() {
        val shake = Shake256()
        val bytes = ByteArray(5)
        assertFailsWith<IllegalArgumentException> { shake.absorb(bytes, -1, 1) }
        assertFailsWith<IllegalArgumentException> { shake.absorb(bytes, 1, Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { shake.absorb(bytes, Int.MAX_VALUE, 0) }
        assertFailsWith<IllegalArgumentException> { shake.absorb(bytes, 0, -1) }
        assertFailsWith<IllegalArgumentException> { shake.squeeze(-1) }
        assertFailsWith<IllegalArgumentException> { shake.squeezeInto(bytes, 0, Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { shake.squeezeInto(bytes, -1, 0) }
        assertFailsWith<IllegalArgumentException> { shake.squeezeInto(bytes, 6, 0) }
        shake.absorb(bytes, bytes.size, 0)
        assertContentEquals(ByteArray(0), shake.squeeze(0))
        assertFailsWith<IllegalStateException> { shake.absorb(bytes) }
        shake.clear()
        shake.clear()
        assertFailsWith<IllegalStateException> { shake.absorb(bytes) }
        assertFailsWith<IllegalStateException> { shake.squeeze(0) }
        assertFailsWith<IllegalStateException> { shake.squeezeInto(bytes) }
    }
}
