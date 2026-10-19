// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

private const val PRIMALITY_HEADROOM_BITS = 65
private const val MONTGOMERY_SLACK_BITS = 3
private const val SIEVE_LIMIT = 509
private const val BPSW_ROUNDS = 24
private const val FIRST_DISCRIMINANT = 5L
private const val MOD4_MASK = 3L
private const val MOD8_MASK = 7L
private const val THREE = 3L
private const val FIVE = 5L
private const val QUARTER = 4L
private val SIEVE_PRIMES: IntArray =
    (3..SIEVE_LIMIT).filter { q -> (2 until q).none { q % it == 0 } }.toIntArray()

/**
 * The reference `ibz_probab_prime`: trial division by the odd primes up to 509, Baillie-PSW (strong
 * base-2 Miller-Rabin and strong Lucas with Selfridge parameters), then `rounds - 24` Miller-Rabin
 * rounds on `rand_interval(2, n - 2)` witnesses.
 */
internal fun Ibz.isProbablePrime(rounds: Int, randomBytes: (Int) -> ByteArray): Boolean {
    require(bitlen <= capacityBits - MONTGOMERY_SLACK_BITS) { "Value exceeds the Montgomery bound" }
    require(bitlen + PRIMALITY_HEADROOM_BITS < capacityBits) { "Value exceeds the primality bound" }
    val n = IbzNatural.trim(magnitude())
    val small =
        if (IbzWords.bitLength(n) < Long.SIZE_BITS - 1) n.firstOrNull() ?: 0L else Long.MAX_VALUE
    return when {
        isNegative || small < 2 -> false
        small <= THREE -> true
        !isOdd || SIEVE_PRIMES.any { modUnsigned(it.toULong()) == 0UL && small != it.toLong() } ->
            false
        small <= SIEVE_LIMIT -> true
        !IbzModRing(n).isStrongProbablePrime(IbzNatural.of(2)) || !isStrongLucasProbablePrime() ->
            false
        else -> passesRandomRounds(rounds - BPSW_ROUNDS, randomBytes)
    }
}

/**
 * Miller-Rabin on [rounds] random witnesses, stopping at the first one that proves compositeness.
 */
internal fun Ibz.passesRandomRounds(rounds: Int, randomBytes: (Int) -> ByteArray): Boolean {
    val n = IbzNatural.trim(magnitude())
    val bits = IbzWords.bitLength(n)
    val argument = if (bits + 1 < bitlen) Ibz.create(capacityBits, bits + 1, false, n) else this
    val two = Ibz.of(capacityBits, 2, 3)
    val upper = argument - two
    val ring = IbzModRing(n)
    return (0 until rounds).all {
        val witness = Ibz.randomInterval(two, upper, randomBytes)
        ring.isStrongProbablePrime(IbzNatural.trim(witness.magnitude()))
    }
}

private fun IbzModRing.isStrongProbablePrime(base: LongArray): Boolean {
    val even = IbzNatural.subtract(modulus, IbzNatural.ONE)
    val twos = IbzWords.trailingZeros(even)
    val minusOne = ofSigned(-1)
    var x = pow(enter(base), IbzNatural.shiftRight(even, twos))
    if (isOne(x)) {
        return true
    }
    // Once a square reaches 1 it never reaches -1, so the loop needs no separate exit for it.
    var index = 1
    while (index < twos && !x.contentEquals(minusOne)) {
        x = multiply(x, x)
        index++
    }
    return x.contentEquals(minusOne)
}

/**
 * The strong Lucas test of an odd value above one with `P = 1`, `Q = (1 - D) / 4` and the first `D`
 * in `5, -7, 9, ...` of symbol `-1`; squares and values sharing a factor with `D` are composite.
 */
internal fun Ibz.isStrongLucasProbablePrime(): Boolean {
    val ring = IbzModRing(IbzNatural.trim(magnitude()))
    val root = IbzNatural.sqrtFloor(ring.modulus)
    val discriminant =
        if (IbzNatural.multiply(root, root).contentEquals(ring.modulus)) null
        else selfridgeDiscriminant()
    return discriminant != null && ring.hasStrongLucasZero(discriminant)
}

private fun Ibz.selfridgeDiscriminant(): Long? {
    var magnitude = FIRST_DISCRIMINANT
    var symbol = smallJacobi(modUnsigned(magnitude.toULong()).toLong(), magnitude)
    while (symbol == 1) {
        magnitude += 2
        symbol = smallJacobi(modUnsigned(magnitude.toULong()).toLong(), magnitude)
    }
    return if (symbol == 0) null else if (magnitude and MOD4_MASK == 1L) magnitude else -magnitude
}

/**
 * Whether `U_d = 0` or some `V_{d 2^r} = 0` for `n + 1 = d 2^s`, `d` odd, by the binary Lucas
 * chain.
 */
private fun IbzModRing.hasStrongLucasZero(discriminant: Long): Boolean {
    val even = IbzNatural.add(modulus, IbzNatural.ONE)
    val twos = IbzWords.trailingZeros(even)
    val odd = IbzNatural.shiftRight(even, twos)
    val q = (1 - discriminant) / QUARTER
    val d = ofSigned(discriminant)
    var u = one
    var v = one
    var qk = ofSigned(q)
    for (position in IbzWords.bitLength(odd) - 2 downTo 0) {
        u = multiply(u, v)
        v = subtract(multiply(v, v), add(qk, qk))
        qk = multiply(qk, qk)
        if (IbzNatural.bit(odd, position)) {
            val next = half(add(u, v))
            v = half(add(multiply(d, u), v))
            u = next
            qk = multiply(qk, ofSigned(q))
        }
    }
    var zero = u.isEmpty() || v.isEmpty()
    var index = 1
    while (!zero && index < twos) {
        v = subtract(multiply(v, v), add(qk, qk))
        qk = multiply(qk, qk)
        zero = v.isEmpty()
        index++
    }
    return zero
}

/** The Jacobi symbol `(a / n)` of word-sized operands, zero when they share a factor. */
private fun smallJacobi(value: Long, modulus: Long): Int {
    var a = value % modulus
    var n = modulus
    var symbol = 1
    while (a != 0L) {
        while (a and 1L == 0L) {
            a = a shr 1
            val n8 = n and MOD8_MASK
            if (n8 == THREE || n8 == FIVE) {
                symbol = -symbol
            }
        }
        val swap = n
        n = a
        a = swap
        if (a and MOD4_MASK == THREE && n and MOD4_MASK == THREE) {
            symbol = -symbol
        }
        a %= n
    }
    return if (n == 1L) symbol else 0
}
