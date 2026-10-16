// SPDX-License-Identifier: Apache-2.0

package org.sqisign

/** Stable ASCII domain separators from specification table 6. */
public enum class PrngDomain(internal val separator: String) {
    /** Key-generation expansion, separated by `key`. */
    KEY_GENERATION("key"),
    /** Signing commitment expansion, separated by `com`. */
    COMMITMENT("com"),
    /** Signing response expansion, separated by `res`. */
    RESPONSE("res"),
    /** General expansion, separated by `def`; not an entropy-source fallback. */
    DEFAULT("def"),
}

/**
 * Specification algorithms 3.1-3.2: SHAKE256(seed || domain), with a 48-byte seed. This is
 * deterministic expansion, NOT an entropy source. Callers must supply secure seeds. Instances are
 * mutable and not thread-safe; no seed is retained separately.
 */
public class SqisignPrng(seed: ByteArray, domain: PrngDomain) {
    private val context: Shake256

    init {
        require(seed.size == SEED_BYTES) { "SQIsign PRNG seeds must contain exactly 48 bytes" }
        context = Shake256().absorb(seed).absorb(domain.separator.encodeToByteArray())
    }

    /** Consumes the next [length] bytes; a negative length is rejected. */
    public fun bytes(length: Int): ByteArray = context.squeeze(length)

    /** Consumes into the requested slice, rejecting invalid ranges before writing. */
    public fun bytesInto(output: ByteArray, offset: Int = 0, length: Int = output.size - offset) {
        context.squeezeInto(output, offset, length)
    }

    /** Best-effort overwrite; subsequent output requests fail even for zero bytes. */
    public fun clear() {
        context.clear()
    }
}

private const val SEED_BYTES = 48
