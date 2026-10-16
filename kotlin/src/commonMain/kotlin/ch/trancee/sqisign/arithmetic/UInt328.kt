// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * Immutable unsigned integer in [0, 2^328), occupying the p324_3 encoding width.
 *
 * The 22 little-endian radix-2^15 limbs are normalized; the top limb has 13 bits. This is not a
 * field element: values at or above the field prime remain representable. Comparison is
 * variable-time; no timing or secure-erasure guarantee is provided.
 */
internal class UInt328 private constructor(private val limbs: IntArray) {
    /** Exports an owned, exact-width, unsigned little-endian encoding. */
    internal fun toLittleEndian(): ByteArray =
        ByteArray(ENCODED_BYTES) { index ->
            val bit = index * BYTE_BITS
            val limb = bit / LIMB_BITS
            val shift = bit % LIMB_BITS
            var value = limbs[limb] ushr shift
            if (shift + BYTE_BITS > LIMB_BITS) {
                value = value or (limbs[limb + 1] shl (LIMB_BITS - shift))
            }
            value.toByte()
        }

    /** Returns -1, 0, or 1 according to the unsigned mathematical order. */
    internal operator fun compareTo(other: UInt328): Int {
        for (index in LAST_LIMB downTo 0) {
            if (limbs[index] != other.limbs[index]) {
                return limbs[index].compareTo(other.limbs[index])
            }
        }
        return 0
    }

    /** Returns the low 328 bits and carry, so no overflow is silently discarded. */
    internal fun add(other: UInt328): Addition {
        val output = IntArray(LIMB_COUNT)
        var carry = 0
        for (index in 0 until LAST_LIMB) {
            val sum = limbs[index] + other.limbs[index] + carry
            output[index] = sum and LIMB_MASK
            carry = sum ushr LIMB_BITS
        }
        val topSum = limbs[LAST_LIMB] + other.limbs[LAST_LIMB] + carry
        output[LAST_LIMB] = topSum and TOP_MASK
        return Addition(UInt328(output), topSum ushr TOP_BITS)
    }

    /** Returns the low 328 bits and borrow; value = this - other + borrow * 2^328. */
    internal fun subtract(other: UInt328): Subtraction {
        val output = IntArray(LIMB_COUNT)
        var borrow = 0
        for (index in 0 until LAST_LIMB) {
            val difference = limbs[index] - other.limbs[index] - borrow
            output[index] = difference and LIMB_MASK
            borrow = difference ushr (Int.SIZE_BITS - 1)
        }
        val topDifference = limbs[LAST_LIMB] - other.limbs[LAST_LIMB] - borrow
        output[LAST_LIMB] = topDifference and TOP_MASK
        return Subtraction(UInt328(output), topDifference ushr (Int.SIZE_BITS - 1))
    }

    /** Exact sum = value + carry * 2^328, with carry in {0, 1}. */
    internal class Addition
    internal constructor(internal val value: UInt328, internal val carry: Int)

    /** Exact difference = value - borrow * 2^328, with borrow in {0, 1}. */
    internal class Subtraction
    internal constructor(internal val value: UInt328, internal val borrow: Int)

    internal companion object {
        internal const val ENCODED_BYTES: Int = 41

        internal fun fromLittleEndian(encoded: ByteArray): UInt328 {
            require(encoded.size == ENCODED_BYTES) {
                "Unsigned 328-bit values require exactly 41 bytes"
            }
            val limbs = IntArray(LIMB_COUNT)
            for (index in encoded.indices) {
                val byte = encoded[index].toInt() and BYTE_MASK
                val bit = index * BYTE_BITS
                val limb = bit / LIMB_BITS
                val shift = bit % LIMB_BITS
                limbs[limb] = limbs[limb] or ((byte shl shift) and LIMB_MASK)
                if (shift + BYTE_BITS > LIMB_BITS) {
                    limbs[limb + 1] = limbs[limb + 1] or (byte ushr (LIMB_BITS - shift))
                }
            }
            return UInt328(limbs)
        }
    }
}

// Addition fits in [0, 65535]; subtraction fits in [-32768, 32767].
private const val LIMB_BITS = 15
private const val LIMB_COUNT = 22
private const val LAST_LIMB = LIMB_COUNT - 1
private const val LIMB_MASK = (1 shl LIMB_BITS) - 1
private const val TOP_BITS = 13
private const val TOP_MASK = (1 shl TOP_BITS) - 1
private const val BYTE_BITS = 8
private const val BYTE_MASK = (1 shl BYTE_BITS) - 1
