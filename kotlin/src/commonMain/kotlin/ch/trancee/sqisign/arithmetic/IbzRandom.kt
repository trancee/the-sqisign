// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

private const val SAMPLE_MARGIN_BITS = 64
private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFFL

/**
 * Samples `[low, high]` as the reference `ibz_rand_interval`: reads `ceil((high.bitlen + 64) / 8)`
 * bytes, reduces them modulo the span and re-bounds the result to `high.bitlen`.
 */
internal fun Ibz.Companion.randomInterval(
    low: Ibz,
    high: Ibz,
    randomBytes: (Int) -> ByteArray,
): Ibz {
    require(low <= high) { "Empty interval" }
    val capacity = high.capacityBits
    val span =
        high
            .sum(low, subtract = true, checked = false)
            .sum(one(capacity), subtract = false, checked = false)
    val bits = high.bitlen + SAMPLE_MARGIN_BITS
    val length = (bits + BYTE_BITS - 1) / BYTE_BITS
    val bytes = randomBytes(length)
    require(bytes.size == length) { "Random source returned ${bytes.size} of $length bytes" }
    val words = LongArray(IbzWords.limbs(length * BYTE_BITS))
    for (index in bytes.indices) {
        val shift = BYTE_BITS * (index % Long.SIZE_BYTES)
        words[index / Long.SIZE_BYTES] =
            words[index / Long.SIZE_BYTES] or ((bytes[index].toLong() and BYTE_MASK) shl shift)
    }
    val sample = copyBits(capacity, words, bits)
    val reduced = sample.sum(span, subtract = false, checked = false).mod(span)
    return low.sum(reduced, subtract = false, checked = false).withBound(high.bitlen)
}

/** Samples `[-bound, bound]` as the reference `ibz_rand_interval_minm_m`. */
internal fun Ibz.Companion.randomSymmetric(
    capacityBits: Int,
    bound: Int,
    randomBytes: (Int) -> ByteArray,
): Ibz {
    require(bound > 0) { "Bound must be positive" }
    val limit = of(capacityBits, bound, Int.SIZE_BITS)
    val doubled = limit.sum(limit, subtract = false, checked = false)
    val sample = randomInterval(zero(capacityBits), doubled, randomBytes)
    return sample.sum(limit, subtract = true, checked = false).withBound(Int.SIZE_BITS)
}
