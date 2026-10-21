//! Theta-point arithmetic and doubling on a theta structure
//! (`theta_structure.h`, `theta_structure.c`).

use super::ThetaPoint;
use crate::gf::{Fp2, FpParams};

/// A level-2 theta structure, kept in the dual form used by the doubling.
pub struct ThetaStructure<P: FpParams> {
    /// The inverse of the dual theta null point.
    pub inv_dual_null_point: ThetaPoint<P>,
    /// `(Az, Ax, Bx, Bw)`, from which the dual null point is recovered.
    pub dbl_data: ThetaPoint<P>,
    /// The inverse of the squared dual theta null point, valid when
    /// `precomputation` holds.
    pub inv_sqr_null_point: ThetaPoint<P>,
    /// Whether `inv_sqr_null_point` is up to date.
    pub precomputation: bool,
}

impl<P: FpParams> Clone for ThetaStructure<P> {
    fn clone(&self) -> Self {
        *self
    }
}
impl<P: FpParams> Copy for ThetaStructure<P> {}

impl<P: FpParams> ThetaPoint<P> {
    /// `(0 : 0 : 0 : 0)`, a placeholder.
    pub const ZERO: Self = Self::new(Fp2::ZERO, Fp2::ZERO, Fp2::ZERO, Fp2::ZERO);

    /// `(x : y : z : t)`.
    pub const fn new(x: Fp2<P>, y: Fp2<P>, z: Fp2<P>, t: Fp2<P>) -> Self {
        Self { x, y, z, t }
    }

    /// The Hadamard transform (C `theta_hadamard`).
    pub fn hadamard(&self) -> Self {
        let t1 = self.x + self.y;
        let t2 = self.x - self.y;
        let t3 = self.z + self.t;
        let t4 = self.z - self.t;
        Self::new(t1 + t3, t2 + t4, t1 - t3, t2 - t4)
    }

    /// The projective inverse `(yzt : xzt : xyt : xyz)` (C `theta_invert_point`).
    pub fn invert(&self) -> Self {
        let a = self.x * self.y;
        let d = self.z * self.t;
        Self::new(self.y * d, self.x * d, self.t * a, self.z * a)
    }

    /// Coordinate-wise square (C `pointwise_square`).
    pub fn square(&self) -> Self {
        Self::new(
            self.x.square(),
            self.y.square(),
            self.z.square(),
            self.t.square(),
        )
    }

    /// Coordinate-wise product (C `pointwise_product`).
    pub fn pmul(&self, q: &Self) -> Self {
        Self::new(self.x * q.x, self.y * q.y, self.z * q.z, self.t * q.t)
    }

    /// Hadamard of the coordinate-wise square (C `to_squared_theta`).
    pub fn to_squared(&self) -> Self {
        self.square().hadamard()
    }

    /// All-ones iff `xt == yz`, i.e. the point splits as a product
    /// (C `theta_is_product_theta_point`).
    pub fn is_product(&self) -> u32 {
        (self.x * self.t).ct_eq(&(self.y * self.z))
    }
}

impl<P: FpParams> ThetaStructure<P> {
    /// The dual theta null point (C `theta_extract_dual_null_point`).
    pub fn dual_null_point(&self) -> ThetaPoint<P> {
        let d = &self.dbl_data;
        let tmp = d.x * d.t;
        ThetaPoint::new(
            tmp * d.y,
            tmp * d.z,
            self.inv_dual_null_point.t,
            self.inv_dual_null_point.z,
        )
    }

    /// Computes `inv_sqr_null_point` unless already done (C `theta_precomputation`).
    pub fn precompute(&mut self) {
        if self.precomputation {
            return;
        }
        self.inv_sqr_null_point = self.dual_null_point().to_squared().invert();
        self.precomputation = true;
    }

    /// `[2]P` (C `theta_DBL`).
    pub fn dbl(&mut self, p: &ThetaPoint<P>) -> ThetaPoint<P> {
        self.precompute();
        p.to_squared()
            .square()
            .pmul(&self.inv_sqr_null_point)
            .hadamard()
            .pmul(&self.inv_dual_null_point)
    }

    /// `[2^n]P` (C `theta_DBL_iter`).
    pub fn dbl_iter(&mut self, p: &ThetaPoint<P>, n: usize) -> ThetaPoint<P> {
        (0..n).fold(*p, |q, _| self.dbl(&q))
    }
}
