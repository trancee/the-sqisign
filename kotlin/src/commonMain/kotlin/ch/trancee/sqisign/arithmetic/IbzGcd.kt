// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

/**
 * C materialises whole Euclidean quotients as integers, so cofactor-tracking operands stay one limb
 * below capacity.
 */
private const val QUOTIENT_HEADROOM_BITS = 64

/** A greatest common divisor at the wider operand bound with tight signed Bezout cofactors. */
internal class IbzBezout(internal val gcd: Ibz, internal val u: Ibz, internal val v: Ibz)

/** A modular inverse candidate; C callers read [value] even when no inverse [exists]. */
internal class IbzInverse(internal val value: Ibz, internal val exists: Boolean)

/** A Bezout cofactor; Euclidean cofactors alternate in sign, so only magnitudes are replayed. */
private class Cofactor(private val negative: Boolean, private val magnitude: LongArray) {
    fun toIbz(capacityBits: Int, negate: Boolean): Ibz =
        Ibz.create(capacityBits, IbzWords.bitLength(magnitude) + 1, negative != negate, magnitude)
}

/** Classical Euclid on magnitudes `larger >= smaller`; yields the gcd and the quotient sequence. */
private class Euclid(larger: LongArray, smaller: LongArray) {
    private val quotients = mutableListOf<LongArray>()
    val gcd: LongArray

    init {
        var current = IbzNatural.trim(larger)
        var following = IbzNatural.trim(smaller)
        while (following.isNotEmpty()) {
            val (quotient, remainder) = IbzNatural.divide(current, following)
            quotients += quotient
            current = following
            following = remainder
        }
        gcd = current
    }

    /**
     * The cofactor of the operand that starts with coefficients `(1, 0)` when [leading], else `(0,
     * 1)`: `|c_(k+1)| = |c_(k-1)| + q_k |c_k|`, and the sign alternates from the start.
     */
    fun cofactor(leading: Boolean): Cofactor {
        var current = if (leading) IbzNatural.ONE else LongArray(0)
        var following = if (leading) LongArray(0) else IbzNatural.ONE
        for (quotient in quotients) {
            val next = IbzNatural.add(current, IbzNatural.multiply(quotient, following))
            current = following
            following = next
        }
        return Cofactor((quotients.size % 2 == 1) == leading, current)
    }
}

private fun orderedEuclid(left: Ibz, right: Ibz): Pair<Euclid, Boolean> {
    val first = left.magnitude()
    val second = right.magnitude()
    val swapped = IbzWords.compare(first, second) < 0
    return Pair(if (swapped) Euclid(second, first) else Euclid(first, second), swapped)
}

private fun requireQuotientHeadroom(left: Ibz, right: Ibz) =
    require(maxOf(left.bitlen, right.bitlen) <= left.capacityBits - QUOTIENT_HEADROOM_BITS) {
        "Cofactor operands need one limb of capacity headroom"
    }

/** The non-negative gcd at bit length `max(bitlen)`; `gcd(0, 0)` is zero at bit length 1. */
internal fun Ibz.gcd(other: Ibz): Ibz {
    require(capacityBits == other.capacityBits) { "Capacity mismatch" }
    if (isZero && other.isZero) {
        return Ibz.zero(capacityBits)
    }
    val euclid = orderedEuclid(this, other).first
    return Ibz.create(capacityBits, maxOf(bitlen, other.bitlen), false, euclid.gcd)
}

/** Euclid's gcd with cofactors `u * this + v * other = gcd`, as the reference `ibz_xgcd`. */
internal fun Ibz.extendedGcd(other: Ibz): IbzBezout {
    require(capacityBits == other.capacityBits) { "Capacity mismatch" }
    requireQuotientHeadroom(this, other)
    val (euclid, swapped) = orderedEuclid(this, other)
    val u = euclid.cofactor(!swapped)
    val v = euclid.cofactor(swapped)
    return IbzBezout(
        Ibz.create(capacityBits, maxOf(bitlen, other.bitlen), false, euclid.gcd),
        u.toIbz(capacityBits, isNegative),
        v.toIbz(capacityBits, other.isNegative),
    )
}

/** The Bezout coefficient of this value reduced modulo a positive [modulus]. */
internal fun Ibz.inverseMod(modulus: Ibz): IbzInverse {
    require(capacityBits == modulus.capacityBits) { "Capacity mismatch" }
    require(!modulus.isNegative && !modulus.isZero) { "Modulus must be positive" }
    requireQuotientHeadroom(this, modulus)
    val (euclid, swapped) = orderedEuclid(this, modulus)
    val coefficient = euclid.cofactor(!swapped)
    return IbzInverse(
        coefficient.toIbz(capacityBits, isNegative).mod(modulus),
        euclid.gcd.contentEquals(IbzNatural.ONE),
    )
}

/** The solution in `[0, m1 * m2)` of `x = a1 (mod m1)` and `x = a2 (mod m2)` for coprime moduli. */
internal fun Ibz.Companion.crt(a1: Ibz, a2: Ibz, m1: Ibz, m2: Ibz): Ibz {
    val inverse = m1.inverseMod(m2)
    require(inverse.exists) { "Moduli must be coprime" }
    val difference = a2 - a1
    require(difference.bitlen + inverse.value.bitlen <= a1.capacityBits) {
        "CRT product exceeds capacity"
    }
    require(m1.bitlen + m2.bitlen <= a1.capacityBits) { "CRT modulus exceeds capacity" }
    val residue = (difference * inverse.value).mod(m2)
    return (a1 + m1 * residue).mod(m1 * m2)
}
