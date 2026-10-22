//! Quaternion algebra elements (vendor `algebra.c`).

use super::{dim4, Lattice, Vec4};
use crate::mp::{Ibz, MpParams};

/// The algebra `B_{p,∞}`, `p = 3 mod 4` (C `quat_alg_t`).
#[derive(Clone, Copy, Debug)]
pub struct Alg<P: MpParams> {
    /// The prime `p`, with `j^2 = -p`.
    pub p: Ibz<P>,
}

/// An algebra element `(c0 + c1 i + c2 j + c3 ij) / denom`, not necessarily
/// in lowest terms (C `quat_alg_elem_t`).
#[derive(Clone, Copy, Debug)]
pub struct AlgElem<P: MpParams> {
    /// The non-zero denominator.
    pub denom: Ibz<P>,
    /// The numerators in the basis `(1, i, j, ij)`.
    pub coord: Vec4<P>,
}

impl<P: MpParams> Default for AlgElem<P> {
    /// Zero over the denominator 1 (C `quat_alg_elem_init`).
    fn default() -> Self {
        Self {
            denom: Ibz::ONE,
            coord: [Ibz::ZERO; 4],
        }
    }
}

impl<P: MpParams> Alg<P> {
    /// The algebra of the SQIsign prime, with the C bound of `p` (C
    /// `QUATALG_PINFTY`).
    pub fn pinfty() -> Self {
        Self {
            p: Ibz::from_bits(P::P.as_ref(), P::BITS as usize),
        }
    }

    /// The coordinates of the product of the integral elements `a` and `b`
    /// (C `quat_alg_coord_mul`).
    pub fn coord_mul(&self, a: &Vec4<P>, b: &Vec4<P>) -> Vec4<P> {
        let [a0, a1, a2, a3] = *a;
        let [b0, b1, b2, b3] = *b;
        let z = Ibz::ZERO;
        [
            ((z - a2 * b2 - a3 * b3) * self.p) + a0 * b0 - a1 * b1,
            ((z + a2 * b3 - a3 * b2) * self.p) + a0 * b1 + a1 * b0,
            z + a0 * b2 + a2 * b0 - a1 * b3 + a3 * b1,
            z + a0 * b3 + a3 * b0 - a2 * b1 + a1 * b2,
        ]
    }

    /// `a * b` (C `quat_alg_mul`).
    pub fn mul(&self, a: &AlgElem<P>, b: &AlgElem<P>) -> AlgElem<P> {
        AlgElem {
            denom: a.denom * b.denom,
            coord: self.coord_mul(&a.coord, &b.coord),
        }
    }

    /// The reduced norm of `a` as a fraction `(num, denom)` in lowest terms,
    /// both non-negative (C `quat_alg_norm`).
    pub fn norm(&self, a: &AlgElem<P>) -> (Ibz<P>, Ibz<P>) {
        let [c0, c1, c2, c3] = a.coord;
        let norm = (c3 * c3 + c2 * c2) * self.p + c0 * c0 + c1 * c1;
        let nd = a.denom * a.denom;
        let g = norm.gcd(&nd);
        (norm.div_rem(&g).0.abs(), nd.div_rem(&g).0.abs())
    }
}

impl<P: MpParams> AlgElem<P> {
    /// `(c0 + c1 i + c2 j + c3 ij) / denom` with 32-bit bounds (C
    /// `quat_alg_elem_set`).
    pub fn from_i32(denom: i32, c: [i32; 4]) -> Self {
        Self {
            denom: Ibz::from_i64(denom.into(), 32),
            coord: dim4::vec_from(c),
        }
    }

    /// `a` and `b` over the common denominator `(a.denom / g) * (b.denom / g)
    /// * g`, `g = gcd(a.denom, b.denom)` (C `quat_alg_equal_denom`).
    pub fn equal_denom(a: &Self, b: &Self) -> (Self, Self) {
        let g = a.denom.gcd(&b.denom);
        let da = a.denom.div_rem(&g).0;
        let db = b.denom.div_rem(&g).0;
        let denom = da * db * g;
        (
            Self {
                denom,
                coord: a.coord.map(|x| x * db),
            },
            Self {
                denom,
                coord: b.coord.map(|x| x * da),
            },
        )
    }

    /// `a + b` (C `quat_alg_add`).
    pub fn add(a: &Self, b: &Self) -> Self {
        let (a, b) = Self::equal_denom(a, b);
        Self {
            denom: a.denom,
            coord: dim4::vec_add(&a.coord, &b.coord),
        }
    }

    /// `scalar * self`, not normalised (C `quat_alg_elem_scalar_mul`).
    pub fn scalar_mul(&self, scalar: &Ibz<P>) -> Self {
        Self {
            denom: self.denom,
            coord: dim4::vec_scalar_mul(scalar, &self.coord),
        }
    }

    /// The standard involution (C `quat_alg_conj`).
    pub fn conj(&self) -> Self {
        let [c0, c1, c2, c3] = self.coord;
        Self {
            denom: self.denom,
            coord: [c0, -c1, -c2, -c3],
        }
    }

    /// The coordinates of `self` in the basis of `order` divided by their
    /// gcd, and that gcd (C `quat_alg_make_primitive`).
    ///
    /// `order` must contain `self` and have an upper-triangular basis.
    pub fn make_primitive(&self, order: &Lattice<P>) -> (Vec4<P>, Ibz<P>) {
        let mut x = order.contains(self).0;
        let content = dim4::vec_content(&x);
        if !content.is_zero() {
            x = x.map(|c| c.div_rem(&content).0);
        }
        (x, content)
    }

    /// Whether `self` is zero (C `quat_alg_elem_is_zero`).
    pub fn is_zero(&self) -> bool {
        dim4::vec_is_zero(&self.coord)
    }

    /// Reduces to lowest terms with a positive denominator (C
    /// `quat_alg_normalize`).
    pub fn normalize(&mut self) {
        let g = dim4::vec_content(&self.coord).gcd(&self.denom);
        self.denom = self.denom.div_rem(&g).0;
        self.coord = dim4::vec_scalar_div(&g, &self.coord).0;
        let sign = Ibz::from_i64(if self.denom > Ibz::ZERO { 1 } else { -1 }, 2);
        self.coord = dim4::vec_scalar_mul(&sign, &self.coord);
        self.denom = sign * self.denom;
    }
}
