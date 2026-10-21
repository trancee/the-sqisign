// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

/** The reference `DIM2I_THRESHOLD`: bits of progress per machine-word transform. */
internal const val GAUSSIAN_THRESHOLD = 27

/** The reference `DIM2I_W`: width of the leading machine-word windows. */
internal const val GAUSSIAN_WINDOW = 2 * GAUSSIAN_THRESHOLD + 5

/** The reference `DIM2I_UP_SHIFT`: headroom kept above the normalised windows. */
private const val UP_SHIFT = 3

/** The reference `DIM2I_INNER_ITS`: machine-word steps per outer pass. */
private const val INNER_STEPS = GAUSSIAN_THRESHOLD + 1

/** Normalisation shifts above this mean a window has lost too much precision. */
private const val STOP_SHIFT = GAUSSIAN_THRESHOLD + (Long.SIZE_BITS - UP_SHIFT - GAUSSIAN_WINDOW)

private const val SIGN_SHIFT = Long.SIZE_BITS - 1
private const val HALF_SHIFT = Int.SIZE_BITS
private const val LOW_HALF = 0xFFFF_FFFFL

/** A signed 64-bit Gaussian integer `re + i im` with wrapping arithmetic. */
internal class ComplexWord(val re: Long, val im: Long) {
    fun shl(shift: Int): ComplexWord = ComplexWord(re shl shift, im shl shift)

    /** `this - q * w`, wrapping as the reference int64 arithmetic. */
    fun minusProduct(q: ComplexInt, w: ComplexWord): ComplexWord =
        ComplexWord(re - (q.re * w.re - q.im * w.im), im - (q.re * w.im + q.im * w.re))
}

/** A signed 32-bit Gaussian integer `re + i im` with wrapping arithmetic. */
internal class ComplexInt(val re: Int, val im: Int) {
    fun shl(shift: Int): ComplexInt = ComplexInt(re shl shift, im shl shift)

    operator fun times(other: ComplexInt): ComplexInt =
        ComplexInt(re * other.re - im * other.im, re * other.im + im * other.re)

    operator fun minus(other: ComplexInt): ComplexInt = ComplexInt(re - other.re, im - other.im)
}

/**
 * The int32 complex transform `v1' = u00 v1 + u01 v2`, `v2' = u10 v1 + u11 v2` of [gaussianInner].
 */
internal class GaussianTransform(
    val u00: ComplexInt,
    val u01: ComplexInt,
    val u10: ComplexInt,
    val u11: ComplexInt,
) {
    /** The `(re, im)` pairs in the reference row-major order. */
    fun flatten(): List<Int> = listOf(u00, u01, u10, u11).flatMap { listOf(it.re, it.im) }
}

/** Leading windows of the basis pairs `((a, c), (b, d))` of a rank-2 Z[i]-lattice. */
internal class GaussianWindows(
    val a: ComplexWord,
    val b: ComplexWord,
    val c: ComplexWord,
    val d: ComplexWord,
)

/**
 * The reference `LG_gaussian_inner`: up to `DIM2I_THRESHOLD + 1` Lagrange-Gauss steps over Z[i] on
 * the signed windows of `v1 = (a, c)` and `v2 = (b, d)` taken at bit offsets [fromV1] and [fromV2],
 * tracked as an int32 complex transform. The first step that loses window precision, would swap
 * when [swapAllowed] is false, or rounds the quotient to zero stops the transform. Variable-time.
 */
internal fun gaussianInner(
    windows: GaussianWindows,
    swapAllowed: Boolean,
    fromV1: Int,
    fromV2: Int,
): GaussianTransform {
    val state = GaussianInnerState(windows, fromV1, fromV2)
    repeat(INNER_STEPS) {
        if (state.valid) {
            state.step(swapAllowed)
        }
    }
    return state.transform()
}

private class GaussianInnerState(
    windows: GaussianWindows,
    private var fromV1: Int,
    private var fromV2: Int,
) {
    private var a = windows.a
    private var b = windows.b
    private var c = windows.c
    private var d = windows.d
    private var u00 = ComplexInt(1, 0)
    private var u01 = ComplexInt(0, 0)
    private var u10 = ComplexInt(0, 0)
    private var u11 = ComplexInt(1, 0)
    var valid = true
        private set

    fun step(swapAllowed: Boolean) {
        var shiftV1 = baseShift(a, c)
        var shiftV2 = baseShift(b, d)
        valid = !stops(shiftV1, fromV1) && !stops(shiftV2, fromV2)
        val swap = valid && firstIsShorter(shiftV1, shiftV2)
        valid = valid && (swapAllowed || !swap)
        if (valid && swap) {
            swapBases()
            shiftV1 = shiftV2.also { shiftV2 = shiftV1 }
        }
        if (valid) {
            reduce(shiftV1, shiftV2)
        }
    }

    /** A vector whose leading window is too short ends the pass once it carries extra bits. */
    private fun stops(shift: Int, extraBits: Int): Boolean = shift > STOP_SHIFT && extraBits > 0

    private fun swapBases() {
        a = b.also { b = a }
        c = d.also { d = c }
        u00 = u10.also { u10 = u00 }
        u01 = u11.also { u11 = u01 }
        fromV1 = fromV2.also { fromV2 = fromV1 }
    }

    /** The reference `na < nb` on norms brought to a common scale by saturating shifts. */
    private fun firstIsShorter(shiftV1: Int, shiftV2: Int): Boolean {
        val relative = shiftV2 - shiftV1
        val first = shiftRightSaturated(norm(a, c, shiftV1), maxOf(-2 * relative, 0))
        val second = shiftRightSaturated(norm(b, d, shiftV2), maxOf(2 * relative, 0))
        return first - second < 0
    }

    private fun reduce(shiftV1: Int, shiftV2: Int) {
        val (aBase, cBase) = a.shl(shiftV1) to c.shl(shiftV1)
        val (bBase, dBase) = b.shl(shiftV2) to d.shl(shiftV2)
        val normB = norm(b, d, shiftV2)
        val x =
            mulHigh(aBase.re, bBase.re) +
                mulHigh(aBase.im, bBase.im) +
                mulHigh(cBase.re, dBase.re) +
                mulHigh(cBase.im, dBase.im)
        val y =
            mulHigh(aBase.im, bBase.re) - mulHigh(aBase.re, bBase.im) +
                mulHigh(cBase.im, dBase.re) - mulHigh(cBase.re, dBase.im)
        val relative = shiftV2 - shiftV1
        val k = maxOf(relative + ceilLog2Diff(maxOf(magnitude(x), magnitude(y)), normB) - 1, 0)
        val difference = relative - k
        val scaledNorm = normB shl maxOf(-difference, 0)
        val q =
            ComplexInt(
                roundRatio(x shl maxOf(difference, 0), scaledNorm),
                roundRatio(y shl maxOf(difference, 0), scaledNorm),
            )
        valid = q.re != 0 || q.im != 0
        if (valid) {
            a = a.minusProduct(q, b.shl(k))
            c = c.minusProduct(q, d.shl(k))
            u00 -= q * u10.shl(k)
            u01 -= q * u11.shl(k)
        }
    }

    fun transform(): GaussianTransform = GaussianTransform(u00, u01, u10, u11)
}

/** The normalising left shift of the pair `(x, y)`, keeping `DIM2I_UP_SHIFT` bits of headroom. */
private fun baseShift(x: ComplexWord, y: ComplexWord): Int {
    val zerosX = (magnitudeBits(x.re) or magnitudeBits(x.im) or 1L).countLeadingZeroBits()
    val zerosY = (magnitudeBits(y.re) or magnitudeBits(y.im) or 1L).countLeadingZeroBits()
    return maxOf(minOf(zerosX, zerosY) - UP_SHIFT, 0)
}

/** The reference `ct_norm64_approx` of both coordinates after a left [shift], as a uint64. */
private fun norm(x: ComplexWord, y: ComplexWord, shift: Int): Long {
    val (xs, ys) = x.shl(shift) to y.shl(shift)
    return mulHigh(xs.re, xs.re) +
        mulHigh(xs.im, xs.im) +
        mulHigh(ys.re, ys.re) +
        mulHigh(ys.im, ys.im)
}

/** The reference `ct_abs64` as the bits of a uint64. */
private fun magnitudeBits(value: Long): Long = if (value < 0) -value else value

private fun magnitude(value: Long): ULong = magnitudeBits(value).toULong()

/** The reference `ct_shr_sat64` on a uint64: shifts of 64 or more give zero. */
private fun shiftRightSaturated(value: Long, shift: Int): Long =
    if (shift >= Long.SIZE_BITS) 0L else value ushr shift

/** The reference `ct_mul_high`: the high word of the signed 128-bit product. */
internal fun mulHigh(a: Long, b: Long): Long {
    val aLow = a and LOW_HALF
    val aHigh = a ushr HALF_SHIFT
    val bLow = b and LOW_HALF
    val bHigh = b ushr HALF_SHIFT
    val first = aHigh * bLow + ((aLow * bLow) ushr HALF_SHIFT)
    val second = aLow * bHigh + (first and LOW_HALF)
    val unsigned = aHigh * bHigh + (first ushr HALF_SHIFT) + (second ushr HALF_SHIFT)
    return unsigned - (if (a < 0) b else 0L) - (if (b < 0) a else 0L)
}

/** The reference `ceil_log2_diff_u64`: `ceil(log2 a - log2 b)` with zero read as one. */
private fun ceilLog2Diff(a: ULong, normB: Long): Int {
    val b = normB.toULong()
    val difference = (b or 1uL).countLeadingZeroBits() - (a or 1uL).countLeadingZeroBits()
    val shiftedB = b shl maxOf(difference, 0)
    val shiftedA = a shl maxOf(-difference, 0)
    return difference + ((shiftedB - shiftedA) shr SIGN_SHIFT).toInt()
}

/** The reference `round_ratio_62bit_ct`: `round(a / b)` for the uint64 [denominator]. */
private fun roundRatio(a: Long, denominator: Long): Int {
    val b = denominator.toULong()
    val half = b shr 1
    val absolute = magnitude(a)
    val rounded = (if (absolute > half) 1 else 0) + (if (absolute > b + half) 1 else 0)
    return if (a < 0) -rounded else rounded
}
