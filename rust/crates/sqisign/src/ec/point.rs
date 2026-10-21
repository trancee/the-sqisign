//! x-only point arithmetic (vendor `ec.c`).

use super::{BaryCoords, Point};
use crate::gf::{Fp2, FpParams};

impl<P: FpParams> Point<P> {
    /// The identity `(1 : 0)`.
    pub const IDENTITY: Self = Self {
        x: Fp2::ONE,
        z: Fp2::ZERO,
    };

    /// The point `(x : z)`.
    pub fn new(x: Fp2<P>, z: Fp2<P>) -> Self {
        Self { x, z }
    }

    /// `0xFFFF_FFFF` if this is the identity, else 0 (C `ec_is_zero`).
    pub fn is_zero(&self) -> u32 {
        self.z.is_zero()
    }

    /// `0xFFFF_FFFF` if `X` or `Z` is zero, else 0 (C `ec_has_zero_coordinate`).
    pub fn has_zero_coordinate(&self) -> u32 {
        self.x.is_zero() | self.z.is_zero()
    }

    /// Projective equality, with the exact return values of C `ec_is_equal`:
    /// `0xFFFF_FFFF` if both are the identity, `1` if both are finite and equal,
    /// `0` otherwise. Callers must only test it for non-zero.
    pub fn ct_eq(&self, q: &Self) -> u32 {
        let l_zero = self.is_zero();
        let r_zero = q.is_zero();
        let lr_equal = (self.x * q.z).ct_eq(&(self.z * q.x));
        (l_zero & r_zero) | (!l_zero & (!r_zero).wrapping_mul(lr_equal))
    }

    /// `(X / Z : 1)` (C `ec_normalize_point`).
    pub fn normalize(&self) -> Self {
        let zi = self.z.inv();
        Self::new(self.x * zi, Fp2::ONE)
    }

    /// `a0` if `ctl == 0`, `a1` if `ctl == 0xFFFF_FFFF`.
    pub fn select(a0: &Self, a1: &Self, ctl: u32) -> Self {
        Self::new(
            Fp2::select(&a0.x, &a1.x, ctl),
            Fp2::select(&a0.z, &a1.z, ctl),
        )
    }

    /// Swaps `a` and `b` if `ctl == 0xFFFF_FFFF`.
    pub fn cswap(a: &mut Self, b: &mut Self, ctl: u32) {
        Fp2::cswap(&mut a.x, &mut b.x, ctl);
        Fp2::cswap(&mut a.z, &mut b.z, ctl);
    }

    /// `2P` on `E0: y^2 = x^3 + x` (C `ec_xDBL_E0`).
    pub fn xdbl_e0(&self) -> Self {
        let t0 = (self.x + self.z).square();
        let t1 = (self.x - self.z).square();
        let t2 = t0 - t1;
        let t1 = t1 + t1;
        Self::new(t0 * t1, (t1 + t2) * t2)
    }

    /// `2P` from the curve constants `ac = (A : C)` (C `ec_xDBL`).
    pub fn xdbl(&self, ac: &Self) -> Self {
        let t0 = (self.x + self.z).square();
        let t1 = (self.x - self.z).square();
        let t2 = t0 - t1;
        let t3 = ac.z + ac.z;
        let t1 = t1 * t3;
        let t1 = t1 + t1;
        let x = t0 * t1;
        let t0 = (t3 + ac.x) * t2 + t1;
        Self::new(x, t0 * t2)
    }

    /// `2P` from `A24 = (A + 2C : 4C)`, or `((A + 2C) / 4C : 1)` when
    /// `normalized` (C `ec_xDBL_A24`).
    pub fn xdbl_a24(&self, a24: &Self, normalized: bool) -> Self {
        let t0 = (self.x + self.z).square();
        let mut t1 = (self.x - self.z).square();
        let t2 = t0 - t1;
        if !normalized {
            t1 = t1 * a24.z;
        }
        let x = t0 * t1;
        let t0 = t2 * a24.x + t1;
        Self::new(x, t0 * t2)
    }

    /// Differential addition `P + Q` given `pq = P - Q` (C `ec_xADD`).
    pub fn xadd(&self, q: &Self, pq: &Self) -> Self {
        let t0 = (self.x + self.z) * (q.x - q.z);
        let t1 = (self.x - self.z) * (q.x + q.z);
        let t2 = (t0 + t1).square();
        let t3 = (t0 - t1).square();
        Self::new(pq.z * t2, pq.x * t3)
    }

    /// `(2P, P + Q)` given `pq = P - Q` and normalised `a24` (C `ec_xDBLADD`).
    pub fn xdbladd(&self, q: &Self, pq: &Self, a24: &Self) -> (Self, Self) {
        let t0 = self.x + self.z;
        let t1 = self.x - self.z;
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
        let s = Self::new(sx.square() * pq.z, sz.square() * pq.x);
        (Self::new(rx, rz), s)
    }
}

impl<P: FpParams> BaryCoords<P> {
    /// Barycentric coordinates of `P`, `Q` with difference `pmq`
    /// (C `ec_points_to_bary_coordinates`).
    pub fn from_points(p: &Point<P>, q: &Point<P>, pmq: &Point<P>) -> Self {
        let t0 = (p.x + p.z) * (q.x - q.z);
        let t1 = (p.x - p.z) * (q.x + q.z);
        let lambda = t1 + t0;
        let mu = t1 - t0;
        let t0 = (pmq.z * lambda).square();
        let t1 = mu * pmq.x;
        let t2 = mu * pmq.z;
        let t3 = t1.square();
        let w = t1 * t2;
        Self {
            u: t3 + t0,
            v: t3 - t0,
            w: w + w,
        }
    }
}
