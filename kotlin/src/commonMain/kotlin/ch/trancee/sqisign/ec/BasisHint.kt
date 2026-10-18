// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.ec

private const val DIRECT_HINT_INDEX_LIMIT = 128

/** Every byte is syntactically valid; only reconstruction can establish a valid basis. */
internal class BasisHint private constructor(internal val encoded: Byte) {
    internal val requiresRegeneration: Boolean
        get() = encoded == 0.toByte()

    internal val searchIndex: Int
        get() = (encoded.toInt() and 0xff) ushr 1

    internal val claimsSquareCoefficient: Boolean
        get() = encoded.toInt() and 1 == 1

    internal companion object {
        internal fun fromEncoded(encoded: Byte): BasisHint = BasisHint(encoded)

        /** Algorithms 4.94-4.95: indices >=128 encode as zero regardless of the residue bit. */
        internal fun fromSearchIndex(index: ByteArray, coefficientIsSquare: Boolean): BasisHint {
            require(index.isNotEmpty() && index.any { it != 0.toByte() }) {
                "Basis search index must be positive unsigned little-endian bytes"
            }
            val low = index[0].toInt() and 0xff
            if (
                low >= DIRECT_HINT_INDEX_LIMIT ||
                    (1 until index.size).any { index[it] != 0.toByte() }
            ) {
                return BasisHint(0)
            }
            val residueBit = if (coefficientIsSquare) 1 else 0
            return BasisHint((2 * low + residueBit).toByte())
        }
    }
}
