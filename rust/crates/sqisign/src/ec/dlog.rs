//! Discrete logarithms of `2^e`-torsion bases via Tate pairings (vendor
//! `biextension.c`, `pairing_dlog_2_tate` and `fp2_dlog_2e`).

use super::biext::{cubical_add, cubical_dbladd, final_exp, frob_ratio, point_ratio, translate};
use super::{Basis, Curve, Point};
use crate::gf::{Fp2, FpParams};

/// Room for the power stacks: `floor(log2 e) + 1` entries (`e <= T < 2^15`).
const STACK: usize = 16;

/// Coordinates of `R = [r1] P + [r2] Q` and `S = [s1] P + [s2] Q`, as
/// little-endian words of `ORDER_WORDS` each.
pub struct Dlog {
    /// `r1`.
    pub r1: Vec<u64>,
    /// `r2`.
    pub r2: Vec<u64>,
    /// `s1`.
    pub s1: Vec<u64>,
    /// `s2`.
    pub s2: Vec<u64>,
}

/// Writes the base-2 digits of `log_g f` for `f` of order `2^len` into
/// `out` from bit `at`; `false` if `f` is not a power of `g` (C
/// `fp2_dlog_2e_rec`; `f[top]`, `g[top]` are the current powers).
fn dlog_rec<P: FpParams>(
    out: &mut [u64],
    at: usize,
    len: usize,
    f: &mut [Fp2<P>; STACK],
    g: &mut [Fp2<P>; STACK],
    top: usize,
) -> bool {
    // `len >= 1`: the top-level order is positive and both halves are non-empty.
    match len {
        1 => {
            let f_is_one = f[top - 1].is_one();
            let f_is_g = f[top - 1].ct_eq(&g[top - 1]);
            out[at / 64] |= u64::from(f_is_one.wrapping_add(1)) << (at % 64);
            for i in 0..top - 1 {
                let fg = f[i] * g[i];
                f[i] = Fp2::select(&f[i], &fg, !f_is_one);
                g[i] = g[i].square();
            }
            (f_is_one | f_is_g) != 0
        }
        _ => {
            let right = len / 2;
            let left = len - right;
            (f[top], g[top]) = (f[top - 1], g[top - 1]);
            for _ in 0..left {
                f[top] = f[top].square();
                g[top] = g[top].square();
            }
            dlog_rec(out, at, right, f, g, top + 1) && dlog_rec(out, at + right, left, f, g, top)
        }
    }
}

/// `log_g f` for `f`, `g` of order dividing `2^e`, given `1/g`; zero (or the
/// low bit when `e = 1`) if `f` is not a power of `g`, as in the C build with
/// `NDEBUG` (C `fp2_dlog_2e`).
fn dlog_2e<P: FpParams>(f: &Fp2<P>, g_inv: &Fp2<P>, e: usize) -> Vec<u64> {
    let mut fs = [Fp2::ZERO; STACK];
    let mut gs = [Fp2::ZERO; STACK];
    (fs[0], gs[0]) = (*f, *g_inv);
    let mut out = vec![0u64; P::ORDER_WORDS];
    if !dlog_rec(&mut out, 0, e, &mut fs, &mut gs, 1) && e > 1 {
        out.fill(0);
    }
    out
}

/// `kappa(P, Q)`: the coefficients `(k00, k01, k11)` of the quadratic with
/// roots `x(P + Q)`, `x(P - Q)` (C `compute_kappa`).
fn kappa<P: FpParams>(p: &Point<P>, q: &Point<P>, e: &Curve<P>) -> [Fp2<P>; 3] {
    let (xx, xz, zx, zz) = (p.x * q.x, p.x * q.z, p.z * q.x, p.z * q.z);
    let k01 = xx * zz * e.a;
    let k01 = k01 + k01 + (xx + zz) * (xz + zx) * e.c;
    [(xx - zz).square(), k01 + k01, (xz - zx).square()]
}

/// `x(P1 - P2)` as the common root of `kappa(P1, P2)` and
/// `kappa(P1 - Q, P2 - Q)` (C `shared_difference_point`).
fn shared_difference<P: FpParams>(
    p1: &Point<P>,
    p2: &Point<P>,
    p1q: &Point<P>,
    p2q: &Point<P>,
    e: &Curve<P>,
) -> Point<P> {
    let [k00, k01, k11] = kappa(p1, p2, e);
    let [k00b, k01b, k11b] = kappa(p1q, p2q, e);
    Point::new(k01b * k00 - k01 * k00b, (k11b * k00 - k11 * k00b) * e.c)
}

/// Normalises `points` to `(x/z : 1)` with one batched inversion that also
/// covers `extra`; returns the inverses of `extra`.
fn normalize_all<P: FpParams, const N: usize>(
    points: &mut [&mut Point<P>],
    extra: [Fp2<P>; N],
) -> [Fp2<P>; N] {
    let mut t: Vec<Fp2<P>> = extra.to_vec();
    t.extend(points.iter().map(|p| p.z));
    Fp2::batched_inv(&mut t);
    for (p, iz) in points.iter_mut().zip(&t[N..]) {
        **p = Point::new(p.x * *iz, Fp2::ONE);
    }
    core::array::from_fn(|i| t[i])
}

impl<P: FpParams> Curve<P> {
    /// `(r1, r2, s1, s2)` with `R = [r1] P + [r2] Q` and `S = [s1] P + [s2] Q`,
    /// for a basis `PQ` of `E[2^T]` and `RS` of `E[2^e]`. Normalises `A24`
    /// and rescales the curve to `(A/C : 1)` (C `pairing_dlog_2_tate`).
    ///
    /// # Panics
    /// If `e` is 0 or exceeds `T`.
    pub fn dlog_2_tate(&mut self, pq: &Basis<P>, rs: &Basis<P>, e: usize) -> Dlog {
        assert!(e > 0 && e <= P::T as usize, "unsupported dlog order");
        self.normalize_a24();
        let a24 = self.a24;
        let (mut pq, mut rs) = (*pq, *rs);

        // x(P - R), then the other differences from it.
        let [k00, k01, k11] = kappa(&pq.p, &rs.p, self);
        let z = k00 * k11;
        let z = z + z;
        let (root, _) = (k01.square() - (z + z)).sqrt_verify();
        let mut pmr = Point::new(k01 + root, k11 + k11);
        let mut rmq = shared_difference(&rs.p, &pq.q, &pmr, &pq.pmq, self);
        let mut pms = shared_difference(&pq.p, &rs.q, &pmr, &rs.pmq, self);
        let mut smq = shared_difference(&rs.q, &pq.q, &pms, &pq.pmq, self);

        // One batched inversion: 1/C, 1/x(P), 1/x(Q) and every z.
        let (px, qx) = (pq.p.x, pq.q.x);
        let (pz, qz) = (pq.p.z, pq.q.z);
        let [ic, ixp, ixq] = normalize_all(
            &mut [
                &mut pq.p,
                &mut pq.q,
                &mut rs.p,
                &mut rs.q,
                &mut pq.pmq,
                &mut pmr,
                &mut pms,
                &mut rmq,
                &mut smq,
            ],
            [self.c, px, qx],
        );
        self.a = self.a * ic;
        self.c = Fp2::ONE;
        let (ix_p, ix_q) = (pz * ixp, qz * ixq);

        // Cubical ladders: P + [2^T] Q-style monodromies (C `tate_dlog_partial`).
        let (mut np, mut npq) = (pq.p, pq.pmq);
        for _ in 0..P::T as usize - 1 {
            (npq, np) = cubical_dbladd(&npq, &np, &ix_q, &a24);
        }
        let (mut nr, mut ns) = (rs.p, rs.q);
        for _ in 0..e - 1 {
            pmr = cubical_add(&pmr, &nr, &ix_p);
            (rmq, nr) = cubical_dbladd(&rmq, &nr, &ix_q, &a24);
            pms = cubical_add(&pms, &ns, &ix_p);
            (smq, ns) = cubical_dbladd(&smq, &ns, &ix_q, &a24);
        }
        let npq = translate(&npq, &np);
        let (pmr, rmq) = (translate(&pmr, &nr), translate(&rmq, &nr));
        let (pms, smq) = (translate(&pms, &ns), translate(&smq, &ns));
        let (np, nr, ns) = (
            translate(&np, &np),
            translate(&nr, &nr),
            translate(&ns, &ns),
        );

        // Tate pairings t(P, Q), t(R, P), t(R, Q), t(S, P), t(S, Q) as w1 / w2.
        let ratios = [
            (point_ratio(&npq, &np, &pq.q), false),
            (point_ratio(&pmr, &nr, &pq.p), false),
            (point_ratio(&rmq, &nr, &pq.q), true),
            (point_ratio(&pms, &ns, &pq.p), false),
            (point_ratio(&smq, &ns, &pq.q), true),
        ];
        let (mut w1, mut w2) = ([Fp2::ZERO; 5], [Fp2::ZERO; 5]);
        for (i, (t, inverted)) in ratios.iter().enumerate() {
            let (num, den) = if *inverted { (t.z, t.x) } else { (t.x, t.z) };
            (w1[i], w2[i]) = frob_ratio(&num, &den);
        }
        Fp2::batched_inv(&mut w2);
        let w: [Fp2<P>; 5] = core::array::from_fn(|i| final_exp(&(w1[i] * w2[i]), e));
        Dlog {
            r2: dlog_2e(&w[1], &w[0], e),
            r1: dlog_2e(&w[2], &w[0], e),
            s2: dlog_2e(&w[3], &w[0], e),
            s1: dlog_2e(&w[4], &w[0], e),
        }
    }
}
