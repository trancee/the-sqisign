//! Vectors of 4 integers, 4x4 matrices and quadratic forms (vendor
//! `dim4.c`).

use super::{Mat4, Vec4};
use crate::mp::{Ibz, MpParams};

/// `[c0, c1, c2, c3]` with 32-bit bounds (C `ibz_vec_4_set`).
pub fn vec_from<P: MpParams>(c: [i32; 4]) -> Vec4<P> {
    c.map(|x| Ibz::from_i64(x.into(), 32))
}

/// `a + b` (C `ibz_vec_4_add`).
pub fn vec_add<P: MpParams>(a: &Vec4<P>, b: &Vec4<P>) -> Vec4<P> {
    core::array::from_fn(|i| a[i] + b[i])
}

/// `a - b` (C `ibz_vec_4_sub`).
pub fn vec_sub<P: MpParams>(a: &Vec4<P>, b: &Vec4<P>) -> Vec4<P> {
    core::array::from_fn(|i| a[i] - b[i])
}

/// Whether `x` is zero (C `ibz_vec_4_is_zero`).
pub fn vec_is_zero<P: MpParams>(x: &Vec4<P>) -> bool {
    x.iter().all(Ibz::is_zero)
}

/// The gcd of the coordinates (C `ibz_vec_4_content`).
pub fn vec_content<P: MpParams>(v: &Vec4<P>) -> Ibz<P> {
    let g = v[0].gcd(&v[1]);
    let g = v[2].gcd(&g);
    v[3].gcd(&g)
}

/// `scalar * vec` (C `ibz_vec_4_scalar_mul`).
pub fn vec_scalar_mul<P: MpParams>(scalar: &Ibz<P>, vec: &Vec4<P>) -> Vec4<P> {
    vec.map(|x| x * *scalar)
}

/// The quotients of `vec` by `scalar`, rounded towards zero, and whether
/// `scalar` divides every coordinate (C `ibz_vec_4_scalar_div`).
///
/// # Panics
///
/// If `scalar` is zero.
pub fn vec_scalar_div<P: MpParams>(scalar: &Ibz<P>, vec: &Vec4<P>) -> (Vec4<P>, bool) {
    let qr = vec.map(|x| x.div_rem(scalar));
    (qr.map(|(q, _)| q), qr.iter().all(|(_, r)| r.is_zero()))
}

/// The zero matrix (C `ibz_mat_4x4_zero`).
pub fn mat_zero<P: MpParams>() -> Mat4<P> {
    [[Ibz::ZERO; 4]; 4]
}

/// The identity matrix (C `ibz_mat_4x4_identity`).
pub fn mat_identity<P: MpParams>() -> Mat4<P> {
    core::array::from_fn(|i| core::array::from_fn(|j| if i == j { Ibz::ONE } else { Ibz::ZERO }))
}

/// `scalar * mat` (C `ibz_mat_4x4_scalar_mul`).
pub fn mat_scalar_mul<P: MpParams>(scalar: &Ibz<P>, mat: &Mat4<P>) -> Mat4<P> {
    mat.map(|row| vec_scalar_mul(scalar, &row))
}

/// `mat * vec` (C `ibz_mat_4x4_eval`).
pub fn mat_eval<P: MpParams>(mat: &Mat4<P>, vec: &Vec4<P>) -> Vec4<P> {
    mat.map(|row| (0..4).fold(Ibz::ZERO, |s, j| s + row[j] * vec[j]))
}

/// `vec^T * mat` (C `ibz_mat_4x4_eval_t`).
pub fn mat_eval_t<P: MpParams>(vec: &Vec4<P>, mat: &Mat4<P>) -> Vec4<P> {
    core::array::from_fn(|i| (0..4).fold(Ibz::ZERO, |s, j| s + mat[j][i] * vec[j]))
}

/// `coord^T * qf * coord` (C `quat_qf_eval`).
pub fn qf_eval<P: MpParams>(qf: &Mat4<P>, coord: &Vec4<P>) -> Ibz<P> {
    let sum = mat_eval(qf, coord);
    (1..4).fold(sum[0] * coord[0], |s, i| s + sum[i] * coord[i])
}
