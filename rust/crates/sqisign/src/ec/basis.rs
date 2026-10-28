//! Deterministic bases of `E[2^f]` and their compact hints (vendor `basis.c`).

use super::params::{cofactor, cofactor_bits, EcParams};
use super::{Basis, Curve, Point};
use crate::gf::{Fp, Fp2, FpParams};

/// The complex conjugate (inline in C as `fp_copy` + `fp_neg`).
fn conj<P: FpParams>(x: &Fp2<P>) -> Fp2<P> {
    Fp2 {
        re: x.re,
        im: -x.im,
    }
}

impl<P: FpParams> Curve<P> {
    /// A deterministic choice of `(x(P + Q), x(P - Q))` (Proposition 3 of
    /// eprint 2017/518; C `difference_point`).
    fn difference_point(&self, p: &Point<P>, q: &Point<P>) -> (Point<P>, Point<P>) {
        let (t0, t1) = (p.x * q.x, p.z * q.z);
        let bxx = (t0 - t1).square() * self.c;
        let bxz = t0 + t1;
        let (t0, t1) = (p.x * q.z, p.z * q.x);
        let bxz = bxz * (t0 + t1);
        let bzz = (t0 - t1).square() * self.c;
        let bxz = bxz * self.c;
        let t = t0 * t1 * self.a;
        let bxz = bxz + t + t;
        // Scale so that the denominator is a fourth power in GF(p).
        let s = conj(&self.c) * self.c * conj(&bzz);
        let (bxx, bxz, bzz) = (bxx * s, bxz * s, bzz * s);
        // The discriminant is a square for entangled bases.
        let r = (bxz.square() - bxx * bzz).sqrt();
        (Point::new(bxz + r, bzz), Point::new(bxz - r, bzz))
    }

    /// Whether `x` is the abscissa of a point of `(A : 1)` (C `is_on_curve`).
    fn is_on_curve(&self, x: &Fp2<P>) -> u32 {
        (((*x + self.a) * *x).add_one() * *x).is_square()
    }

    /// Clears the odd cofactor and `2^(T - f)` (C
    /// `clear_cofactor_for_maximal_even_order`).
    fn clear_cofactor(&mut self, p: &Point<P>, f: usize) -> Point<P> {
        let mut p = self.mul(&cofactor::<P>(), cofactor_bits::<P>(), p);
        for _ in f..P::T as usize {
            p = p.xdbl_a24(&self.a24, self.a24_normalized);
        }
        p
    }

    /// `P = (-A : 1 + ib)` for the first `b >= start` with `1 + b^2` and
    /// `A^2 ib - (1 + ib)^2` non-squares, and its 7-bit hint (C `find_nqr_factor`).
    fn find_nqr_factor(&self, start: u8) -> (Point<P>, u8) {
        let mut n = u16::from(start);
        let a2 = self.a.square();
        let z = loop {
            loop {
                let t = u32::from(n) * u32::from(n) + 1;
                n = n.wrapping_add(1);
                if Fp::<P>::from_small(t).is_square() == 0 {
                    break;
                }
            }
            let b = Fp::from_small(u32::from(n).wrapping_sub(1));
            let ib = Fp2 {
                re: Fp::ZERO,
                im: b,
            };
            let z = Fp2 { re: Fp::ONE, im: b };
            if (ib * a2 - z.square()).is_square() == 0 {
                break z;
            }
        };
        let hint = if n <= 128 { n.wrapping_sub(1) as u8 } else { 0 };
        (Point::new(-self.a, z), hint)
    }

    /// `P` from [`Self::find_nqr_factor`] and `Q = (-(xP + A zP) : zP)`
    /// (C `PQ_from_nqr`).
    fn pq_from_nqr(&self, start: u8) -> (Point<P>, Point<P>, u8) {
        let (p, hint) = self.find_nqr_factor(start);
        let q = Point::new(-(self.a * p.z + p.x), p.z);
        (p, q, hint)
    }

    /// The first `x = n A` with `n >= start` on the curve, and its 7-bit
    /// hint (C `find_nA_x_coord`).
    fn find_na_x(&self, start: u8) -> (Fp2<P>, u8) {
        let mut n = start;
        let mut x = if n == 1 {
            self.a
        } else {
            self.a.mul_small(u32::from(n))
        };
        while self.is_on_curve(&x) == 0 {
            x = x + self.a;
            n = n.wrapping_add(1);
        }
        (x, if n < 128 { n } else { 0 })
    }

    /// `P = (n A : 1)` and `Q = (-(A + xP) : 1)` (C `PQ_from_qr`).
    fn pq_from_qr(&self, start: u8) -> (Point<P>, Point<P>, u8) {
        let (x, hint) = self.find_na_x(start);
        let p = Point::new(x, Fp2::ONE);
        (p, Point::new(-(self.a + x), Fp2::ONE), hint)
    }

    /// The precomputed `E0[2^f]` basis (C `ec_basis_E0_2f`).
    fn basis_e0_2f(&self, f: usize) -> Basis<P>
    where
        P: EcParams,
    {
        let mut p = Point::new(P::BASIS_E0_PX, Fp2::ONE);
        let mut q = Point::new(P::BASIS_E0_QX, Fp2::ONE);
        for _ in f..P::T as usize {
            p = p.xdbl_e0();
            q = q.xdbl_e0();
        }
        let (_, pmq) = self.difference_point(&p, &q);
        Basis { p, q, pmq }
    }

    /// `P`, `Q` with `P` above the cofactor-cleared candidate and `Q` the
    /// chosen difference, and `P + Q` (shared tail of both basis functions).
    fn entangled_basis(&mut self, p: &Point<P>, q: &Point<P>, f: usize) -> (Basis<P>, Point<P>) {
        let p = self.clear_cofactor(p, f);
        let q = self.clear_cofactor(q, f);
        // Q and P - Q swap roles so that Q lies above (0 : 0).
        let (ppq, pmq) = self.difference_point(&p, &q);
        (Basis { p, q: pmq, pmq: q }, ppq)
    }

    /// A basis of `E[2^f]` with `Q` above `(0 : 0)`, and the hint that lets
    /// [`Self::basis_2f_from_hint`] recompute it. When `e > 0`, `P - Q` is
    /// chosen so that the basis of `E[2^e]` recomputed from the hint
    /// matches (C `ec_curve_to_basis_2f_to_hint`). Normalises the curve.
    ///
    /// # Panics
    /// If `0 < e` and `f <= e`.
    pub fn basis_2f_to_hint(&mut self, f: usize, e: usize) -> (Basis<P>, u8)
    where
        P: EcParams,
    {
        self.normalize_with_a24();
        if self.a.is_zero() != 0 {
            return (self.basis_e0_2f(f), 0);
        }
        let hint_a = self.a.is_square() != 0;
        let (p, q, hint) = if hint_a {
            self.pq_from_nqr(1)
        } else {
            self.pq_from_qr(1)
        };
        let (mut b, ppq) = self.entangled_basis(&p, &q, f);
        if e > 0 {
            assert!(e < f, "e must be below f");
            let q = self.dbl_iter(&b.q, f - e);
            let q2 = self.dbl_iter(&ppq, f - e);
            let t0 = q.x * q2.z - q2.x * q.z;
            let t1 = q.z * q2.z;
            let t0 = t0 * (t1 * conj(&t1).square());
            let t1 = t0.square().sqrt();
            b.q.x = Fp2::select(&b.q.x, &ppq.x, t0.ct_eq(&t1));
        }
        (b, (hint << 1) | u8::from(hint_a))
    }

    /// The basis of `E[2^f]` described by `hint` (C
    /// `ec_curve_to_basis_2f_from_hint` as built with `NDEBUG`, which never
    /// fails). Normalises the curve.
    pub fn basis_2f_from_hint(&mut self, f: usize, hint: u8) -> Basis<P>
    where
        P: EcParams,
    {
        self.normalize_with_a24();
        if self.a.is_zero() != 0 {
            return self.basis_e0_2f(f);
        }
        let (hint_a, hint_p) = (hint & 1 == 1, hint >> 1);
        let (p, q) = match (hint_p, hint_a) {
            // No point within 128 tries: search on, skipping the known failures.
            (0, false) => {
                let (p, q, _) = self.pq_from_qr(128);
                (p, q)
            }
            (0, true) => {
                let (p, q, _) = self.pq_from_nqr(128);
                (p, q)
            }
            (n, false) => {
                let x = self.a.mul_small(u32::from(n));
                (Point::new(x, Fp2::ONE), Point::new(-(self.a + x), Fp2::ONE))
            }
            (n, true) => {
                let z = Fp2 {
                    re: Fp::ONE,
                    im: Fp::from_small(u32::from(n)),
                };
                let x = -self.a;
                (Point::new(x, z), Point::new(-(self.a * z + x), z))
            }
        };
        self.entangled_basis(&p, &q, f).0
    }
}

#[cfg(test)]
mod tests {
    use crate::ec::Curve;
    use crate::gf::{Fp, Fp2, FpParams, P324, P500, P664};

    /// Whether a curve accepts `b = 127` as the first candidate from 127,
    /// which must give the largest 7-bit hint (C `n <= 128`).
    fn hint_at_boundary<P: FpParams>() -> bool {
        (2..64).any(|k| {
            let e = Curve::<P> {
                a: Fp2 {
                    re: Fp::from_small(k),
                    im: Fp::ONE,
                },
                ..Curve::default()
            };
            let (p, hint) = e.find_nqr_factor(127);
            let hit = p.z.im.ct_eq(&Fp::from_small(127)) != 0;
            assert_eq!(hit.then_some(hint), hit.then_some(127), "hint for b = 127");
            hit
        })
    }

    /// Whether a curve rejects `127 A` and accepts `128 A`, which no longer
    /// fits the 7-bit hint (C `n < 128`).
    fn na_hint_overflows<P: FpParams>() -> bool {
        (2..64).any(|k| {
            let a = Fp2 {
                re: Fp::from_small(k),
                im: Fp::ONE,
            };
            let e = Curve::<P> {
                a,
                ..Curve::default()
            };
            let (x, hint) = e.find_na_x(127);
            let hit = x.ct_eq(&a.mul_small(128)) != 0;
            assert_eq!(hit.then_some(hint), hit.then_some(0), "hint for n = 128");
            hit
        })
    }

    #[test]
    fn overflowing_na_hint_is_dropped() {
        let hits = [
            na_hint_overflows::<P324>(),
            na_hint_overflows::<P500>(),
            na_hint_overflows::<P664>(),
        ];
        assert!(hits.contains(&true), "no level reaches n = 128");
    }

    #[test]
    fn largest_nqr_hint_is_kept() {
        let hits = [
            hint_at_boundary::<P324>(),
            hint_at_boundary::<P500>(),
            hint_at_boundary::<P664>(),
        ];
        assert!(hits.contains(&true), "no level reaches b = 127");
    }
}
