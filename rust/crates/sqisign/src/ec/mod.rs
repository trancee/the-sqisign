//! Elliptic curves in Montgomery form over GF(p^2) (vendor `ec`).
//!
//! Points are x-only projective `(X : Z)`; curves are `y^2 = x^3 + (A/C) x^2 + x`.

mod biscalar;
mod curve;
mod point;
mod scalar;

use crate::gf::{Fp2, FpParams};

/// A projective x-only point `(X : Z)`; `(1 : 0)` is the identity.
pub struct Point<P: FpParams> {
    /// `X`.
    pub x: Fp2<P>,
    /// `Z`.
    pub z: Fp2<P>,
}

/// A Montgomery curve `(A : C)` with its cached `A24 = (A + 2C : 4C)`.
pub struct Curve<P: FpParams> {
    /// `A`.
    pub a: Fp2<P>,
    /// `C` (non-zero).
    pub c: Fp2<P>,
    /// `(A + 2C : 4C)`, meaningful when `a24_normalized` holds.
    pub a24: Point<P>,
    /// Whether `a24` has been computed and normalised to `((A + 2C) / 4C : 1)`.
    pub a24_normalized: bool,
}

/// A torsion basis `P`, `Q` together with `P - Q`.
pub struct Basis<P: FpParams> {
    /// `P`.
    pub p: Point<P>,
    /// `Q`.
    pub q: Point<P>,
    /// `P - Q`.
    pub pmq: Point<P>,
}

/// Barycentric coordinates: `P + Q = (u - v : w)`, `P - Q = (u + v : w)`.
pub struct BaryCoords<P: FpParams> {
    /// `u`.
    pub u: Fp2<P>,
    /// `v`.
    pub v: Fp2<P>,
    /// `w`.
    pub w: Fp2<P>,
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
copy_clone!(Point, Curve, Basis, BaryCoords);

/// All-ones `u32` mask when `bit == 1`, zero when `bit == 0`.
fn mask(bit: u64) -> u32 {
    0u32.wrapping_sub(bit as u32)
}

#[cfg(test)]
mod ec_tests;
