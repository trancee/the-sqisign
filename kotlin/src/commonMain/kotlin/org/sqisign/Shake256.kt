// SPDX-License-Identifier: Apache-2.0

package org.sqisign

/**
 * Byte-oriented FIPS 202 SHAKE256. Instances are mutable and not thread-safe. Absorption ends on
 * the first squeeze, including a zero-length squeeze.
 */
public class Shake256 {
    private val state = LongArray(STATE_LANES)
    private val columns = LongArray(STATE_DIMENSION)
    private val deltas = LongArray(STATE_DIMENSION)
    private val lanes = LongArray(STATE_LANES)
    private var position = 0
    private var squeezing = false
    private var cleared = false

    /** Absorbs a valid input slice; rejects use after squeezing or clearing. */
    public fun absorb(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset,
    ): Shake256 {
        check(!cleared) { "SHAKE256 state has been cleared" }
        check(!squeezing) { "Cannot absorb after squeezing" }
        checkRange(input.size, offset, length)
        for (index in offset until offset + length) {
            xorByte(position, input[index].toInt() and UNSIGNED_BYTE_MASK)
            position++
            if (position == RATE) {
                permute()
                position = 0
            }
        }
        return this
    }

    /** Consumes the next [length] bytes; zero bytes still finalize absorption. */
    public fun squeeze(length: Int): ByteArray {
        require(length >= 0) { "Output length must be non-negative" }
        check(!cleared) { "SHAKE256 state has been cleared" }
        return ByteArray(length).also { squeezeInto(it) }
    }

    /** Consumes into a valid output slice, rejecting invalid ranges before state mutation. */
    public fun squeezeInto(output: ByteArray, offset: Int = 0, length: Int = output.size - offset) {
        check(!cleared) { "SHAKE256 state has been cleared" }
        checkRange(output.size, offset, length)
        if (!squeezing) {
            xorByte(position, SHAKE_SUFFIX)
            xorByte(RATE - 1, FINAL_PADDING_BIT)
            permute()
            position = 0
            squeezing = true
        }
        for (index in offset until offset + length) {
            if (position == RATE) {
                permute()
                position = 0
            }
            output[index] =
                (state[position / Long.SIZE_BYTES] ushr
                        (Byte.SIZE_BITS * (position % Long.SIZE_BYTES)))
                    .toByte()
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
        state[index / Long.SIZE_BYTES] =
            state[index / Long.SIZE_BYTES] xor
                (value.toLong() shl (Byte.SIZE_BITS * (index % Long.SIZE_BYTES)))
    }

    private fun permute() {
        for (constant in ROUND_CONSTANTS) {
            for (x in 0 until STATE_DIMENSION) {
                columns[x] =
                    state[x] xor
                        state[x + STATE_DIMENSION] xor
                        state[x + 2 * STATE_DIMENSION] xor
                        state[x + THIRD_ROW_OFFSET] xor
                        state[x + LAST_COORDINATE * STATE_DIMENSION]
            }
            for (x in 0 until STATE_DIMENSION) {
                deltas[x] =
                    columns[(x + LAST_COORDINATE) % STATE_DIMENSION] xor
                        columns[(x + 1) % STATE_DIMENSION].rotateLeft(1)
            }
            for (y in 0 until STATE_DIMENSION) {
                for (x in 0 until STATE_DIMENSION) {
                    val index = x + STATE_DIMENSION * y
                    val destination =
                        y + STATE_DIMENSION * ((2 * x + PI_Y_MULTIPLIER * y) % STATE_DIMENSION)
                    lanes[destination] = (state[index] xor deltas[x]).rotateLeft(ROTATIONS[index])
                }
            }
            for (y in 0 until STATE_DIMENSION) {
                for (x in 0 until STATE_DIMENSION) {
                    state[x + STATE_DIMENSION * y] =
                        lanes[x + STATE_DIMENSION * y] xor
                            (lanes[(x + 1) % STATE_DIMENSION + STATE_DIMENSION * y].inv() and
                                lanes[(x + 2) % STATE_DIMENSION + STATE_DIMENSION * y])
                }
            }
            state[0] = state[0] xor constant
        }
    }

    /** One-shot hashing that clears its temporary sponge after use. */
    public companion object {
        private const val RATE = 136
        private const val STATE_DIMENSION = 5
        private const val STATE_LANES = STATE_DIMENSION * STATE_DIMENSION
        private const val LAST_COORDINATE = STATE_DIMENSION - 1
        private const val PI_Y_MULTIPLIER = 3
        private const val THIRD_ROW_OFFSET = 15
        private const val UNSIGNED_BYTE_MASK = 0xff
        private const val SHAKE_SUFFIX = 0x1f
        private const val FINAL_PADDING_BIT = 0x80
        private val ROTATIONS =
            intArrayOf(
                0,
                1,
                62,
                28,
                27,
                36,
                44,
                6,
                55,
                20,
                3,
                10,
                43,
                25,
                39,
                41,
                45,
                15,
                21,
                8,
                18,
                2,
                61,
                56,
                14,
            )
        private val ROUND_CONSTANTS =
            longArrayOf(
                0x0000000000000001L,
                0x0000000000008082L,
                0x800000000000808aUL.toLong(),
                0x8000000080008000UL.toLong(),
                0x000000000000808bL,
                0x0000000080000001L,
                0x8000000080008081UL.toLong(),
                0x8000000000008009UL.toLong(),
                0x000000000000008aL,
                0x0000000000000088L,
                0x0000000080008009L,
                0x000000008000000aL,
                0x000000008000808bL,
                0x800000000000008bUL.toLong(),
                0x8000000000008089UL.toLong(),
                0x8000000000008003UL.toLong(),
                0x8000000000008002UL.toLong(),
                0x8000000000000080UL.toLong(),
                0x000000000000800aL,
                0x800000008000000aUL.toLong(),
                0x8000000080008081UL.toLong(),
                0x8000000000008080UL.toLong(),
                0x0000000080000001L,
                0x8000000080008008UL.toLong(),
            )

        /** Hashes [input] into [outputLength] bytes; rejects negative output lengths. */
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
