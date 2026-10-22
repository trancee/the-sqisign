//! Vectors, 2x2 matrices and Gaussian integers (vendor `dim2.c`).
//!
//! A [`Vec2`] `[a, b]` doubles as the Gaussian integer `a + bi`.

use super::{Mat2, Vec2};
use crate::mp::{Ibz, MpParams};

/// `[a0, a1]` with 32-bit bounds (C `ibz_vec_2_set`).
pub fn vec_from<P: MpParams>(a0: i32, a1: i32) -> Vec2<P> {
    [a0, a1].map(|x| Ibz::from_i64(x.into(), 32))
}

/// `a + b` (C `ibz_vec_2_add`).
pub fn vec_add<P: MpParams>(a: &Vec2<P>, b: &Vec2<P>) -> Vec2<P> {
    [a[0] + b[0], a[1] + b[1]]
}

/// `a - b` (C `ibz_vec_2_sub`).
pub fn vec_sub<P: MpParams>(a: &Vec2<P>, b: &Vec2<P>) -> Vec2<P> {
    [a[0] - b[0], a[1] - b[1]]
}

/// Whether `x` is zero (C `ibz_vec_2_is_zero`).
pub fn vec_is_zero<P: MpParams>(x: &Vec2<P>) -> bool {
    x[0].is_zero() && x[1].is_zero()
}

/// `mat * vec` (C `ibz_mat_2x2_eval`).
pub fn mat_eval<P: MpParams>(mat: &Mat2<P>, vec: &Vec2<P>) -> Vec2<P> {
    mat.map(|row| row[0] * vec[0] + row[1] * vec[1])
}

/// `a * b` (C `ibz_mat_2x2_mul`).
pub fn mat_mul<P: MpParams>(a: &Mat2<P>, b: &Mat2<P>) -> Mat2<P> {
    let mut sums = [[Ibz::ZERO; 2]; 2];
    for (i, row) in sums.iter_mut().enumerate() {
        for (j, s) in row.iter_mut().enumerate() {
            for k in 0..2 {
                *s = *s + a[i][k] * b[k][j];
            }
        }
    }
    sums
}

/// The adjugate of `mat` if its determinant is non-zero, and the determinant
/// (C `ibz_mat_2x2_inv_with_det_as_denom`, which leaves the output matrix
/// untouched for a singular `mat`).
pub fn mat_inv_with_det<P: MpParams>(mat: &Mat2<P>) -> (Option<Mat2<P>>, Ibz<P>) {
    let [[a, b], [c, d]] = *mat;
    let det = a * d - b * c;
    ((!det.is_zero()).then_some([[d, -b], [-c, a]]), det)
}

/// The inverse of `mat` modulo the positive `m`, entries in `[0, m)`, and
/// whether it exists; the zero matrix if it does not (C
/// `ibz_mat_2x2_inv_mod`).
pub fn mat_inv_mod<P: MpParams>(mat: &Mat2<P>, m: &Ibz<P>) -> (Mat2<P>, bool) {
    let [[a, b], [c, d]] = *mat;
    let det = ((a * d).modulo(m) - b * c).modulo(m);
    let (det, ok) = det.invmod(m);
    let det = det * Ibz::from_i64(ok.into(), 2);
    let inv = [[d, -b], [-c, a]].map(|row| row.map(|x| (x * det).modulo(m)));
    (inv, ok)
}

/// Replaces `mat`, whose entries lie in `[0, 2^e)`, by `2^e - mat` if its
/// first non-zero entry exceeds `2^(e-1)`: a canonical sign for a matrix
/// modulo `2^e` (C `ibz_mat_2x2_normalize`).
pub fn mat_normalize<P: MpParams>(mat: &mut Mat2<P>, e: usize) {
    let half = Ibz::ONE.mul_2exp(e - 1);
    let Some(first) = mat.iter().flatten().find(|x| !x.is_zero() && **x != half) else {
        return;
    };
    if *first > half {
        let full = half.mul_2exp(1);
        mat.iter_mut().flatten().for_each(|x| *x = full - *x);
    }
}

/// The Gaussian product `a * b` (C `ibz_vec_2_gaussian_mul`).
pub fn gaussian_mul<P: MpParams>(a: &Vec2<P>, b: &Vec2<P>) -> Vec2<P> {
    let r = (a[0] + a[1]) * b[0];
    let s = (a[0] - a[1]) * b[1];
    let t = (b[0] + b[1]) * a[1];
    [r - t, t + s]
}

/// Gaussian division with remainder `a = q * b + r`, `N(r) < N(b)`, the
/// quotient rounded coordinate-wise; `r` takes the bounds of `b` and `q`
/// the larger bound of `a` (C `ibz_vec_2_gaussian_euclidean_division`).
///
/// # Panics
///
/// If `b` is zero.
pub fn gaussian_div<P: MpParams>(a: &Vec2<P>, b: &Vec2<P>) -> (Vec2<P>, Vec2<P>) {
    let n = Ibz::sum_two_squares(&b[0], &b[1]);
    let q = gaussian_mul(&[b[0], -b[1]], a);
    let mut q = [q[0].rounded_div(&n), q[1].rounded_div(&n)];
    let mut r = vec_sub(a, &gaussian_mul(&q, b));
    let max_a = a[0].bound().max(a[1].bound());
    for (x, y) in r.iter_mut().zip(b) {
        x.set_bound(y.bound());
    }
    q.iter_mut().for_each(|x| x.set_bound(max_a));
    (q, r)
}

/// A Gaussian gcd of `a` and `b` (C `ibz_vec_2_gaussian_gcd`).
pub fn gaussian_gcd<P: MpParams>(a: &Vec2<P>, b: &Vec2<P>) -> Vec2<P> {
    let (mut q, mut r) = if vec_is_zero(a) { (*b, *a) } else { (*a, *b) };
    while !vec_is_zero(&r) {
        (q, r) = (r, gaussian_div(&q, &r).1);
    }
    q
}
