// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * Immutable unsigned value in [0, 2^656), retaining exact products and wide quotients.
 *
 * The owned 82-byte encoding does not imply field reduction, a signed interpretation, or a
 * timing/secure-erasure guarantee.
 */
internal class UInt656 private constructor(private val encoded: ByteArray) {
    /** Exports an owned, exact-width, unsigned little-endian encoding. */
    internal fun toLittleEndian(): ByteArray = encoded.copyOf()

    /**
     * Returns an exact wide quotient and a remainder below the nonzero divisor.
     *
     * Division is variable-time; a zero divisor is rejected before producing any result.
     */
    internal fun divideAndRemainder(divisor: UInt328): Division {
        val divisorBytes = divisor.toLittleEndian()
        require(divisorBytes.any { it != 0.toByte() }) {
            "Unsigned division requires a nonzero divisor"
        }
        val quotient = ByteArray(ENCODED_BYTES)
        var remainder = IntArray(UInt328.ENCODED_BYTES + 1)
        var difference = IntArray(UInt328.ENCODED_BYTES + 1)
        for (bit in ENCODED_BYTES * BYTE_BITS - 1 downTo 0) {
            var carry = (encoded[bit / BYTE_BITS].toInt() ushr (bit % BYTE_BITS)) and 1
            // One extra byte retains bit 328 of the trial remainder, which is below 2 * divisor.
            for (index in 0..UInt328.ENCODED_BYTES) {
                val shifted = (remainder[index] shl 1) or carry
                remainder[index] = shifted and BYTE_MASK
                carry = shifted ushr BYTE_BITS
            }
            if (subtractDivisor(remainder, divisorBytes, difference)) {
                val previous = remainder
                remainder = difference
                difference = previous
                quotient[bit / BYTE_BITS] =
                    (quotient[bit / BYTE_BITS].toInt() or (1 shl (bit % BYTE_BITS))).toByte()
            }
        }
        return Division(
            UInt656(quotient),
            UInt328.fromLittleEndian(ByteArray(UInt328.ENCODED_BYTES) { remainder[it].toByte() }),
        )
    }

    private fun subtractDivisor(left: IntArray, right: ByteArray, output: IntArray): Boolean {
        var borrow = 0
        for (index in 0 until UInt328.ENCODED_BYTES) {
            val difference = left[index] - (right[index].toInt() and BYTE_MASK) - borrow
            output[index] = difference and BYTE_MASK
            borrow = difference ushr (Int.SIZE_BITS - 1)
        }
        output[UInt328.ENCODED_BYTES] = left[UInt328.ENCODED_BYTES] - borrow
        return output[UInt328.ENCODED_BYTES] >= 0
    }

    /** Exact unsigned result with independently owned quotient and remainder storage. */
    internal class Division
    internal constructor(internal val quotient: UInt656, internal val remainder: UInt328)

    internal companion object {
        internal const val ENCODED_BYTES: Int = 82

        internal fun fromLittleEndian(encoded: ByteArray): UInt656 {
            require(encoded.size == ENCODED_BYTES) {
                "Unsigned 656-bit values require exactly 82 bytes"
            }

            return UInt656(encoded.copyOf())
        }
    }
}

private const val BYTE_BITS = 8
private const val BYTE_MASK = (1 shl BYTE_BITS) - 1
