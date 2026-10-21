// SPDX-License-Identifier: Apache-2.0

package ch.trancee.sqisign.quaternion

import ch.trancee.sqisign.ParameterSet
import ch.trancee.sqisign.arithmetic.Ibz
import ch.trancee.sqisign.arithmetic.bitSize
import ch.trancee.sqisign.arithmetic.extractSigned64
import ch.trancee.sqisign.arithmetic.minusWithBound
import ch.trancee.sqisign.arithmetic.plusIntWithBound
import ch.trancee.sqisign.arithmetic.plusWithBound
import ch.trancee.sqisign.arithmetic.shiftSigned
import ch.trancee.sqisign.arithmetic.timesIntWithBound
import ch.trancee.sqisign.arithmetic.timesWithBound
import ch.trancee.sqisign.arithmetic.unaryMinus

/** Headroom of the int32 complex products above the value bound, as `DIM2_WORK_BITS`. */
private const val WORK_HEADROOM = 32

/** Carry bits of the Hermitian product above its two values and `p`, as `DIM2I_HERM_BITS`. */
private const val HERMITIAN_HEADROOM = 3

/** Outer passes beyond the required drop, as `DIM2I_OUTER_ITS`. */
private const val OUTER_EXTRA = 4

/** Little-endian words of the reference `QUAT_SQRT_P_HEX`, `floor(sqrt(p))`, at each level. */
private val SQRT_P_WORDS =
    mapOf(
        ParameterSet.NIST_I to
            longArrayOf(
                0xe2e0ee249760d331UL.toLong(),
                0x132a9cec95d0b5c1UL.toLong(),
                0x00000006ed9eba16UL.toLong(),
            ),
        ParameterSet.NIST_III to
            longArrayOf(
                0x295587cb652b43d2UL.toLong(),
                0x6dc6227993ee3b5bUL.toLong(),
                0xc5c1722145a8a2caUL.toLong(),
                0x14c8dc2e42397fd6UL.toLong(),
            ),
        ParameterSet.NIST_V to
            longArrayOf(
                0xb7c06fd19d2561ecUL.toLong(),
                0x916aca3991fcaec1UL.toLong(),
                0x2012131573f2aa59UL.toLong(),
                0x4654c2a217583284UL.toLong(),
                0x3d9abfb41bd6b23cUL.toLong(),
                0x00000000000041f8UL.toLong(),
            ),
    )

/** A Gaussian integer `re + i im` of bounded integers. */
internal class Complex(val re: Ibz, val im: Ibz) {
    fun bitSize(): Int = maxOf(re.bitSize(), im.bitSize())

    fun window(offset: Int): ComplexWord =
        ComplexWord(re.extractSigned64(offset), im.extractSigned64(offset))

    fun timesI(): Complex = Complex(-im, re)

    fun negated(): Complex = Complex(-re, -im)

    fun withBound(bound: Int): Complex = Complex(re.withBound(bound), im.withBound(bound))

    /** The reference `ct_complex_mul_si`: every product and sum at [bound]. */
    fun scaled(q: ComplexInt, bound: Int): Complex =
        Complex(
            re.timesIntWithBound(q.re, bound)
                .plusWithBound(im.timesIntWithBound(-q.im, bound), bound),
            re.timesIntWithBound(q.im, bound)
                .plusWithBound(im.timesIntWithBound(q.re, bound), bound),
        )

    fun plus(other: Complex, bound: Int): Complex =
        Complex(re.plusWithBound(other.re, bound), im.plusWithBound(other.im, bound))
}

/** The rank-2 Z[i] basis `v1 = (aa, cc)`, `v2 = (bb, dd)` and the tracked pair `(ee, ff)`. */
private class GaussianBasis(
    val aa: Complex,
    val bb: Complex,
    val cc: Complex,
    val dd: Complex,
    val ee: Complex,
    val ff: Complex,
) {
    fun swapped(): GaussianBasis = GaussianBasis(bb, aa, dd, cc, ff, ee)
}

/** The public widths of `quat_lll_reduce_O0_ideal` for an input bound at one level. */
private class GaussianWidths(bound: Int, sqrtBits: Int, primeBits: Int) {
    private val inBits = maxOf(bound, sqrtBits + 1)

    /** `DIM2_VAL_BITS`: bound of the basis values. */
    val valueBits = inBits + 1

    /** `DIM2_WORK_BITS`: bound of the int32 complex products. */
    val workBits = valueBits + WORK_HEADROOM

    /** `DIM2I_HERM_BITS`: bound of every Hermitian intermediate. */
    val hermitianBits = 2 * valueBits + primeBits + HERMITIAN_HEADROOM

    /** `DIM2I_SHIFT_MAX`: largest pre-scaling shift of the second vector. */
    val maxShift = maxOf(valueBits - GAUSSIAN_THRESHOLD, 0)

    /** `DIM2I_OUTER_ITS`: outer passes for the required drop `DIM2I_REQ_DROP`. */
    val outerIterations =
        ((inBits - sqrtBits + 1) / 2 + GAUSSIAN_THRESHOLD - 1) / (GAUSSIAN_THRESHOLD - 1) +
            OUTER_EXTRA
}

/**
 * The reference `quat_lll_reduce_O0_ideal` on the numerator [hnf] of an `O0`-ideal lattice with
 * denominator 2 in the quaternion algebra ramified at [p]: the Lagrange-Gauss reduction over Z[i]
 * of the generating pair, columns 0 and 2, under the norm `|z1|^2 + p |z2|^2`, approximated by
 * scaling `z2` with `floor(sqrt(p))`. Returns the columns `v1, i v1, v2, i v2` bounded to
 * `max([bound], sqrt(p) bits + 1) + 1` bits, `v1` oriented into the first quadrant and `v2` so that
 * the Hermitian product `H(v1, v2)` is. The 64-bit windows match the second norm only to window
 * precision. Variable-time.
 */
internal fun LllConfig.reduceO0Ideal(hnf: List<List<Ibz>>, p: Ibz, bound: Int): List<List<Ibz>> {
    val capacity = p.capacityBits
    val sqrtWords = SQRT_P_WORDS.getValue(parameters)
    val sqrtP = Ibz.fromWords(capacity, sqrtWords.size * Long.SIZE_BITS, sqrtWords)
    val sqrtBits = sqrtP.bitSize()
    val nextSquare = sqrtP.plusIntWithBound(1, sqrtBits + 2).let { it * it }
    require(sqrtP * sqrtP <= p && p < nextSquare) { "p is not the prime of the parameter set" }
    val widths = GaussianWidths(bound, sqrtBits, primeBits)
    // The Hermitian width dominates the work width, so it alone bounds every intermediate.
    require(widths.hermitianBits <= capacity) {
        "Bound exceeds the integer capacity"
    }
    val v = widths.valueBits
    val s = sqrtP.withBound(sqrtBits + 1)
    fun column(c: Int, first: Int) = Complex(hnf[first][c], hnf[first + 1][c]).withBound(v)
    fun scaled(z: Complex) = Complex(z.re.timesWithBound(s, v), z.im.timesWithBound(s, v))
    val (ee, ff) = column(0, 2) to column(2, 2)
    var basis = GaussianBasis(column(0, 0), column(2, 0), scaled(ee), scaled(ff), ee, ff)
    repeat(widths.outerIterations) { basis = outerPass(basis, widths) }
    basis = basis.swapped()
    val (aa, _, first) = orient(listOf(basis.aa, basis.cc, basis.ee))
    val (bb, _, second) = orient(listOf(basis.bb, basis.dd, basis.ff))
    val key = hermitian(listOf(aa, first, bb, second), p, widths.hermitianBits)
    val (_, v2, w2) = orient(listOf(key, bb, second))
    val columns =
        listOf(
            listOf(aa, first),
            listOf(aa.timesI(), first.timesI()),
            listOf(v2, w2),
            listOf(v2.timesI(), w2.timesI()),
        )
    return List(DIMENSION) { r ->
        List(DIMENSION) { c ->
            val z = columns[c][r / 2]
            (if (r % 2 == 0) z.re else z.im).withBound(v)
        }
    }
}

/** One outer pass of the reference `lehmer_gaussian`: windows, int32 kernel, full update. */
private fun outerPass(current: GaussianBasis, widths: GaussianWidths): GaussianBasis {
    val sizeV1 = maxOf(current.aa.bitSize(), current.cc.bitSize())
    val sizeV2 = maxOf(current.bb.bitSize(), current.dd.bitSize())
    val basis = if (sizeV1 < sizeV2) current.swapped() else current
    val (bitsV1, bitsV2) = maxOf(sizeV1, sizeV2) to minOf(sizeV1, sizeV2)
    val rho = bitsV1 - bitsV2 + 1
    val large = rho >= GAUSSIAN_THRESHOLD
    val fromV1 = maxOf(bitsV1 - GAUSSIAN_WINDOW, 0)
    val fromV2 =
        if (large) maxOf(bitsV2 - (GAUSSIAN_WINDOW - GAUSSIAN_THRESHOLD + 1), 0) else fromV1
    val extra = if (large) rho - GAUSSIAN_THRESHOLD else 0
    val windows =
        GaussianWindows(
            basis.aa.window(fromV1),
            basis.bb.window(fromV2),
            basis.cc.window(fromV1),
            basis.dd.window(fromV2),
        )
    val m = gaussianInner(windows, !large, fromV1, fromV2)
    val (aa, bb) = applyTransform(basis.aa, basis.bb, m, extra, widths)
    val (cc, dd) = applyTransform(basis.cc, basis.dd, m, extra, widths)
    val (ee, ff) = applyTransform(basis.ee, basis.ff, m, extra, widths)
    return GaussianBasis(aa, bb, cc, dd, ee, ff)
}

/** The reference `ct_apply_matrix_gaussian`: `(x, y) <- (u00 x + u01 2^extra y, u10 x + u11 y)`. */
private fun applyTransform(
    x: Complex,
    y: Complex,
    m: GaussianTransform,
    extra: Int,
    widths: GaussianWidths,
): Pair<Complex, Complex> {
    val w = widths.workBits
    val shifted =
        Complex(
            y.re.shiftSigned(extra, widths.maxShift, w),
            y.im.shiftSigned(extra, widths.maxShift, w),
        )
    val first = x.scaled(m.u00, w).plus(shifted.scaled(m.u01, w), widths.valueBits)
    val second = y.scaled(m.u11, w).plus(x.scaled(m.u10, w), widths.valueBits)
    return first to second
}

/**
 * The reference `orient_canonical`: multiply every value by the unit that moves the first, the key,
 * into the closed first quadrant.
 */
internal fun orient(values: List<Complex>): List<Complex> {
    val key = values[0]
    val rotated = if (key.re.isNegative != key.im.isNegative) values.map { it.timesI() } else values
    val first = rotated[0]
    return if (first.re.isNegative || first.im.isNegative) rotated.map { it.negated() } else rotated
}

/**
 * The reference `ct_zi_hermitian` of `v = (v1, v2)` and `w = (w1, w2)` given as `[v1, v2, w1, w2]`:
 * `conj(v1) w1 + p conj(v2) w2` with every intermediate bounded to [bound].
 */
private fun hermitian(values: List<Complex>, p: Ibz, bound: Int): Complex {
    val (v1, v2, w1) = values
    val w2 = values[DIMENSION - 1]
    fun product(x: Ibz, y: Ibz) = x.timesWithBound(y, bound)
    fun real(x: Complex, y: Complex) = product(x.re, y.re).plusWithBound(product(x.im, y.im), bound)
    fun imaginary(x: Complex, y: Complex) =
        product(x.re, y.im).minusWithBound(product(x.im, y.re), bound)
    return Complex(
        real(v1, w1).plusWithBound(product(real(v2, w2), p), bound),
        imaginary(v1, w1).plusWithBound(product(imaginary(v2, w2), p), bound),
    )
}
