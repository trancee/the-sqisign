// SPDX-License-Identifier: Apache-2.0

package org.sqisign

public enum class PrngDomain(internal val separator: String) {
    KEY_GENERATION("key"),
    COMMITMENT("com"),
    RESPONSE("res"),
    DEFAULT("def"),
}

/**
 * Specification algorithms 3.1-3.2: SHAKE256(seed || domain), with a 48-byte seed.
 * This is deterministic expansion, NOT an entropy source. Callers must supply secure seeds.
 * Instances are mutable and not thread-safe; no seed is retained separately.
 */
public class SqisignPrng(seed: ByteArray, domain: PrngDomain) {
    private val context: Shake256

    init {
        require(seed.size == 48) { "SQIsign PRNG seeds must contain exactly 48 bytes" }
        context = Shake256().absorb(seed).absorb(domain.separator.encodeToByteArray())
    }

    public fun bytes(length: Int): ByteArray = context.squeeze(length)

    public fun bytesInto(output: ByteArray, offset: Int = 0, length: Int = output.size - offset) {
        context.squeezeInto(output, offset, length)
    }

    public fun clear() {
        context.clear()
    }
}
