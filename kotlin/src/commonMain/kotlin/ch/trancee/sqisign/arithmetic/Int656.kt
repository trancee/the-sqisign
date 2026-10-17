// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * Immutable signed value in [-2^655, 2^655), retaining exact wide products and quotients.
 *
 * The owned 82-byte two's-complement encoding is not an SQIsign wire format. No timing or
 * secure-erasure guarantee is provided.
 */
internal class Int656 private constructor(private val encoded: ByteArray) {
    /** Exports an owned, exact-width, two's-complement little-endian encoding. */
    internal fun toLittleEndian(): ByteArray = encoded.copyOf()

    /**
     * Returns a quotient truncated toward zero and a remainder with the dividend's sign.
     *
     * Division is variable-time; zero divisors and the unrepresentable minimum / -1 quotient fail.
     */
    internal fun divideAndRemainder(divisor: Int328): Division {
        val divisorBytes = divisor.toLittleEndian()
        require(divisorBytes.any { it != 0.toByte() }) {
            "Signed division requires a nonzero divisor"
        }
        val dividendNegative = encoded[encoded.lastIndex] < 0
        val divisorNegative = divisorBytes[divisorBytes.lastIndex] < 0
        val dividendMagnitude = magnitude(encoded)
        val divisorMagnitude = magnitude(divisorBytes)
        val quotient =
            UInt656.fromLittleEndian(dividendMagnitude)
                .divideAndRemainder(UInt328.fromLittleEndian(divisorMagnitude))
        val quotientBytes = quotient.quotient.toLittleEndian()
        val quotientNegative = dividendNegative != divisorNegative
        if (!quotientNegative && quotientBytes[quotientBytes.lastIndex] < 0) {
            throw ArithmeticException("Signed 656-bit division overflow")
        }
        val signedQuotient = if (quotientNegative) negate(quotientBytes) else quotientBytes
        val remainderBytes = quotient.remainder.toLittleEndian()
        val signedRemainder = if (dividendNegative) negate(remainderBytes) else remainderBytes
        return Division(
            Int656.fromLittleEndian(signedQuotient),
            Int328.fromLittleEndian(signedRemainder),
        )
    }

    private fun magnitude(value: ByteArray): ByteArray =
        if (value[value.lastIndex] < 0) negate(value) else value.copyOf()

    private fun negate(value: ByteArray): ByteArray {
        val result = value.copyOf()
        var carry = 1
        for (index in result.indices) {
            val negated = (result[index].toInt().inv() and BYTE_MASK) + carry
            result[index] = negated.toByte()
            carry = negated ushr Byte.SIZE_BITS
        }
        return result
    }

    /** Exact wide signed quotient and narrow signed remainder. */
    internal class Division
    internal constructor(internal val quotient: Int656, internal val remainder: Int328)

    internal companion object {
        internal const val ENCODED_BYTES: Int = UInt656.ENCODED_BYTES

        internal fun fromLittleEndian(encoded: ByteArray): Int656 {
            require(encoded.size == ENCODED_BYTES) {
                "Signed 656-bit values require exactly 82 bytes"
            }

            return Int656(encoded.copyOf())
        }
    }
}

private const val BYTE_MASK = 0xff
