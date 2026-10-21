//! (2^n, 2^n)-isogeny chains between elliptic products in the theta model
//! (vendor `hd`).
//!
//! A chain starts with a gluing `E1 x E2 -> A`, continues with generic
//! (2,2)-isogenies between abelian surfaces and ends with a splitting back to
//! an elliptic product.

mod chain;
mod gluing;
mod gluing_basis;
mod splitting;
mod start;
mod theta;

pub use gluing::{gluing_ladder, Gluing};
pub use gluing_basis::GluingMatrix;
pub use splitting::{check_valid_theta_null_point, tensor_product_to_couple_point};
pub use theta::ThetaStructure;

use crate::ec::{Basis, Curve, Point};
use crate::gf::{Fp2, FpParams};

/// A theta point `(x : y : z : t)` on an abelian surface.
pub struct ThetaPoint<P: FpParams> {
    /// `x`.
    pub x: Fp2<P>,
    /// `y`.
    pub y: Fp2<P>,
    /// `z`.
    pub z: Fp2<P>,
    /// `t`.
    pub t: Fp2<P>,
}

/// A point `(P1, P2)` on an elliptic product `E1 x E2`.
pub struct CouplePoint<P: FpParams> {
    /// The point on `E1`.
    pub p1: Point<P>,
    /// The point on `E2`.
    pub p2: Point<P>,
}

/// An elliptic product `E1 x E2`.
pub struct CoupleCurve<P: FpParams> {
    /// `E1`.
    pub e1: Curve<P>,
    /// `E2`.
    pub e2: Curve<P>,
}

/// Generators `T1`, `T2` of a chain kernel on `E1 x E2`.
pub struct KernelCouplePoints<P: FpParams> {
    /// `T1`.
    pub t1: CouplePoint<P>,
    /// `T2`.
    pub t2: CouplePoint<P>,
}

macro_rules! copy_clone {
    ($($t:ident),*) => {$(
        impl<P: FpParams> Clone for $t<P> {
            fn clone(&self) -> Self {
                *self
            }
        }
        impl<P: FpParams> Copy for $t<P> {}
    )*};
}
copy_clone!(ThetaPoint, CouplePoint, CoupleCurve, KernelCouplePoints);

impl<P: FpParams> CouplePoint<P> {
    /// `[2](P1, P2)` (C `theta_DBL_couple_point`).
    pub fn dbl(&self, e12: &CoupleCurve<P>) -> Self {
        Self {
            p1: e12.e1.dbl(&self.p1),
            p2: e12.e2.dbl(&self.p2),
        }
    }

    /// `[2^n](P1, P2)` (C `theta_DBL_couple_point_iter`).
    pub fn dbl_iter(&self, n: usize, e12: &CoupleCurve<P>) -> Self {
        (0..n).fold(*self, |q, _| q.dbl(e12))
    }
}

impl<P: FpParams> KernelCouplePoints<P> {
    /// `T1 = (B1.P, B2.P)`, `T2 = (B1.Q, B2.Q)` (C `theta_copy_bases_to_kernel`).
    pub fn from_bases(b1: &Basis<P>, b2: &Basis<P>) -> Self {
        Self {
            t1: CouplePoint { p1: b1.p, p2: b2.p },
            t2: CouplePoint { p1: b1.q, p2: b2.q },
        }
    }
}
