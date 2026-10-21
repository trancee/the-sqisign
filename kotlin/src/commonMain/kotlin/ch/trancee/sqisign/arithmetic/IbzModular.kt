// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

private const val MODULUS_HEADROOM_BITS = 64
private const val MONTGOMERY_RADIX_BITS = 61
private const val MOD4_MASK = 3L
private const val MOD8_MASK = 7L
private const val FIVE = 5L
private const val TWO_BITS = 2
private const val HEADROOM_BITS = 3
private val JACOBI_BASES: IntArray =
    (2 until 140).filter { q -> (2 until q).none { q % it == 0 } }.toIntArray()

/** A modular square root; [root] is absent when the reference leaves its output untouched. */
internal class IbzRoot(internal val found: Boolean, internal val root: Ibz?)

/** `this^exponent mod modulus` at the modulus bit length, as the reference `ibz_pow_mod`. */
internal fun Ibz.powMod(exponent: Ibz, modulus: Ibz): Ibz {
    val ring = modulus.oddRing()
    require(!isNegative && this < modulus) { "Base must be reduced" }
    require(!exponent.isNegative && exponent < modulus) { "Exponent must be reduced" }
    return modulus.residue(
        ring.exit(
            ring.pow(ring.enter(magnitude()), IbzNatural.trim(exponent.magnitude())),
        ),
    )
}

/**
 * A square root of `this` modulo an odd prime: `(p + 1) / 4` powering when `p = 3 mod 4`, else
 * Tonelli-Shanks after drawing `rand_interval(1, p - 1)` candidates until one is a non-residue.
 */
internal fun Ibz.sqrtModPrime(modulus: Ibz, randomBytes: (Int) -> ByteArray): IbzRoot {
    require(!isNegative && this < modulus) { "Value must be reduced" }
    return when {
        !modulus.isOdd -> IbzRoot(false, null)
        modulus.magnitude()[0] and MOD4_MASK == MOD4_MASK -> sqrtModThreeMod4(modulus)
        else -> {
            val ring = modulus.oddRing()
            ring
                .tonelliShanks(ring.enter(magnitude()), ring.enter(modulus.nonResidue(randomBytes)))
                ?.let { IbzRoot(true, modulus.residue(ring.exit(it))) } ?: IbzRoot(false, null)
        }
    }
}

/** The reference always writes `a^((p + 1) / 4)` and reports whether it squares back to `a`. */
private fun Ibz.sqrtModThreeMod4(modulus: Ibz): IbzRoot {
    val ring = modulus.oddRing()
    val value = ring.enter(magnitude())
    val exponent = IbzNatural.shiftRight(IbzNatural.add(ring.modulus, IbzNatural.ONE), TWO_BITS)
    val root = ring.pow(value, exponent)
    return IbzRoot(ring.multiply(root, root).contentEquals(value), modulus.residue(ring.exit(root)))
}

/**
 * A square root of `-1` modulo `p = 1 mod 4` as the reference `ibz_sqrt_m1_mod`, which feeds each
 * `rand_interval(0, p)` draw `x` to Montgomery powering without conversion: the base is `x / R`.
 */
internal fun Ibz.sqrtMinusOneMod(randomBytes: (Int) -> ByteArray): Ibz {
    val ring = oddRing()
    require(magnitude()[0] and MOD4_MASK == 1L) { "Modulus must be 1 mod 4" }
    val words = (bitlen + MONTGOMERY_RADIX_BITS + 1) / MONTGOMERY_RADIX_BITS
    val radix =
        ring.pow(
            ring.enter(IbzNatural.of(2)),
            IbzNatural.of(MONTGOMERY_RADIX_BITS.toLong() * words),
        )
    val radixInverse = ring.enter(residue(ring.exit(radix)).inverseMod(this).value.magnitude())
    val exponent = IbzNatural.shiftRight(ring.modulus, TWO_BITS)
    val minusOne = ring.ofSigned(-1)
    while (true) {
        val draw =
            IbzNatural.trim(
                Ibz.randomInterval(Ibz.zero(capacityBits), this, randomBytes).magnitude(),
            )
        val root = ring.pow(ring.multiply(ring.enter(draw), radixInverse), exponent)
        if (ring.multiply(root, root).contentEquals(minusOne)) {
            return residue(ring.exit(root))
        }
    }
}

/**
 * The reference `ibz_sqrt_m1_mod_verified`: powers the first small prime base `a` with `(a / n) =
 * -1` to `n >> 2` and returns the result only if it squares to `-1`.
 */
internal fun Ibz.sqrtMinusOneModVerified(): Ibz? {
    require(!isNegative && magnitude()[0] and MOD4_MASK == 1L) { "Modulus must be 1 mod 4" }
    require(bitlen <= capacityBits - HEADROOM_BITS) { "Modulus exceeds the Montgomery bound" }
    val ring = IbzModRing(IbzNatural.trim(magnitude()))
    val base = JACOBI_BASES.firstOrNull { ring.isJacobiBase(it) } ?: return null
    val root =
        ring.pow(
            ring.enter(IbzNatural.of(base.toLong())),
            IbzNatural.shiftRight(ring.modulus, TWO_BITS),
        )
    return if (ring.multiply(root, root).contentEquals(ring.ofSigned(-1))) {
        residue(ring.exit(root))
    } else {
        null
    }
}

/**
 * Whether `(a / n) = -1` by quadratic reciprocity for `n = 1 mod 4`, as
 * `qlapoty_find_jacobi_minus_one`.
 */
private fun IbzModRing.isJacobiBase(base: Int): Boolean {
    if (base == 2) {
        return modulus[0] and MOD8_MASK == FIVE
    }
    val residue = IbzNatural.mod(modulus, IbzNatural.of(base.toLong())).firstOrNull() ?: 0L
    val small = IbzModRing(IbzNatural.of(base.toLong()))
    return residue != 0L &&
        small
            .pow(small.enter(IbzNatural.of(residue)), IbzNatural.of((base - 1L) / 2))
            .contentEquals(small.ofSigned(-1))
}

private fun Ibz.oddRing(): IbzModRing {
    require(!isNegative && isOdd) { "Modulus must be odd and positive" }
    require(bitlen + MODULUS_HEADROOM_BITS < capacityBits) {
        "Modulus exceeds the Montgomery bound"
    }
    return IbzModRing(IbzNatural.trim(magnitude()))
}

private fun Ibz.residue(words: LongArray): Ibz = Ibz.create(capacityBits, bitlen, false, words)

private fun Ibz.nonResidue(randomBytes: (Int) -> ByteArray): LongArray {
    val upper = this - Ibz.one(capacityBits)
    while (true) {
        val draw = Ibz.randomInterval(Ibz.one(capacityBits), upper, randomBytes)
        if (draw.jacobi(this) == -1) {
            return IbzNatural.trim(draw.magnitude())
        }
    }
}

/** Tonelli-Shanks with a known non-residue; `null` when [value] is not a square. */
private fun IbzModRing.tonelliShanks(value: LongArray, generator: LongArray): LongArray? {
    val even = IbzNatural.subtract(modulus, IbzNatural.ONE)
    var order = IbzWords.trailingZeros(even)
    val odd = IbzNatural.shiftRight(even, order)
    var c = pow(generator, odd)
    var t = pow(value, odd)
    var root = pow(value, IbzNatural.shiftRight(IbzNatural.add(odd, IbzNatural.ONE), 1))
    while (!isOne(t)) {
        var probe = t
        var index = 0
        while (!isOne(probe) && index < order) {
            probe = multiply(probe, probe)
            index++
        }
        if (index >= order) {
            return null
        }
        var b = c
        repeat(order - index - 1) { b = multiply(b, b) }
        root = multiply(root, b)
        c = multiply(b, b)
        t = multiply(t, c)
        order = index
    }
    return root
}
