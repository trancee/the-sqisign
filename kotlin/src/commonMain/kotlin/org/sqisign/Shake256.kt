// SPDX-License-Identifier: Apache-2.0

package org.sqisign

/**
 * Byte-oriented FIPS 202 SHAKE256. Instances are mutable and not thread-safe.
 * Absorption ends on the first squeeze, including a zero-length squeeze.
 */
public class Shake256 {
    private val state = LongArray(25)
    private val columns = LongArray(5)
    private val deltas = LongArray(5)
    private val lanes = LongArray(25)
    private var position = 0
    private var squeezing = false
    private var cleared = false

    public fun absorb(input: ByteArray, offset: Int = 0, length: Int = input.size - offset): Shake256 {
        check(!cleared) { "SHAKE256 state has been cleared" }
        check(!squeezing) { "Cannot absorb after squeezing" }
        checkRange(input.size, offset, length)
        for (index in offset until offset + length) {
            xorByte(position, input[index].toInt() and 0xff)
            position++
            if (position == RATE) {
                permute()
                position = 0
            }
        }
        return this
    }

    public fun squeeze(length: Int): ByteArray {
        require(length >= 0) { "Output length must be non-negative" }
        check(!cleared) { "SHAKE256 state has been cleared" }
        return ByteArray(length).also { squeezeInto(it) }
    }

    public fun squeezeInto(output: ByteArray, offset: Int = 0, length: Int = output.size - offset) {
        check(!cleared) { "SHAKE256 state has been cleared" }
        checkRange(output.size, offset, length)
        if (!squeezing) {
            xorByte(position, 0x1f)
            xorByte(RATE - 1, 0x80)
            permute()
            position = 0
            squeezing = true
        }
        for (index in offset until offset + length) {
            if (position == RATE) {
                permute()
                position = 0
            }
            output[index] = (state[position / 8] ushr (8 * (position % 8))).toByte()
            position++
        }
    }

    /** Best-effort state overwrite, not a managed-runtime secure-erasure guarantee. */
    public fun clear() {
        state.fill(0)
        columns.fill(0)
        deltas.fill(0)
        lanes.fill(0)
        position = 0
        cleared = true
    }

    private fun xorByte(index: Int, value: Int) {
        state[index / 8] = state[index / 8] xor (value.toLong() shl (8 * (index % 8)))
    }

    private fun permute() {
        for (constant in ROUND_CONSTANTS) {
            for (x in 0..4) {
                columns[x] = state[x] xor state[x + 5] xor state[x + 10] xor state[x + 15] xor state[x + 20]
            }
            for (x in 0..4) {
                deltas[x] = columns[(x + 4) % 5] xor columns[(x + 1) % 5].rotateLeft(1)
            }
            for (y in 0..4) {
                for (x in 0..4) {
                    val index = x + 5 * y
                    val destination = y + 5 * ((2 * x + 3 * y) % 5)
                    lanes[destination] = (state[index] xor deltas[x]).rotateLeft(ROTATIONS[index])
                }
            }
            for (y in 0..4) {
                for (x in 0..4) {
                    state[x + 5 * y] = lanes[x + 5 * y] xor
                        (lanes[(x + 1) % 5 + 5 * y].inv() and lanes[(x + 2) % 5 + 5 * y])
                }
            }
            state[0] = state[0] xor constant
        }
    }

    public companion object {
        private const val RATE = 136
        private val ROTATIONS = intArrayOf(
            0, 1, 62, 28, 27,
            36, 44, 6, 55, 20,
            3, 10, 43, 25, 39,
            41, 45, 15, 21, 8,
            18, 2, 61, 56, 14,
        )
        private val ROUND_CONSTANTS = longArrayOf(
            0x0000000000000001L, 0x0000000000008082L,
            0x800000000000808aUL.toLong(), 0x8000000080008000UL.toLong(),
            0x000000000000808bL, 0x0000000080000001L,
            0x8000000080008081UL.toLong(), 0x8000000000008009UL.toLong(),
            0x000000000000008aL, 0x0000000000000088L,
            0x0000000080008009L, 0x000000008000000aL,
            0x000000008000808bL, 0x800000000000008bUL.toLong(),
            0x8000000000008089UL.toLong(), 0x8000000000008003UL.toLong(),
            0x8000000000008002UL.toLong(), 0x8000000000000080UL.toLong(),
            0x000000000000800aL, 0x800000008000000aUL.toLong(),
            0x8000000080008081UL.toLong(), 0x8000000000008080UL.toLong(),
            0x0000000080000001L, 0x8000000080008008UL.toLong(),
        )

        public fun digest(input: ByteArray, outputLength: Int): ByteArray {
            val context = Shake256()
            return try {
                context.absorb(input).squeeze(outputLength)
            } finally {
                context.clear()
            }
        }

        private fun checkRange(size: Int, offset: Int, length: Int) {
            require(offset >= 0 && offset <= size && length >= 0 && length <= size - offset) {
                "Invalid byte array range"
            }
        }
    }
}
