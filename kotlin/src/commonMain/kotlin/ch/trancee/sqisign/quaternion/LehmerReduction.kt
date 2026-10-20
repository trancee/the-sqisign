// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.abs
import ch.trancee.sqisign.arithmetic.bitSize
import ch.trancee.sqisign.arithmetic.extractUnsigned64
import ch.trancee.sqisign.arithmetic.plusWithBound
import ch.trancee.sqisign.arithmetic.shiftSigned
import ch.trancee.sqisign.arithmetic.timesIntWithBound
import ch.trancee.sqisign.arithmetic.unaryMinus

/** The reference `DIM2_THRESHOLD`: bits of progress per machine-word transform. */
private const val THRESHOLD = 30

/** The reference `DIM2_W`: width of the leading machine-word windows. */
private const val WINDOW = 2 * THRESHOLD + 3

/** Headroom of the transform products above the value width. */
private const val WORK_HEADROOM = 32

/** Window offsets at or below this disable the square-root stopping bound. */
private const val WINDOW_FLOOR = -48

/** Largest right shift applied to the 16-bit square root of the determinant window. */
private const val SQRT_SHIFT_CAP = 16

/** The square root of a 32-bit window has 16 bits. */
private const val SQRT_BITS = 16

private const val MASK32 = 0xFFFF_FFFFL

/** Row-major entries of the int32 transform `[[m00, m01], [m10, m11]]`. */
private const val M00 = 0
private const val M01 = 1
private const val M10 = 2
private const val M11 = 3

/** Coordinates of a vector `(x, y)` with `x^2 + y^2` the reduced lattice determinant. */
internal class TwoSquares(internal val x: Ibz, internal val y: Ibz)

/** The column basis `(a, c), (b, d)` of a dimension-2 lattice. */
private class Basis(val a: Ibz, val b: Ibz, val c: Ibz, val d: Ibz)

/**
 * The reference `quat_lll_dim2_sumofsquares`: Lehmer-style partial extended GCD on the lattice
 * `{(x, y) : x = root * y mod p}`, returning the absolute coordinates of its second basis vector.
 * The determinant `p` needs `bitlen + 34` bits of capacity. Variable-time.
 */
internal fun sumOfSquares(p: Ibz, root: Ibz): TwoSquares {
    val reduction = LehmerReduction(maxOf(p.bitlen, root.bitlen))
    val bits = reduction.valueBits
    require(reduction.workBits < p.capacityBits) { "Determinant exceeds the reduction headroom" }
    val zero = Ibz.zero(p.capacityBits).withBound(bits)
    val one = Ibz.one(p.capacityBits).withBound(bits)
    val reduced = reduction.reduce(Basis(p.withBound(bits), root.withBound(bits), zero, one))
    return TwoSquares(reduced.b.abs(), reduced.d.abs())
}

/** The reference `partial_xgcd_lehmer` for a determinant of at most [detBits] bits. */
private class LehmerReduction(detBits: Int) {
    val valueBits = detBits + 1
    val workBits = valueBits + WORK_HEADROOM
    private val maxShift = maxOf(valueBits - THRESHOLD, 0)
    private val iterations = detBits / (THRESHOLD - 1) + 1

    fun reduce(basis: Basis): Basis {
        val determinant = (basis.a * basis.d).withBound(valueBits)
        val shift2k = (determinant.bitSize() - THRESHOLD) and -2
        val sqrtA = isqrt32(window(determinant, shift2k) and MASK32)
        var current = basis
        repeat(iterations) { current = step(current, shift2k / 2, sqrtA) }
        return current
    }

    private fun step(basis: Basis, shiftK: Int, sqrtA: Long): Basis {
        val ordered = if (basis.a < basis.b) Basis(basis.b, basis.a, basis.d, basis.c) else basis
        val bitsA = ordered.a.bitSize()
        val bitsB = ordered.b.bitSize()
        val rho = bitsA - bitsB + 1
        val large = rho >= THRESHOLD
        val fromA = bitsA - WINDOW
        val fromB = if (large) bitsB - (WINDOW - THRESHOLD + 1) else fromA
        val extra = if (large) rho - THRESHOLD else 0
        val offset = fromB - shiftK
        val bound =
            if (offset <= WINDOW_FLOOR) -1L
            else (sqrtA ushr minOf(SQRT_SHIFT_CAP, maxOf(offset, 0))) shl maxOf(-offset, 0)
        val transform =
            msbTransform(window(ordered.a, fromA), window(ordered.b, fromB), bound, !large)
        val (a, b) = apply(ordered.a, ordered.b, transform, extra)
        val (c, d) = apply(ordered.c, ordered.d, transform, extra)
        val (positiveA, signedC) = if (a.isNegative) -a to -c else a to c
        val (positiveB, signedD) = if (b.isNegative) -b to -d else b to d
        return Basis(positiveA, positiveB, signedC, signedD)
    }

    /** The reference `ct_apply_matrix`: `(x, y) -> (m00 x + m01 2^extra y, m10 x + m11 y)`. */
    private fun apply(x: Ibz, y: Ibz, transform: IntArray, extra: Int): Pair<Ibz, Ibz> {
        val crossX = x.timesIntWithBound(transform[M10], workBits)
        val crossY =
            y.shiftSigned(extra, maxShift, workBits).timesIntWithBound(transform[M01], workBits)
        val nextX = x.timesIntWithBound(transform[M00], workBits).plusWithBound(crossY, valueBits)
        val nextY = y.timesIntWithBound(transform[M11], workBits).plusWithBound(crossX, valueBits)
        return nextX to nextY
    }
}

/** The 64 magnitude bits from [offset], or the low bits shifted up for a negative [offset]. */
private fun window(value: Ibz, offset: Int): Long =
    value.extractUnsigned64(maxOf(offset, 0)) shl maxOf(-offset, 0)

/**
 * The reference `partial_xgcd_msb`: up to `THRESHOLD + 1` subtractive steps on the unsigned windows
 * [start] and [next], tracked as the int32 matrix `[m00, m01, m10, m11]`. The first step that would
 * reach a zero window, fall below [bound], lose precision, or swap when [swapAllowed] is false
 * stops the transform.
 */
internal fun msbTransform(start: Long, next: Long, bound: Long, swapAllowed: Boolean): IntArray {
    var a = start
    var b = next
    val matrix = intArrayOf(1, 0, 0, 1)
    var valid = true
    repeat(THRESHOLD + 1) {
        valid = valid and (a != 0L) and (b != 0L)
        val swap = valid and (a.toULong() < b.toULong())
        if (swap and swapAllowed) {
            a = b.also { b = a }
            matrix[M00] = matrix[M10].also { matrix[M10] = matrix[M00] }
            matrix[M01] = matrix[M11].also { matrix[M11] = matrix[M01] }
        }
        val leadingA = (a or 1L).countLeadingZeroBits()
        val leadingB = (b or 1L).countLeadingZeroBits()
        valid =
            valid and
                (!swap or swapAllowed) and
                (b.toULong() >= bound.toULong()) and
                (leadingA <= THRESHOLD) and
                (leadingB <= THRESHOLD)
        if (valid) {
            var shift = leadingB - leadingA
            if ((b shl shift).toULong() > a.toULong()) {
                shift--
            }
            a -= b shl shift
            matrix[M00] -= matrix[M10] shl shift
            matrix[M01] -= matrix[M11] shl shift
        }
    }
    return matrix
}

/** The floor square root of a 32-bit unsigned [value], one result bit at a time. */
private fun isqrt32(value: Long): Long {
    var root = 0L
    for (bit in SQRT_BITS - 1 downTo 0) {
        val candidate = root or (1L shl bit)
        if (candidate * candidate <= value) {
            root = candidate
        }
    }
    return root
}
