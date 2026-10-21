// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.arithmetic

private const val MOD4_MASK = 3L
private const val MOD8_MASK = 7L
private const val THREE = 3L
private const val FIVE = 5L

/** The inverse of `[[r1, r2], [s1, s2]]` modulo `2^e`; it [exists] iff the determinant is odd. */
internal class IbzInverseMatrix(internal val exists: Boolean, internal val entries: List<Ibz>)

/**
 * The Jacobi symbol `(this / modulus)` for a non-negative value and an odd positive modulus.
 * Reference `ibz_legendre` aborts for non-coprime non-zero values, so they are rejected here.
 */
internal fun Ibz.jacobi(modulus: Ibz): Int {
    require(capacityBits == modulus.capacityBits) { "Capacity mismatch" }
    require(!isNegative) { "Value must be non-negative" }
    require(!modulus.isNegative && modulus.isOdd) { "Modulus must be odd and positive" }
    if (isZero) {
        return 0
    }
    var n = IbzNatural.trim(modulus.magnitude())
    var a = IbzNatural.mod(IbzNatural.trim(magnitude()), n)
    var symbol = 1
    while (a.isNotEmpty()) {
        val twos = IbzWords.trailingZeros(a)
        a = IbzNatural.shiftRight(a, twos)
        val n8 = n[0] and MOD8_MASK
        if (twos % 2 == 1 && (n8 == THREE || n8 == FIVE)) {
            symbol = -symbol
        }
        if (a[0] and MOD4_MASK == THREE && n[0] and MOD4_MASK == THREE) {
            symbol = -symbol
        }
        val reduced = IbzNatural.mod(n, a)
        n = a
        a = reduced
    }
    require(n.contentEquals(IbzNatural.ONE)) { "Value and modulus must be coprime" }
    return symbol
}

/** The floor of the square root of a non-negative value at its tight bit length. */
internal fun Ibz.sqrtFloor(): Ibz {
    require(!isNegative) { "Value must be non-negative" }
    val root = IbzNatural.sqrtFloor(IbzNatural.trim(magnitude()))
    return Ibz.create(capacityBits, IbzWords.bitLength(root) + 1, false, root)
}

/**
 * The reference `ibz_invmat`: entries wider than `e + 1` bits are reinterpreted at that bound, then
 * the adjugate is scaled by the determinant's Hensel inverse modulo `2^e`.
 */
internal fun Ibz.Companion.inverseMatrixMod2exp(
    r1: Ibz,
    r2: Ibz,
    s1: Ibz,
    s2: Ibz,
    exponent: Int,
): IbzInverseMatrix {
    require(exponent >= 0) { "Exponent must be non-negative" }
    val a = r1.reinterpretAt(exponent + 1)
    val b = r2.reinterpretAt(exponent + 1)
    val c = s1.reinterpretAt(exponent + 1)
    val d = s2.reinterpretAt(exponent + 1)
    // Reinterpreted entries keep whole words, so products and sums wrap like the reference.
    val determinant = (a * d).sum(b * c, subtract = true, checked = false)
    val inverse = determinant.inverseMod2exp(exponent)
    val entries = listOf(d, -b, -c, a).map { (it * inverse).mod2exp(exponent) }
    return IbzInverseMatrix(determinant.isOdd, entries)
}

private fun Ibz.reinterpretAt(bound: Int): Ibz =
    if (bitlen > bound) Ibz.fromWords(capacityBits, bound, toWords().copyOf(IbzWords.limbs(bound)))
    else this

/** Newton-Hensel lifting `x <- x * (2 - a * x)` at doubling precision, as `ibz_invmod_2e`. */
private fun Ibz.inverseMod2exp(exponent: Int): Ibz {
    if (exponent == 0) {
        return Ibz.zero(capacityBits)
    }
    val two = Ibz.of(capacityBits, 2, 3)
    var inverse = Ibz.one(capacityBits)
    var precision = 1
    while (precision < exponent) {
        val next = minOf(2 * precision, exponent)
        inverse =
            (inverse * two.sum(this * inverse, subtract = true, checked = false)).mod2exp(next)
        precision = next
    }
    return inverse.mod2exp(exponent)
}
