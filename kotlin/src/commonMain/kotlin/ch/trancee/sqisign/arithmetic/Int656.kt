// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * Immutable signed value in [-2^655, 2^655), retaining exact Int328 products.
 *
 * The owned 82-byte two's-complement encoding is not an SQIsign wire format. No timing or
 * secure-erasure guarantee is provided.
 */
internal class Int656 private constructor(private val encoded: ByteArray) {
    /** Exports an owned, exact-width, two's-complement little-endian encoding. */
    internal fun toLittleEndian(): ByteArray = encoded.copyOf()

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
