// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import ch.trancee.sqisign.arithmetic.IbzWords.WORD_BITS
import ch.trancee.sqisign.arithmetic.IbzWords.limbs

/** Variable-time shift, truncation and sign-extension kernels on little-endian words. */
internal object IbzBits {
    /** Logical right shift of the stored words, keeping the word count. */
    internal fun shiftRight(words: LongArray, exponent: Int): LongArray {
        val wordShift = exponent / WORD_BITS
        val bitShift = exponent % WORD_BITS
        return LongArray(words.size) { index ->
            val low = words.wordAt(index + wordShift) ushr bitShift
            val high =
                if (bitShift == 0) 0L
                else words.wordAt(index + wordShift + 1) shl (WORD_BITS - bitShift)
            low or high
        }
    }

    internal fun shiftLeft(words: LongArray, exponent: Int, size: Int): LongArray {
        val wordShift = exponent / WORD_BITS
        val bitShift = exponent % WORD_BITS
        return LongArray(size) { index ->
            val low = words.signedWordAt(index - wordShift) shl bitShift
            val high =
                if (bitShift == 0) 0L
                else words.signedWordAt(index - wordShift - 1) ushr (WORD_BITS - bitShift)
            low or high
        }
    }

    /** The value modulo `2^bits` in `limbs(bits)` words. */
    internal fun truncate(words: LongArray, bits: Int): LongArray {
        val result = words.copyOf(limbs(bits))
        val partial = bits % WORD_BITS
        if (partial != 0) {
            result[result.size - 1] = result[result.size - 1] and ((1L shl partial) - 1)
        }
        return result
    }

    /** Sign-extends the low `bits` bits into `limbs(bits)` two's-complement words. */
    internal fun signExtend(words: LongArray, bits: Int): LongArray {
        val result = words.copyOf(limbs(bits))
        val unused = WORD_BITS - bits % WORD_BITS
        if (unused != WORD_BITS) {
            result[result.size - 1] = (result[result.size - 1] shl unused) shr unused
        }
        return result
    }

    private fun LongArray.signedWordAt(index: Int): Long = if (index in indices) this[index] else 0L
}
