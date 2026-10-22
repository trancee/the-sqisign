//! Constant-time Minkowski reduction of an LLL-reduced dual (vendor
//! `lll/lll_dim4.c`, part 3): 27 short candidates sorted by norm through a
//! bitonic network, four independent ones chosen, signs canonicalised.

use super::config::LllConfig;
use super::ldl::Ldl;
use super::mk_select::{invert_transpose, select_basis, T4};
use super::word::neg_mask;
use super::{bounded, highmul_p};
use crate::mp::Ibz;
use crate::quat::Mat4;

/// Real candidates, padded to a power of two with sentinels.
const N_REAL: usize = 27;
const N_TOTAL: usize = 32;

/// One `(t1, t2, t3)` per sign pair of the nonzero points of `{-1,0,1}^3`.
const T_SUB: [[i8; 3]; 13] = [
    [0, 0, 1],
    [0, 1, -1],
    [0, 1, 0],
    [0, 1, 1],
    [1, -1, -1],
    [1, -1, 0],
    [1, -1, 1],
    [1, 0, -1],
    [1, 0, 0],
    [1, 0, 1],
    [1, 1, -1],
    [1, 1, 0],
    [1, 1, 1],
];

/// The chosen transform (C `ct_mk_result_t`).
pub struct Minkowski<P: LllConfig> {
    /// Chosen candidates, one per row.
    pub t: [T4; 4],
    /// `Ainv T^-1`, bound `AINV`.
    pub ainv: Mat4<P>,
    /// The lower triangle of the Gram matrix.
    pub g: Mat4<P>,
}

/// The lower triangle of `L D L^T` (C `ct_mk_build_gram`).
fn build_gram<P: LllConfig>(st: &Ldl<P>, prec: usize) -> Mat4<P> {
    let nb = P::GRAM;
    // `dl[i][k]` is C's `DL[k][i]`, `D[k] L[i][k]`.
    let mut dl = [[Ibz::ZERO; 4]; 4];
    for (i, row) in dl.iter_mut().enumerate() {
        for (k, x) in row[..i].iter_mut().enumerate() {
            *x = highmul_p(&st.d[k], &st.l[i][k], prec, nb);
        }
        row[i] = bounded(st.d[i], nb);
    }
    let mut g = [[Ibz::ZERO; 4]; 4];
    for (i, row) in g.iter_mut().enumerate() {
        for j in 0..=i {
            row[j] = (0..j).fold(bounded(dl[i][j], nb), |acc, k| {
                bounded(acc + highmul_p(&dl[i][k], &st.l[j][k], prec, nb), nb)
            });
        }
    }
    g
}

/// `sum_{j <= i} coeff(i, j) G[i][j]` over the Gram bound.
fn eval<P: LllConfig>(g: &Mat4<P>, coeff: impl Fn(usize, usize) -> i32) -> Ibz<P> {
    let nb = P::GRAM;
    let mut total = bounded(Ibz::ZERO, nb);
    for (i, row) in g.iter().enumerate() {
        for (j, gij) in row.iter().enumerate().take(i + 1) {
            total = bounded(total + gij.mul_i32_bounded(coeff(i, j), nb), nb);
        }
    }
    total
}

/// `t^T G t` (C `ct_mk_eval_norm`).
pub fn eval_norm<P: LllConfig>(g: &Mat4<P>, t: &T4) -> Ibz<P> {
    let t = t.map(i32::from);
    eval(g, |i, j| t[i] * t[j] * if i == j { 1 } else { 2 })
}

/// `<t, u>_G` (C `ct_mk_eval_inner`).
fn eval_inner<P: LllConfig>(g: &Mat4<P>, t: &T4, u: &T4) -> Ibz<P> {
    let (t, u) = (t.map(i32::from), u.map(i32::from));
    eval(g, |i, j| {
        if i == j {
            t[i] * u[i]
        } else {
            t[i] * u[j] + t[j] * u[i]
        }
    })
}

/// `floor(-(t1 L[1][0] + t2 L[2][0] + t3 L[3][0]) / 2^prec)` (C
/// `ct_mk_t0_floor`).
fn t0_floor<P: LllConfig>(st: &Ldl<P>, t123: &[i8; 3], prec: usize) -> i32 {
    let w = P::L;
    let shift = (1..4).fold(bounded(Ibz::ZERO, w), |acc, j| {
        bounded(acc + st.l[j][0].mul_i32_bounded(t123[j - 1].into(), w), w)
    });
    let neg = -shift;
    neg.ct_shift(-(prec as i32), w as u32, P::L - P::PREC + 16)
        .get()
}

/// The 27 candidate norms and coordinates, padded with sentinels (C
/// `gen_candidates`).
fn candidates<P: LllConfig>(
    st: &Ldl<P>,
    g: &Mat4<P>,
    prec: usize,
) -> ([Ibz<P>; N_TOTAL], [T4; N_TOTAL]) {
    let sentinel = bounded(bounded(Ibz::ONE, P::GRAM).mul_2exp(P::GRAM - 4), P::GRAM);
    let mut keys = [sentinel; N_TOTAL];
    let mut tv = [[0i8; 4]; N_TOTAL];
    tv[0] = [1, 0, 0, 0];
    for (s, sub) in T_SUB.iter().enumerate() {
        let t0 = t0_floor(st, sub, prec);
        for which in 0..2 {
            tv[1 + 2 * s + which] = [(t0 + which as i32) as i8, sub[0], sub[1], sub[2]];
        }
    }
    for (key, t) in keys.iter_mut().zip(&tv).take(N_REAL) {
        *key = eval_norm(g, t);
    }
    (keys, tv)
}

/// Sorts ascending by key, carrying the coordinates (C `bitonic_sort32`).
fn bitonic_sort<P: LllConfig>(keys: &mut [Ibz<P>; N_TOTAL], tv: &mut [T4; N_TOTAL]) {
    for key in keys.iter_mut() {
        key.set_bound(P::GRAM);
    }
    let mut k = 2;
    while k <= N_TOTAL {
        let mut j = k >> 1;
        while j > 0 {
            for i in 0..N_TOTAL {
                let l = i ^ j;
                if l > i {
                    let lt = keys[l].ct_lt_mask(&keys[i]);
                    let swap = if i & k == 0 { lt } else { !lt };
                    let (lo, hi) = keys.split_at_mut(l);
                    Ibz::cswap(&mut lo[i], &mut hi[0], swap);
                    let (lo, hi) = tv.split_at_mut(l);
                    for (a, b) in lo[i].iter_mut().zip(hi[0].iter_mut()) {
                        let t = (*a ^ *b) & swap as i8;
                        *a ^= t;
                        *b ^= t;
                    }
                }
            }
            j >>= 1;
        }
        k <<= 1;
    }
}

/// Flips rows so that `<T[0], T[i]>_G >= 0` (C `ct_mk_canonicalize_T`).
fn canonicalize<P: LllConfig>(t: &mut [T4; 4], g: &Mat4<P>) {
    let first = t[0];
    for row in t.iter_mut().skip(1) {
        let m = neg_mask(&eval_inner(g, &first, row)) as i8;
        for v in row.iter_mut() {
            *v = (v.wrapping_neg() & m) | (*v & !m);
        }
    }
}

/// Minkowski-reduces an LLL-reduced state (C `ct_minkowski_reduce`).
pub fn reduce<P: LllConfig>(st: &Ldl<P>, prec: usize) -> Minkowski<P> {
    let g = build_gram(st, prec);
    let (mut keys, mut tv) = candidates(st, &g, prec);
    bitonic_sort(&mut keys, &mut tv);
    let mut t = select_basis(&tv);
    canonicalize(&mut t, &g);
    let v = invert_transpose(&t);
    let ab = P::AINV;
    let ainv = st.ainv.map(|row| {
        core::array::from_fn(|j| {
            (0..4).fold(bounded(Ibz::ZERO, ab), |acc, c| {
                bounded(acc + row[c].mul_i32_bounded(v[j][c] as i32, ab), ab)
            })
        })
    });
    Minkowski { t, ainv, g }
}
