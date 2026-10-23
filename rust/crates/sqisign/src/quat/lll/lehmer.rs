//! Lehmer-style constant-time reduction of 2x2 integer lattices (vendor
//! `lll/lehmer_xgcd.c`).
//!
//! The four entries `A, B, C, D` of a basis `[[A, B], [C, D]]` (columns are
//! the basis vectors) are reduced by 2x2 transforms computed on 63-bit
//! windows of the leading bits. Every count, shift and mask follows the C
//! code so the results agree with C in value and bound.

use super::val_bits;
use super::word::{
    abs_sign, clrsb, div_nonzero, isqrt_32, mask, mask_if, max, max0, min, mul_high, neg_mask,
    select32, sqr_high, swap_i32, swap_u64,
};
use crate::mp::{Ibz, MpParams};
use crate::quat::Mat2;

const THRESHOLD: i32 = 30;
const W: i32 = 2 * THRESHOLD + 3;

/// C `DIM2_OUTER_ITS`.
fn outer_its(det_bits: usize) -> usize {
    det_bits / (THRESHOLD as usize - 1) + 1
}

/// C `DIM2_WORK_BITS`.
fn work_bits(det_bits: usize) -> usize {
    val_bits(det_bits) + 32
}

/// C `DIM2_SHIFT_MAX`.
fn shift_max(det_bits: usize) -> u32 {
    val_bits(det_bits).saturating_sub(THRESHOLD as usize) as u32
}

/// `x / 2^off` for either sign of `off`, as a word (C `lg_window_u64`).
fn window_u64<P: MpParams>(x: &Ibz<P>, off: i32) -> u64 {
    x.extract_u64(max0(off)).wrapping_shl(max0(-off) as u32)
}

/// Signed `x / 2^off` (C `lg_window_i64`).
fn window_i64<P: MpParams>(x: &Ibz<P>, off: i32) -> i64 {
    (x.extract_i64(max0(off)) as u64).wrapping_shl(max0(-off) as u32) as i64
}

fn bits<P: MpParams>(x: &Ibz<P>) -> i32 {
    x.bitsize() as i32
}

/// The transform of a Euclidean reduction of the windows `a >= b`, stopped
/// once `b < sqrta` or the windows run short; `swap` allows exchanging them
/// (C `partial_xgcd_msb`).
fn partial_xgcd_msb(mut a: u64, mut b: u64, sqrta: u64, swap: bool) -> [[i32; 2]; 2] {
    let (mut u00, mut u01, mut u10, mut u11) = (1i32, 0i32, 0i32, 1i32);
    let mut valid = u64::MAX;
    let global_swap = mask_if(swap);
    for _ in 0..=THRESHOLD {
        valid &= !mask_if((a == 0) | (b == 0));
        let swap_mask = mask_if(a < b) & valid;
        valid &= !swap_mask | global_swap;
        let actual = swap_mask & global_swap;
        swap_u64(actual, &mut a, &mut b);
        swap_i32(actual as u32, &mut u00, &mut u10);
        swap_i32(actual as u32, &mut u01, &mut u11);
        valid &= !mask_if(b < sqrta);
        let lza = (a | 1).leading_zeros();
        let lzb = (b | 1).leading_zeros();
        valid &= !mask_if((lza > THRESHOLD as u32) | (lzb > THRESHOLD as u32));
        let mut k = lzb.wrapping_sub(lza) & valid as u32;
        let b_shifted = b.wrapping_shl(k);
        k = k.wrapping_sub((1 & mask_if(b_shifted > a)) as u32);
        k &= valid as u32;
        a = a.wrapping_sub(b.wrapping_shl(k) & valid);
        u00 = u00.wrapping_sub(((u10 as u32).wrapping_shl(k) & valid as u32) as i32);
        u01 = u01.wrapping_sub(((u11 as u32).wrapping_shl(k) & valid as u32) as i32);
    }
    [[u00, u01], [u10, u11]]
}

/// `(a, b) <- (m00 a + m01 (b << extra), m10 a + m11 b)` (C
/// `ct_apply_matrix`).
fn apply<P: MpParams>(a: &mut Ibz<P>, b: &mut Ibz<P>, m: &[[i32; 2]; 2], extra: i32, det: usize) {
    let (wb, vb) = (work_bits(det), val_bits(det));
    let t1 = a.mul_i32_bounded(m[1][0], wb);
    let t2 = b
        .ct_shift(extra, shift_max(det), wb)
        .mul_i32_bounded(m[0][1], wb);
    *a = a.mul_i32_bounded(m[0][0], wb).add_bounded(&t2, vb);
    *b = b.mul_i32_bounded(m[1][1], wb).add_bounded(&t1, vb);
}

/// `a += mu (b << extra)` (C `ct_size_reduce`).
fn size_reduce<P: MpParams>(a: &mut Ibz<P>, b: &Ibz<P>, mu: i32, extra: i32, det: usize) {
    let t1 = b
        .ct_shift(extra, shift_max(det), work_bits(det))
        .mul_i32_bounded(mu, work_bits(det));
    *a = a.add_bounded(&t1, val_bits(det));
}

/// Negates the columns so that `A >= 0` and `B >= 0`.
fn normalize<P: MpParams>(q: &mut [Ibz<P>; 4]) {
    for (top, bottom) in [(0, 2), (1, 3)] {
        let m = neg_mask(&q[top]);
        q[top] = q[top].cneg(m);
        q[bottom] = q[bottom].cneg(m);
    }
}

/// C `partial_xgcd_lehmer` on `q = [A, B, C, D]`, `A, B >= 0`, `C = 0`.
fn partial_xgcd_lehmer<P: MpParams>(q: &mut [Ibz<P>; 4], outer: usize, det: usize) {
    let mut d = q[0] * q[3];
    d.set_bound(val_bits(det));
    let shift_2k = (bits(&d) - THRESHOLD) & !1;
    let shift_k = shift_2k / 2;
    let sqrta = isqrt_32(window_u64(&d, shift_2k) as u32);
    for _ in 0..outer {
        let borrow = neg_mask(&(q[0] - q[1]));
        let [a, b, c, dd] = q;
        Ibz::cswap(a, b, borrow);
        Ibz::cswap(c, dd, borrow);
        let (bits_a, bits_b) = (bits(a), bits(b));
        let rho = bits_a - bits_b + 1;
        let from_a = bits_a - W;
        let large = rho >= THRESHOLD;
        let from_b = select32(large, bits_b - (W - THRESHOLD + 1), from_a);
        let extra = select32(large, rho - THRESHOLD, 0);
        let (pa, pb) = (window_u64(a, from_a), window_u64(b, from_b));
        let diff_sh = from_b - shift_k;
        let shift_right = min(16, max0(diff_sh));
        let shift_left = select32(diff_sh <= -48, 0, max0(-diff_sh));
        let psqrta =
            (sqrta >> shift_right).wrapping_shl(shift_left as u32) | mask_if(diff_sh <= -48);
        let m = partial_xgcd_msb(pa, pb, psqrta, !large);
        apply(a, b, &m, extra, det);
        apply(c, dd, &m, extra, det);
        normalize(q);
    }
}

/// The multiple `k` of `v1 = (b, d)` that size-reduces `v2 = (a, c)`,
/// negated (C `sizered_msb`).
fn sizered_msb(a: u64, b: u64, c: i64, d: i64) -> i32 {
    let v1_shift = min((b | 1).leading_zeros() as i32 - 1, clrsb(d | 1)) as u32;
    let bs = b.wrapping_shl(v1_shift) as i64;
    let ds = (d as u64).wrapping_shl(v1_shift) as i64;
    let num = (mul_high(a as i64, bs) >> 1).wrapping_add(mul_high(c, ds) >> 1);
    let den = (mul_high(b as i64, bs) >> 1).wrapping_add(mul_high(d, ds) >> 1) as u64;
    let (num_abs, num_sign) = abs_sign(num);
    let k_abs = div_nonzero(
        num_abs.wrapping_mul(2).wrapping_add(den),
        den.wrapping_mul(2),
    );
    let m = 0u64.wrapping_sub(num_sign & 1);
    ((k_abs ^ m).wrapping_add(num_sign) as i32).wrapping_neg()
}

/// C `sizered_lehmer` on `q = [A, B, C, D]`: size-reduces `(A, C)` by
/// `(B, D)`.
fn sizered_lehmer<P: MpParams>(q: &mut [Ibz<P>; 4], outer: usize, det: usize) {
    for _ in 0..outer {
        let v1 = max(bits(&q[1]), bits(&q[3]));
        let v2 = max(bits(&q[0]), bits(&q[2]));
        let top = max(v1, v2);
        let rho = top - v1 + 1;
        let from_v2 = top - W;
        let large = rho >= THRESHOLD;
        let from_v1 = select32(large, v1 - (W - THRESHOLD + 1), from_v2);
        let extra = select32(large, rho - THRESHOLD, 0);
        let mu = sizered_msb(
            window_u64(&q[0], from_v2),
            window_u64(&q[1], from_v1),
            window_i64(&q[2], from_v2),
            window_i64(&q[3], from_v1),
        );
        let [a, b, c, d] = q;
        size_reduce(a, b, mu, extra, det);
        size_reduce(c, d, mu, extra, det);
        normalize(q);
    }
}

/// A Lagrange-Gauss reduced basis of the lattice with the basis
/// `[[A/g, 0], [B, g]]` (columns), `det = A <= 2^det_bits`, shorter vector
/// first (C `quat_lll_dim2_short_basis`).
pub fn dim2_short_basis<P: MpParams>(basis: &Mat2<P>, det_bits: usize) -> Mat2<P> {
    let outer = outer_its(det_bits);
    let mut q = [basis[0][0], basis[0][1], basis[1][0], basis[1][1]];
    for x in &mut q {
        x.set_bound(val_bits(det_bits));
    }
    partial_xgcd_lehmer(&mut q, outer, det_bits);
    sizered_lehmer(&mut q, outer, det_bits);
    let mut t = [q[1], q[0], q[3], q[2]];
    sizered_lehmer(&mut t, 1, det_bits);
    let [mut a, mut b, mut c, mut d] = [t[1], t[0], t[3], t[2]];
    let v1 = max(bits(&b), bits(&d));
    let top = max(v1, max(bits(&a), bits(&c)));
    let from = select32(top >= W, top - W, 0);
    let up = max0(W - 1 - top) as u32;
    let lead = |x: &Ibz<P>| (x.extract_i64(from) as u64).wrapping_shl(up) as i64;
    let n1 = sqr_high(lead(&a)).wrapping_add(sqr_high(lead(&c)));
    let n2 = sqr_high(lead(&b)).wrapping_add(sqr_high(lead(&d)));
    let swap = mask(u64::from(n1 > n2));
    Ibz::cswap(&mut a, &mut b, swap);
    Ibz::cswap(&mut c, &mut d, swap);
    [[a, b], [c, d]]
}

/// `(x, y)` with `x^2 + y^2 = p` for a prime `p` and `r^2 = -1 mod p`,
/// `0 < r < p` (C `quat_lll_dim2_sumofsquares`).
pub fn dim2_sum_of_squares<P: MpParams>(p: &Ibz<P>, r: &Ibz<P>) -> (Ibz<P>, Ibz<P>) {
    let det = p.bound().max(r.bound());
    let vb = val_bits(det);
    let bounded = |x: Ibz<P>| {
        let mut x = x;
        x.set_bound(vb);
        x
    };
    let mut q = [
        bounded(*p),
        bounded(*r),
        bounded(Ibz::from_i64(0, 2)),
        bounded(Ibz::from_i64(1, 2)),
    ];
    partial_xgcd_lehmer(&mut q, outer_its(det), det);
    (q[1].abs(), q[3].abs())
}

#[cfg(test)]
mod tests {
    use super::super::probe::{same, to_c, Rng, I, RUNS};
    use super::super::val_bits;
    use super::{outer_its, partial_xgcd_lehmer, sizered_lehmer, THRESHOLD};
    use super::{partial_xgcd_msb, sizered_msb};
    use sqisign_reference::p324_3::lll as c;

    #[test]
    fn partial_xgcd_msb_matches_c() {
        let mut rng = Rng::new(3);
        for _ in 0..RUNS {
            let a = rng.word();
            let b = rng.near(a);
            let sqrta = rng.near(b);
            let swap = rng.below(2) == 0;
            assert_eq!(
                partial_xgcd_msb(a, b, sqrta, swap),
                c::partial_xgcd_msb(a, b, sqrta, swap),
                "{a:#x} {b:#x} {sqrta:#x} {swap}"
            );
        }
    }

    #[test]
    fn sizered_msb_matches_c() {
        let mut rng = Rng::new(4);
        for _ in 0..RUNS {
            let b = rng.word() >> 1 | 1 << 62;
            let a = rng.near(b) >> 1;
            let (x, y) = (rng.near(b), rng.near(a));
            let (d, c_) = (rng.signed(x), rng.signed(y));
            assert_eq!(
                sizered_msb(a, b, c_, d),
                c::sizered_msb(a, b, c_, d),
                "{a:#x} {b:#x} {c_:#x} {d:#x}"
            );
        }
    }

    /// `[A, B, 0, D]` with `A * D < 2^det`, `B < A`, the size gap of `A`
    /// and `B` around the Euclid/division threshold or `B` around the
    /// scale where the square-root window stops the reduction.
    fn basis(rng: &mut Rng) -> ([I; 4], usize) {
        let ga = 40 + rng.below(400) as usize;
        let gd = 1 + rng.below(400) as usize;
        let det = ga + gd + rng.below(3) as usize;
        let shift_k = (ga + gd) as i64 / 2 - i64::from(THRESHOLD) / 2;
        let gb = match rng.below(3) {
            0 => rng.around(ga as i64 - i64::from(THRESHOLD) + 1),
            1 => rng.around(shift_k - 14),
            _ => 1 + rng.below(ga as u64) as usize,
        }
        .min(ga - 1);
        let bound = val_bits(det);
        let q = [
            rng.value(ga, bound),
            rng.value(gb, bound),
            I::ZERO,
            rng.value(gd, bound),
        ];
        let mut q = q;
        q[2].set_bound(bound);
        (q, det)
    }

    /// `rho = THRESHOLD` with `A = B 2^29 + B - 2^40`, `B = pb 2^37`: one
    /// word step leaves the window `pb - 8` below `pb` at the same size, so
    /// the next step needs the swap that `rho >= THRESHOLD` forbids. Later
    /// outer iterations finish the reduction either way, so this compares
    /// single iterations as well.
    #[test]
    fn lehmer_at_the_threshold_forbids_the_swap() {
        let det = 101;
        let bound = val_bits(det);
        let b = I::from_i64((1 << 33) | 0x1234, 35).mul_2exp(37);
        let mut q = [
            b.mul_2exp(29) + b - I::from_i64(8, 5).mul_2exp(37),
            b,
            I::ZERO,
            I::ONE,
        ];
        q.iter_mut().for_each(|x| x.set_bound(bound));
        assert_eq!(q[0].bitsize() - q[1].bitsize() + 1, THRESHOLD as usize);
        for outer in [1, outer_its(det)] {
            let mut r = q;
            partial_xgcd_lehmer(&mut r, outer, det);
            let x = c::partial_xgcd_lehmer(&q.each_ref().map(to_c), outer as i32, det as i32);
            r.iter().zip(&x).for_each(|(r, x)| same(r, x));
        }
    }

    #[test]
    fn lehmer_steps_match_c_near_their_thresholds() {
        let mut rng = Rng::new(5);
        for _ in 0..RUNS / 100 {
            let (q, det) = basis(&mut rng);
            let outer = outer_its(det);
            let mut r = q;
            partial_xgcd_lehmer(&mut r, outer, det);
            let x = c::partial_xgcd_lehmer(&q.each_ref().map(to_c), outer as i32, det as i32);
            r.iter().zip(&x).for_each(|(r, x)| same(r, x));
            sizered_lehmer(&mut r, outer, det);
            let x = c::sizered_lehmer(&x, outer as i32, det as i32);
            r.iter().zip(&x).for_each(|(r, x)| same(r, x));
        }
    }
}
