//! Constant-time reduction of an `O0` ideal as a rank-2 `Z[i]` lattice
//! (vendor `lll/gaussian_xgcd.c`).
//!
//! An ideal of `O0 = Z[i] + Z[i] j` is the `Z[i]`-span of two vectors
//! `(z1, z2)`; the quaternion norm is `|z1|^2 + p |z2|^2`. The second
//! coordinate is scaled by `isqrt(p)` for the reduction, the unscaled one
//! tracked alongside.

use super::word::{mask_if, max, max0, neg_mask, select32, swap_i32};
use super::zi_inner::{inner, Zw, THRESHOLD, W, Z32, Z64};
use super::{bounded, val_bits};
use crate::mp::{Ibz, MpParams};
use crate::quat::{Alg, Lattice, QuatParams};

/// A Gaussian integer (C `ct_complex_t`).
type Zi<P> = Zw<Ibz<P>>;

/// C `DIM2I_OUTER_ITS`.
fn outer_its<P: QuatParams>(in_bits: usize) -> usize {
    let drop = in_bits.saturating_sub(P::SQRT_P_BITS).div_ceil(2) + 1;
    drop.div_ceil(THRESHOLD as usize - 1) + 4
}

/// `a * s` (C `ct_complex_mul_si`).
fn mul_si<P: MpParams>(a: &Zi<P>, s: Z32, wbits: usize) -> Zi<P> {
    let re = a.re.mul_i32_bounded(s.re, wbits);
    let re = re.add_bounded(&a.im.mul_i32_bounded(s.im.wrapping_neg(), wbits), wbits);
    let im = a.re.mul_i32_bounded(s.im, wbits);
    let im = im.add_bounded(&a.im.mul_i32_bounded(s.re, wbits), wbits);
    Zw { re, im }
}

/// C `ct_complex_add`.
fn add<P: MpParams>(a: &Zi<P>, b: &Zi<P>, vbits: usize) -> Zi<P> {
    Zw {
        re: a.re.add_bounded(&b.re, vbits),
        im: a.im.add_bounded(&b.im, vbits),
    }
}

/// C `ct_complex_cond_swap`.
fn cswap<P: MpParams>(m: u64, a: &mut Zi<P>, b: &mut Zi<P>) {
    Ibz::cswap(&mut a.re, &mut b.re, m);
    Ibz::cswap(&mut a.im, &mut b.im, m);
}

/// C `ct_complex_sizeinbase_2`.
fn size<P: MpParams>(a: &Zi<P>) -> i32 {
    max(a.re.bitsize() as i32, a.im.bitsize() as i32)
}

/// The window `a / 2^off` (C `ibz_extract_i64` on both parts).
fn window<P: MpParams>(a: &Zi<P>, off: i32) -> Z64 {
    Zw {
        re: a.re.extract_i64(off),
        im: a.im.extract_i64(off),
    }
}

/// `(a, b) <- (m00 a + m01 (b << extra), m10 a + m11 b)` (C
/// `ct_apply_matrix_gaussian`).
fn apply<P: MpParams>(a: &mut Zi<P>, b: &mut Zi<P>, m: &[[Z32; 2]; 2], extra: i32, in_bits: usize) {
    let (wbits, vbits) = (val_bits(in_bits) + 32, val_bits(in_bits));
    let max_shift = val_bits(in_bits).saturating_sub(THRESHOLD as usize) as u32;
    let t1 = mul_si(a, m[1][0], wbits);
    let shifted = Zw {
        re: b.re.ct_shift(extra, max_shift, wbits),
        im: b.im.ct_shift(extra, max_shift, wbits),
    };
    let t2 = mul_si(&shifted, m[0][1], wbits);
    *a = add(&mul_si(a, m[0][0], wbits), &t2, vbits);
    *b = add(&mul_si(b, m[1][1], wbits), &t1, vbits);
}

/// Multiplies `key` and `rest` by the unit of `Z[i]` making both parts of
/// `key` non-negative (C `orient_canonical`).
fn orient<P: MpParams>(key: &mut Zi<P>, rest: &mut [&mut Zi<P>]) {
    let swap = neg_mask(&key.re) ^ neg_mask(&key.im);
    let rotate = |t: &mut Zi<P>| {
        Ibz::cswap(&mut t.re, &mut t.im, swap);
        t.re = t.re.cneg(swap);
    };
    rotate(key);
    rest.iter_mut().for_each(|t| rotate(t));
    let neg = neg_mask(&key.re) | neg_mask(&key.im);
    let negate = |t: &mut Zi<P>| {
        t.re = t.re.cneg(neg);
        t.im = t.im.cneg(neg);
    };
    negate(key);
    rest.iter_mut().for_each(|t| negate(t));
}

/// Lagrange-Gauss reduces the basis `((A, C), (B, D))` over `Z[i]`, with
/// `(E, F)` tracked; the shorter vector comes first, oriented (C
/// `lehmer_gaussian`).
fn lehmer_gaussian<P: MpParams>(z: &mut [Zi<P>; 6], outer: usize, in_bits: usize) {
    let [a, b, c, d, e, f] = z;
    for _ in 0..outer {
        let (mut v1, mut v2) = (max(size(a), size(c)), max(size(b), size(d)));
        let swap = mask_if(v1 < v2);
        cswap(swap, a, b);
        cswap(swap, c, d);
        cswap(swap, e, f);
        swap_i32(swap as u32, &mut v1, &mut v2);
        let rho = v1 - v2 + 1;
        let from_v1 = select32(v1 >= W, v1 - W, 0);
        let large = rho >= THRESHOLD;
        let from_v2 = select32(large, max0(v2 - (W - THRESHOLD + 1)), from_v1);
        let extra = select32(large, rho - THRESHOLD, 0);
        let windows = [
            window(a, from_v1),
            window(b, from_v2),
            window(c, from_v1),
            window(d, from_v2),
        ];
        let m = inner(windows, !large, from_v1, from_v2);
        apply(a, b, &m, extra, in_bits);
        apply(c, d, &m, extra, in_bits);
        apply(e, f, &m, extra, in_bits);
    }
    core::mem::swap(a, b);
    core::mem::swap(c, d);
    core::mem::swap(e, f);
    orient(a, &mut [c, e]);
    orient(b, &mut [d, f]);
}

/// The real and imaginary parts of the Hermitian inner product
/// `conj(v1) w1 + p conj(v2) w2` (C `ct_zi_hermitian`).
fn hermitian<P: MpParams>(v: [&Zi<P>; 4], p: &Ibz<P>, hbits: usize) -> Zi<P> {
    let [v1, v2, w1, w2] = v;
    let mul = |x: &Ibz<P>, y: &Ibz<P>| bounded(*x * *y, hbits);
    let re2 = mul(&v2.re, &w2.re).add_bounded(&mul(&v2.im, &w2.im), hbits);
    let re1 = mul(&v1.re, &w1.re).add_bounded(&mul(&v1.im, &w1.im), hbits);
    let im2 = bounded(mul(&v2.re, &w2.im) - mul(&v2.im, &w2.re), hbits);
    let im1 = bounded(mul(&v1.re, &w1.im) - mul(&v1.im, &w1.re), hbits);
    Zw {
        re: re1.add_bounded(&mul(&re2, p), hbits),
        im: im1.add_bounded(&mul(&im2, p), hbits),
    }
}

impl<P: QuatParams> Lattice<P> {
    /// A reduced basis `v1, i v1, v2, i v2` of the `O0` ideal with the
    /// inert-form HNF basis `self`, entries bounded by `2^bound` (C
    /// `quat_lll_reduce_O0_ideal`).
    pub fn reduce_o0_ideal(&self, alg: &Alg<P>, bound: usize) -> Self {
        let s = bounded(Alg::<P>::pinfty().p.sqrt_floor(), P::SQRT_P_BITS + 1);
        let in_bits = bound.max(P::SQRT_P_BITS + 1);
        let wbits = val_bits(in_bits);
        let hbits = 2 * wbits + P::P_BITS + 3;
        let m = &self.basis;
        let rows = |c: usize, r: usize| Zw {
            re: bounded(m[r][c], wbits),
            im: bounded(m[r + 1][c], wbits),
        };
        let scaled = |x: &Zi<P>| Zw {
            re: bounded(x.re * s, wbits),
            im: bounded(x.im * s, wbits),
        };
        let (e, f) = (rows(0, 2), rows(2, 2));
        let mut z = [rows(0, 0), rows(2, 0), scaled(&e), scaled(&f), e, f];
        lehmer_gaussian(&mut z, outer_its::<P>(in_bits), in_bits);
        let [a, mut b, _, _, e, mut f] = z;
        let mut h = hermitian([&a, &e, &b, &f], &alg.p, hbits);
        orient(&mut h, &mut [&mut b, &mut f]);
        let mut basis = [[Ibz::ZERO; 4]; 4];
        for (col, (z1, z2)) in [(0, (a, e)), (2, (b, f))] {
            let [r1, i1, r2, i2] = [z1.re, z1.im, z2.re, z2.im].map(|x| bounded(x, wbits));
            let cols = [[r1, i1, r2, i2], [-i1, r1, -i2, r2]];
            for (k, v) in cols.into_iter().enumerate() {
                for (r, x) in v.into_iter().enumerate() {
                    basis[r][col + k] = bounded(x, wbits);
                }
            }
        }
        Self {
            denom: self.denom,
            basis,
        }
    }
}
