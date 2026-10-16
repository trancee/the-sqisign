// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * Immutable unsigned value in [0, 2^656), retaining an exact UInt328 product.
 *
 * The owned 82-byte encoding does not imply field reduction, a signed interpretation, or a
 * timing/secure-erasure guarantee.
 */
internal class UInt656 private constructor(private val encoded: ByteArray) {
    /** Exports an owned, exact-width, unsigned little-endian encoding. */
    internal fun toLittleEndian(): ByteArray = encoded.copyOf()

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
