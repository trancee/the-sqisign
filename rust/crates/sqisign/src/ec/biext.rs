//! Cubical biextension arithmetic and the reduced Tate pairing (vendor
//! `biextension.c`). As in C, `cubical_add` is off by a factor 4, which the
//! final exponentiation removes.

use super::params::cofactor;
use super::{Curve, Point};
use crate::gf::{Fp2, FpParams};

/// `P + Q` from cubical `P`, `Q` and `x(P - Q) = (1 : ix_pq)` (C `cubicalADD`).
pub(super) fn cubical_add<P: FpParams>(p: &Point<P>, q: &Point<P>, ix_pq: &Fp2<P>) -> Point<P> {
    let t0 = (p.x + p.z) * (q.x - q.z);
    let t1 = (p.x - p.z) * (q.x + q.z);
    Point::new(*ix_pq * (t0 + t1).square(), (t0 - t1).square())
}

/// `(P + Q, [2] Q)` for `A24 = ((A + 2C) / 4C : 1)` (C `cubicalDBLADD`).
pub(super) fn cubical_dbladd<P: FpParams>(
    p: &Point<P>,
    q: &Point<P>,
    ix_pq: &Fp2<P>,
    a24: &Point<P>,
) -> (Point<P>, Point<P>) {
    let (s, d) = (q.x + q.z, q.x - q.z);
    let (t2, qqz) = (s.square(), d.square());
    let t0 = (p.x + p.z) * d;
    let t1 = (p.x - p.z) * s;
    let ppq = Point::new(*ix_pq * (t0 + t1).square(), (t0 - t1).square());
    let t3 = t2 - qqz;
    let qq = Point::new(t2 * qqz, (t3 * a24.x + qqz) * t3);
    (ppq, qq)
}

/// The monodromy ratio `(nQ.x P.x : PnQ.x)` above `(1 : 0)` (C `point_ratio`).
pub(super) fn point_ratio<P: FpParams>(pnq: &Point<P>, nq: &Point<P>, p: &Point<P>) -> Point<P> {
    Point::new(nq.x * p.x, pnq.x)
}

/// The cubical translation of `p` by the 2-torsion point `t`, in constant
/// time (C `translate`).
pub(super) fn translate<P: FpParams>(p: &Point<P>, t: &Point<P>) -> Point<P> {
    let x = t.x * p.x - t.z * p.z;
    let z = t.z * p.x - t.x * p.z;
    let ta_zero = t.x.is_zero();
    let (x, z) = (
        Fp2::select(&x, &p.z, ta_zero),
        Fp2::select(&z, &p.x, ta_zero),
    );
    let tb_zero = t.z.is_zero();
    Point::new(
        Fp2::select(&x, &p.x, tb_zero),
        Fp2::select(&z, &p.z, tb_zero),
    )
}

/// `w^(p - 1)` as a projective pair `(num, den)` for `w = num / den`, folded
/// with the Frobenius: returns `(den * conj(num), num * conj(den))`.
pub(super) fn frob_ratio<P: FpParams>(num: &Fp2<P>, den: &Fp2<P>) -> (Fp2<P>, Fp2<P>) {
    (*den * num.frob(), *num * den.frob())
}

/// `w^((p + 1) / 2^T)` then `2^(T - e)` squarings (C `clear_cofac` and loop).
pub(super) fn final_exp<P: FpParams>(w: &Fp2<P>, e: usize) -> Fp2<P> {
    let mut r = w.pow_vartime(&cofactor::<P>());
    for _ in e..P::T as usize {
        r = r.square();
    }
    r
}

impl<P: FpParams> Curve<P> {
    /// The reduced Tate pairing of order `2^e` of `P`, `Q` with `PQ = x(P + Q)`;
    /// normalises `A24` (C `pairing_reduced_tate`).
    ///
    /// # Panics
    /// If `e` is 0 or exceeds `T`.
    pub fn reduced_tate(&mut self, e: usize, p: &Point<P>, q: &Point<P>, pq: &Point<P>) -> Fp2<P> {
        assert!(e > 0 && e <= P::T as usize, "unsupported pairing order");
        let mut t = [p.x, p.z, q.x, q.z];
        Fp2::batched_inv(&mut t);
        let ix_q = q.z * t[2];
        let pn = Point::new(p.x * t[1], Fp2::ONE);
        let qn = Point::new(q.x * t[3], Fp2::ONE);
        self.normalize_a24();
        let a24 = self.a24;

        // Monodromy of Q + [2^e] P (C `monodromy_i` with the roles swapped).
        let (mut pnq, mut nq) = (*pq, pn);
        for _ in 0..e - 1 {
            (pnq, nq) = cubical_dbladd(&pnq, &nq, &ix_q, &a24);
        }
        let pnq = translate(&pnq, &nq);
        let nq = translate(&nq, &nq);
        let r = point_ratio(&pnq, &nq, &qn);

        let (x, z) = frob_ratio(&r.x, &r.z);
        final_exp(&(x.inv() * z), e)
    }
}
