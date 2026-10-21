//! The gluing isogeny `E1 x E2 -> A` (`gluing.c`).

use super::{CoupleCurve, CouplePoint, GluingMatrix, ThetaPoint};
use crate::ec::{BaryCoords, Curve, Point};
use crate::gf::{Fp2, FpParams};

/// A gluing isogeny with the data needed to evaluate it.
pub struct Gluing<P: FpParams> {
    /// Inverse of the image of `K1_8`.
    pub inv_image_k1_8: ThetaPoint<P>,
    /// Inverse of the image of `K2_8`.
    pub inv_image_k2_8: ThetaPoint<P>,
    /// The change of theta coordinates on the domain.
    pub m: GluingMatrix<P>,
    /// Inverse of the dual codomain theta null point (`t = 0`).
    pub inv_dual_theta_null: ThetaPoint<P>,
    /// The codomain theta null point.
    pub codomain: ThetaPoint<P>,
}

impl<P: FpParams> Clone for Gluing<P> {
    fn clone(&self) -> Self {
        *self
    }
}
impl<P: FpParams> Copy for Gluing<P> {}

/// xDBLADD for a normalised `A24` and difference `(XPQ : 1)`
/// (C `gluing_special_xDBLADD`): `(2P, P + Q)`.
fn special_xdbladd<P: FpParams>(
    p: &Point<P>,
    q: &Point<P>,
    pq: &Point<P>,
    a24: &Point<P>,
) -> (Point<P>, Point<P>) {
    let t0 = p.x + p.z;
    let t1 = p.x - p.z;
    let rx = t0.square();
    let t2 = q.x - q.z;
    let sx = q.x + q.z;
    let t0 = t0 * t2;
    let rz = t1.square();
    let t1 = t1 * sx;
    let t2 = rx - rz;
    let rx = rx * rz;
    let sx = a24.x * t2;
    let sz = t0 - t1;
    let rz = (rz + sx) * t2;
    let sx = t0 + t1;
    (
        Point::new(rx, rz),
        Point::new(sx.square(), sz.square() * pq.x),
    )
}

/// `([2^pow2] P, [2^pow2] P - Q')` by iterating `(R1, R0) <- (2 R1, R1 + R0)`
/// from `(P, Q)` with constant difference `PmQ` (C `gluing_ladder`).
///
/// `curve` must have its `A24` normalised and `PmQ` must have `Z = 1`.
pub fn gluing_ladder<P: FpParams>(
    p: &Point<P>,
    q: &Point<P>,
    pmq: &Point<P>,
    pow2: usize,
    curve: &Curve<P>,
) -> (Point<P>, Point<P>) {
    (0..pow2).fold((*p, *q), |(r1, r0), _| {
        special_xdbladd(&r1, &r0, pmq, &curve.a24)
    })
}

/// Whether `K1_2`, `K2_2` are 2-torsion with independent components
/// (C `verify_two_torsion`).
fn verify_two_torsion<P: FpParams>(
    k1: &CouplePoint<P>,
    k2: &CouplePoint<P>,
    e12: &CoupleCurve<P>,
) -> bool {
    let torsion = e12.e1.is_two_torsion(&k1.p1)
        & e12.e2.is_two_torsion(&k1.p2)
        & e12.e1.is_two_torsion(&k2.p1)
        & e12.e2.is_two_torsion(&k2.p2);
    torsion != 0 && (k1.p1.ct_eq(&k2.p1) | k1.p2.ct_eq(&k2.p2)) == 0
}

impl<P: FpParams> Gluing<P> {
    /// The gluing with kernel `[4](K1_8, K2_8)` (C `gluing_compute`), or
    /// `None` if the kernel is malformed.
    pub fn compute(
        e12: &CoupleCurve<P>,
        k1_8: &CouplePoint<P>,
        k2_8: &CouplePoint<P>,
        dual_codomain: bool,
        verify: bool,
    ) -> Option<Self> {
        let k1_4 = k1_8.dbl(e12);
        let k2_4 = k2_8.dbl(e12);
        if verify && !verify_two_torsion(&k1_4.dbl(e12), &k2_4.dbl(e12), e12) {
            return None;
        }
        let m = GluingMatrix::from_four_torsion(&k1_4, &k2_4);
        let tt1 = m.couple_point_to_theta(k1_8).to_squared();
        let tt2 = m.couple_point_to_theta(k2_8).to_squared();
        if tt1.t.is_zero() & tt2.t.is_zero() == 0 {
            return None;
        }
        let zero =
            tt1.x.is_zero() | tt2.x.is_zero() | tt1.y.is_zero() | tt2.z.is_zero() | tt1.z.is_zero();
        if zero != 0 {
            return None;
        }
        let codomain = ThetaPoint::new(tt1.x * tt2.x, tt1.y * tt2.x, tt1.x * tt2.z, Fp2::ZERO);
        let inv = ThetaPoint::new(tt1.y * tt2.z, codomain.z, codomain.y, Fp2::ZERO);
        let (k1x, k1z) = (tt1.z * inv.z, tt1.x * inv.x);
        let (k2x, k2y) = (tt2.y * inv.y, tt2.x * inv.x);
        Some(Self {
            inv_image_k1_8: ThetaPoint::new(k1x, k1x, k1z, k1z),
            inv_image_k2_8: ThetaPoint::new(k2x, k2y, k2x, k2y),
            m,
            inv_dual_theta_null: inv,
            codomain: if dual_codomain {
                codomain
            } else {
                codomain.hadamard()
            },
        })
    }

    /// The image of `P + Q` from the barycentric coordinates of its
    /// components, scaled by `aux_inv` (C `gluing_eval_point_bary`).
    pub fn eval_bary(
        &self,
        c1: &BaryCoords<P>,
        c2: &BaryCoords<P>,
        aux_inv: &ThetaPoint<P>,
        dual_codomain: bool,
    ) -> ThetaPoint<P> {
        let x = c1.u * c2.u + c1.v * c2.v;
        let t1 = ThetaPoint::new(x, c1.u * c2.w, c1.w * c2.u, c1.w * c2.w);
        let t2 = ThetaPoint::new(
            (c1.u + c1.v) * (c2.u + c2.v) - x,
            c1.v * c2.w,
            c1.w * c2.v,
            Fp2::ZERO,
        );
        let t1 = self.m.apply(&t1, true).square();
        let t2 = self.m.apply(&t2, false).square();
        let diff = ThetaPoint::new(t1.x - t2.x, t1.y - t2.y, t1.z - t2.z, t1.t - t2.t);
        let image = diff.hadamard().pmul(aux_inv);
        if dual_codomain {
            image
        } else {
            image.hadamard()
        }
    }

    /// The image of a couple point with a zero component, or `None` if its
    /// squared theta point has `t != 0` (C `gluing_eval_point_special_case`).
    pub fn eval_special(&self, p: &CouplePoint<P>, dual_codomain: bool) -> Option<ThetaPoint<P>> {
        let t = self.m.couple_point_to_theta(p).to_squared();
        if t.t.is_zero() == 0 {
            return None;
        }
        let inv = &self.inv_dual_theta_null;
        let image = ThetaPoint::new(t.x * inv.x, t.y * inv.y, t.z * inv.z, Fp2::ZERO);
        Some(if dual_codomain {
            image
        } else {
            image.hadamard()
        })
    }

    /// The images of `ps` in non-dual coordinates, or `None` if any is
    /// rejected by [`Self::eval_special`].
    pub(super) fn eval_special_all(&self, ps: &[CouplePoint<P>]) -> Option<Vec<ThetaPoint<P>>> {
        ps.iter().map(|p| self.eval_special(p, false)).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::{Gluing, GluingMatrix, ThetaPoint};
    use crate::ec::Point;
    use crate::gf::{Fp2, P324};
    use crate::hd::CouplePoint;

    /// With the identity matrix the squared `t` coordinate is nonzero for
    /// generic points, which a genuine gluing never produces.
    #[test]
    fn special_evaluation_rejects_points_off_the_hyperplane() {
        let one = Fp2::<P324>::ONE;
        let mut m = [[Fp2::ZERO; 4]; 4];
        (0..4).for_each(|k| m[k][k] = one);
        let th = ThetaPoint::new(one, one, one, one);
        let g = Gluing {
            inv_image_k1_8: th,
            inv_image_k2_8: th,
            m: GluingMatrix { m },
            inv_dual_theta_null: th,
            codomain: th,
        };
        let p = Point::new(one.mul_small(2), one);
        let generic = CouplePoint { p1: p, p2: p };
        // Theta point (1 : 0 : 1 : 0), whose squared `t` is 1 - 0 - 1 + 0 = 0.
        let special = CouplePoint {
            p1: Point::new(one, one),
            p2: Point::new(one, Fp2::ZERO),
        };
        assert!(g.eval_special_all(&[special]).is_some());
        assert!(g.eval_special_all(&[special, generic]).is_none());
    }
}
