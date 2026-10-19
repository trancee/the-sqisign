// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Decodes generated `op level arguments = results` records from the independent Python model. */
internal class IbzRecord(text: String) {
    private val sides = text.split(" = ")
    private val query = sides[0].split(" ")
    internal val operation: String = query[0]
    internal val capacity: Int = CAPACITIES.getValue(query[1])
    internal val arguments: List<String> = query.drop(2)
    internal val expected: String = sides[1]
    internal val description: String = text.take(DESCRIPTION_LENGTH)

    internal fun ibz(index: Int): Ibz = decodeIbz(capacity, arguments[index])

    internal fun int(index: Int): Int = arguments[index].toInt()

    /** Asserts the textual result, or a capacity rejection where C silently wraps. */
    internal fun assertResult(compute: () -> String) {
        if (expected.startsWith("! ")) {
            assertFailsWith<ArithmeticException>(description) { compute() }
        } else {
            assertEquals(expected, compute(), description)
        }
    }

    internal companion object {
        private const val DESCRIPTION_LENGTH = 160
        private val CAPACITIES = mapOf("p324_3" to 1920, "p500_27" to 2560, "p664_17" to 3520)

        internal fun all(
            operation: String,
            source: List<String> = IbzVectors.records,
        ): List<IbzRecord> {
            val records = source.map(::IbzRecord).filter { it.operation == operation }
            check(records.isNotEmpty()) { "No $operation records" }
            return records
        }

        internal fun decodeIbz(capacity: Int, text: String): Ibz {
            val bitlen = text.substringBefore(':').toInt()
            val signed = text.substringAfter(':')
            val count = (bitlen + Long.SIZE_BITS - 1) / Long.SIZE_BITS
            val words = magnitudeWords(signed.removePrefix("-"), count)
            if (signed.startsWith("-")) {
                var carry = 1L
                for (index in words.indices) {
                    val inverted = words[index].inv()
                    words[index] = inverted + carry
                    carry = if (carry == 1L && words[index] == 0L) 1L else 0L
                }
            }
            return Ibz.fromWords(capacity, bitlen, words)
        }

        internal fun magnitudeWords(hex: String, count: Int): LongArray {
            val words = LongArray(count)
            for ((offset, character) in hex.reversed().withIndex()) {
                val digit = character.digitToInt(HEX_RADIX).toLong()
                words[offset / NIBBLES] =
                    words[offset / NIBBLES] or (digit shl (NIBBLE_BITS * (offset % NIBBLES)))
            }
            return words
        }

        /** Signed hexadecimal text of the two's-complement value of the stored words. */
        internal fun encode(value: Ibz): String {
            val words = value.toWords()
            val negative = words.last() < 0
            if (negative) {
                var carry = 1L
                for (index in words.indices) {
                    words[index] = words[index].inv() + carry
                    carry = if (carry == 1L && words[index] == 0L) 1L else 0L
                }
            }
            val digits =
                words.reversed().joinToString("") {
                    it.toULong().toString(HEX_RADIX).padStart(NIBBLES, '0')
                }
            val trimmed = digits.trimStart('0').ifEmpty { "0" }
            return "${value.bitlen}:${if (negative) "-" else ""}$trimmed"
        }

        private const val HEX_RADIX = 16
        private const val NIBBLES = 16
        private const val NIBBLE_BITS = 4
    }
}
