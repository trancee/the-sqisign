//! The change of theta coordinates for a gluing (`gluing.c`:
//! `compute_symmetric_element`, `gluing_basis_compute`,
//! `gluing_apply_change_coord`, `gluing_couple_point_to_theta`).

use super::{CouplePoint, ThetaPoint};
use crate::ec::Point;
use crate::gf::{Fp2, FpParams};

/// The 4x4 change of theta coordinates induced by a basis of `(E1 x E2)[4]`.
pub struct GluingMatrix<P: FpParams> {
    /// Row-major coefficients.
    pub m: [[Fp2<P>; 4]; 4],
}

impl<P: FpParams> Clone for GluingMatrix<P> {
    fn clone(&self) -> Self {
        *self
    }
}
impl<P: FpParams> Copy for GluingMatrix<P> {}

/// A symmetric 2x2 matrix `((s00, s01), (s10, -s00))`-like translation action.
#[derive(Clone, Copy)]
struct Sym<P: FpParams> {
    s00: Fp2<P>,
    s01: Fp2<P>,
    s10: Fp2<P>,
}

impl<P: FpParams> Sym<P> {
    fn cswap(a: &mut Self, b: &mut Self, ctl: u32) {
        Fp2::cswap(&mut a.s00, &mut b.s00, ctl);
        Fp2::cswap(&mut a.s01, &mut b.s01, ctl);
        Fp2::cswap(&mut a.s10, &mut b.s10, ctl);
    }
}

/// The translation actions of `T1`, `T2` and `T1 + T2` for a basis of `E[4]`.
struct SymBasis<P: FpParams> {
    delta: Fp2<P>,
    g1: Sym<P>,
    g2: Sym<P>,
    g3: Sym<P>,
}

/// All-ones iff `a == b`, without branching (C `ct_eq_mask`).
fn ct_eq_mask(a: u32, b: u32) -> u32 {
    let d = a ^ b;
    ((d | d.wrapping_neg()) >> 31).wrapping_sub(1)
}

/// C `compute_symmetric_element`.
fn symmetric_element<P: FpParams>(t1: &Point<P>, t2: &Point<P>) -> SymBasis<P> {
    let mut pos = 255u32;
    let r1 = t1.x + t1.z;
    let s1 = t1.x - t1.z;
    let r2 = t2.x + t2.z;
    let s2 = t2.x - t2.z;
    pos ^= pos & s1.is_zero();
    pos ^= (pos ^ 1) & r1.is_zero();
    pos ^= (pos ^ 2) & s2.is_zero();
    pos ^= (pos ^ 3) & r2.is_zero();
    let s2 = r1 * s2;
    let r1 = s2.mul_by_i(1);
    let r2 = r2 * s1;
    pos ^= (pos ^ 4) & (r1 + r2).is_zero();
    pos ^= (pos ^ 5) & (r1 - r2).is_zero();

    let low = ct_eq_mask(pos >> 1, 0);
    let tmp1 = Fp2::select(&t1.x, &t2.x, low);
    let tmp2 = Fp2::select(&t1.z, &t2.z, low);
    let s1 = tmp1.square();
    let s2 = tmp2.square();
    let delta = s1 - s2;
    let r1 = s1 + s2;
    let r2 = tmp1 * tmp2;
    let r2 = r2 + r2;
    let mut g1 = Sym {
        s00: -r1,
        s01: r2,
        s10: -r2,
    };
    let g2_off = Fp2::select(&-delta, &delta, (pos & 1).wrapping_neg());
    let mut g2 = Sym {
        s00: Fp2::ZERO,
        s01: g2_off,
        s10: g2_off,
    };
    Sym::cswap(&mut g1, &mut g2, low);
    let flg1 = (ct_eq_mask(pos, 0) | ct_eq_mask(pos, 3) | ct_eq_mask(pos, 4)) & 1;
    let flg2 = (ct_eq_mask(pos, 1) | ct_eq_mask(pos, 2) | ct_eq_mask(pos, 5)) & 1;
    let mut g3 = Sym {
        s00: r2.mul_by_i(flg1),
        s01: r1.mul_by_i(flg2),
        s10: r1.mul_by_i(flg1),
    };
    Sym::cswap(&mut g2, &mut g3, ct_eq_mask(pos >> 1, 2));
    SymBasis { delta, g1, g2, g3 }
}

impl<P: FpParams> GluingMatrix<P> {
    /// The change of coordinates for the 4-torsion basis `K1_4`, `K2_4`
    /// (C `gluing_basis_compute`).
    pub fn from_four_torsion(k1_4: &CouplePoint<P>, k2_4: &CouplePoint<P>) -> Self {
        let b1 = symmetric_element(&k1_4.p1, &k2_4.p1);
        let b2 = symmetric_element(&k1_4.p2, &k2_4.p2);
        let delta12 = b1.delta * b2.delta;
        let row0 = |f1: fn(&Sym<P>) -> Fp2<P>, f2: fn(&Sym<P>) -> Fp2<P>| {
            f1(&b1.g1) * f2(&b2.g1) + f1(&b1.g2) * f2(&b2.g2) - f1(&b1.g3) * f2(&b2.g3)
        };
        let s00: fn(&Sym<P>) -> Fp2<P> = |s| s.s00;
        let s01: fn(&Sym<P>) -> Fp2<P> = |s| s.s01;
        let r0 = [
            delta12 + row0(s00, s00),
            row0(s00, s01),
            row0(s01, s00),
            row0(s01, s01),
        ];
        let g2 = &b2.g2;
        let r1 = [
            g2.s00 * r0[0] + g2.s10 * r0[1],
            g2.s01 * r0[0] - g2.s00 * r0[1],
            g2.s00 * r0[2] + g2.s10 * r0[3],
            g2.s01 * r0[2] - g2.s00 * r0[3],
        ];
        let g1 = &b1.g1;
        let act = |r: &[Fp2<P>; 4]| {
            [
                g1.s00 * r[0] + g1.s10 * r[2],
                g1.s00 * r[1] + g1.s10 * r[3],
                g1.s01 * r[0] - g1.s00 * r[2],
                g1.s01 * r[1] - g1.s00 * r[3],
            ]
        };
        let (r2, r3) = (act(&r0), act(&r1));
        Self {
            m: [
                r0.map(|v| v * delta12),
                r1.map(|v| v * b1.delta),
                r2.map(|v| v * b2.delta),
                r3,
            ],
        }
    }

    /// `M . P`, ignoring the `t` column unless `t_not_zero`
    /// (C `gluing_apply_change_coord`).
    pub fn apply(&self, p: &ThetaPoint<P>, t_not_zero: bool) -> ThetaPoint<P> {
        let row = |r: &[Fp2<P>; 4]| {
            let v = p.x * r[0] + p.y * r[1] + p.z * r[2];
            if t_not_zero {
                v + p.t * r[3]
            } else {
                v
            }
        };
        let [x, y, z, t] = self.m.each_ref().map(row);
        ThetaPoint::new(x, y, z, t)
    }

    /// The theta point of a couple point (C `gluing_couple_point_to_theta`).
    pub fn couple_point_to_theta(&self, t: &CouplePoint<P>) -> ThetaPoint<P> {
        let null = ThetaPoint::new(
            t.p1.x * t.p2.x,
            t.p1.x * t.p2.z,
            t.p2.x * t.p1.z,
            t.p1.z * t.p2.z,
        );
        self.apply(&null, true)
    }
}
