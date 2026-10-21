// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

private const val DECIMAL = 10
private const val HEXADECIMAL = 16
private const val DECIMAL_CHUNK = 1_000_000_000L
private const val DECIMAL_CHUNK_DIGITS = 9
private const val HEXADECIMAL_CHUNK = 0x1000_0000L
private const val HEXADECIMAL_CHUNK_DIGITS = 7
private const val LETTER_OFFSET = 10

/** The 64 magnitude bits starting at [offset]. */
internal fun Ibz.extractUnsigned64(offset: Int): Long {
    require(offset >= 0) { "Offset must be non-negative" }
    return IbzBits.shiftRight(magnitude(), offset)[0]
}

/** The low 63 bits of [extractUnsigned64] carrying the value sign. */
internal fun Ibz.extractSigned64(offset: Int): Long {
    val low = extractUnsigned64(offset) and Long.MAX_VALUE
    return if (isNegative) -low else low
}

/** The reference digit-count bound: exact for powers of two, one digit more otherwise. */
internal fun Ibz.sizeInBase(base: Int): Int {
    val bits = bitSize()
    if (base < 2 || bits == 0) {
        return 0
    }
    val digitBits = Int.SIZE_BITS - 1 - base.countLeadingZeroBits()
    val extra = if (base and (base - 1) == 0) 0 else 1
    return (bits + digitBits - 1) / digitBits + extra
}

/** The minimal unsigned words of a non-negative value, least significant first. */
internal fun Ibz.toDigits(): LongArray {
    require(!isNegative) { "Digits require a non-negative value" }
    return toWords().copyOf(IbzWords.limbs(bitSize()))
}

/** Lower-case text in radix 10 or 16 without leading zeros. */
internal fun Ibz.toString(radix: Int): String {
    val (chunk, width) = chunking(radix)
    val digits = IbzDigits.toDigits(magnitude())
    val chunks = ArrayList<Long>()
    do {
        chunks += IbzDigits.divideSmall(digits, chunk)
    } while (!IbzWords.isZero(digits))
    val text = StringBuilder(if (isNegative) "-" else "")
    text.append(chunks.last().toString(radix))
    for (index in chunks.size - 2 downTo 0) {
        text.append(chunks[index].toString(radix).padStart(width, '0'))
    }
    return text.toString()
}

/**
 * Parses optional-sign ASCII text in radix 10 or 16 at bit length `bitSize + 1`, or returns `null`
 * for malformed text and for values the capacity cannot hold, which the reference silently wraps.
 */
internal fun Ibz.Companion.parse(capacityBits: Int, text: String, radix: Int): Ibz? {
    chunking(radix)
    val body = if (text.startsWith('-') || text.startsWith('+')) text.substring(1) else text
    val magnitude = if (body.isEmpty()) null else parseMagnitude(body, radix, capacityBits)
    return magnitude?.let {
        create(capacityBits, IbzWords.bitLength(it) + 1, text.startsWith('-'), it)
    }
}

/** The magnitude of ASCII digits, or `null` for an invalid digit or a value of capacity bits. */
private fun parseMagnitude(body: String, radix: Int, capacityBits: Int): LongArray? {
    val digits = LongArray(capacityBits / IbzDigits.DIGIT_BITS + 1)
    var valid = true
    var index = 0
    while (valid && index < body.length) {
        val digit = asciiDigit(body[index], radix)
        if (digit == null) {
            valid = false
        } else {
            IbzDigits.multiplyAdd(digits, radix.toLong(), digit.toLong())
            valid = IbzWords.bitLength(IbzDigits.fromDigits(digits)) < capacityBits
        }
        index++
    }
    return if (valid) IbzDigits.fromDigits(digits) else null
}

private fun chunking(radix: Int): Pair<Long, Int> =
    when (radix) {
        DECIMAL -> Pair(DECIMAL_CHUNK, DECIMAL_CHUNK_DIGITS)
        HEXADECIMAL -> Pair(HEXADECIMAL_CHUNK, HEXADECIMAL_CHUNK_DIGITS)
        else -> throw IllegalArgumentException("Radix must be 10 or 16")
    }

private fun asciiDigit(character: Char, radix: Int): Int? =
    when (character) {
        in '0'..'9' -> character - '0'
        in 'a'..'f' -> if (radix == HEXADECIMAL) character - 'a' + LETTER_OFFSET else null
        in 'A'..'F' -> if (radix == HEXADECIMAL) character - 'A' + LETTER_OFFSET else null
        else -> null
    }
