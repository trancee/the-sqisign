//! Curve set-up, doubling chains and single-scalar ladders (vendor `ec.c`).

use super::{mask, Basis, Curve, Point};
use crate::gf::{Fp2, FpParams};

impl<P: FpParams> Default for Curve<P> {
    /// `E0: y^2 = x^3 + x`, `(A : C) = (0 : 1)`, `A24` not computed (C `ec_curve_init`).
    fn default() -> Self {
        Self {
            a: Fp2::ZERO,
            c: Fp2::ONE,
            a24: Point::IDENTITY,
            a24_normalized: false,
        }
    }
}

impl<P: FpParams> Curve<P> {
    /// Whether `A != ±2`, i.e. the curve is non-singular (C `ec_curve_verify_A`).
    pub fn verify_a(a: &Fp2<P>) -> bool {
        let mut t = Fp2::ONE;
        t.re = t.re + t.re;
        if a.ct_eq(&t) != 0 {
            return false;
        }
        t.re = -t.re;
        a.ct_eq(&t) == 0
    }

    /// The curve `(A : 1)`, or `None` if it is singular (C `ec_curve_init_from_A`).
    pub fn from_a(a: &Fp2<P>) -> Option<Self> {
        let e = Self {
            a: *a,
            ..Self::default()
        };
        Self::verify_a(a).then_some(e)
    }

    /// `(A : C) <- (A / C : 1)` (C `ec_normalize_curve`).
    pub fn normalize(&mut self) {
        self.a = self.a * self.c.inv();
        self.c = Fp2::ONE;
    }

    /// Computes and normalises `A24` if needed (C `ec_curve_normalize_A24`).
    pub fn normalize_a24(&mut self) {
        if !self.a24_normalized {
            let z = self.c + self.c;
            let x = self.a + z;
            self.a24 = Point::new(x, z + z).normalize();
            self.a24_normalized = true;
        }
    }

    /// Normalises `(A : C)` and then `A24` (C `ec_normalize_curve_and_A24`).
    pub fn normalize_with_a24(&mut self) {
        if self.c.is_one() == 0 {
            self.normalize();
        }
        if !self.a24_normalized {
            let x = self.a.add_one().add_one().half().half();
            self.a24 = Point::new(x, Fp2::ONE);
            self.a24_normalized = true;
        }
    }

    /// `0xFFFF_FFFF` if `p` has order exactly 2, else 0 (C `ec_is_two_torsion`).
    pub fn is_two_torsion(&self, p: &Point<P>) -> u32 {
        if p.is_zero() != 0 {
            return 0;
        }
        let t0 = (p.x + p.z).square();
        let t1 = (p.x - p.z).square();
        let t2 = (t0 - t1) * self.a;
        let t1 = (t0 + t1) * self.c;
        let t0 = t1 + t1 + t2;
        p.x.is_zero() | t0.is_zero()
    }

    /// `0xFFFF_FFFF` if `2p` has order exactly 2, else 0 (C `ec_is_four_torsion`).
    pub fn is_four_torsion(&self, p: &Point<P>) -> u32 {
        self.is_two_torsion(&p.xdbl_a24(&self.a24, self.a24_normalized))
    }

    /// `(A : C)` viewed as a point, as C `ec_xDBL` takes it.
    fn ac(&self) -> Point<P> {
        Point::new(self.a, self.c)
    }

    /// `2p` (C `ec_dbl`).
    pub fn dbl(&self, p: &Point<P>) -> Point<P> {
        if self.a24_normalized {
            p.xdbl_a24(&self.a24, true)
        } else {
            p.xdbl(&self.ac())
        }
    }

    /// `2^n p`; normalises `A24` first when `n > 50` (C `ec_dbl_iter`).
    pub fn dbl_iter(&mut self, p: &Point<P>, n: usize) -> Point<P> {
        if n > 50 {
            self.normalize_a24();
        }
        let mut r = *p;
        for _ in 0..n {
            r = self.dbl(&r);
        }
        r
    }

    /// `2^n` times every point of `b` (C `ec_dbl_iter_basis`).
    pub fn dbl_iter_basis(&mut self, b: &Basis<P>, n: usize) -> Basis<P> {
        Basis {
            p: self.dbl_iter(&b.p, n),
            q: self.dbl_iter(&b.q, n),
            pmq: self.dbl_iter(&b.pmq, n),
        }
    }

    /// `[k] p` for the low `kbits` bits of `k` (constant-time Montgomery ladder);
    /// normalises `A24` first when `kbits > 50` (C `ec_mul`).
    ///
    /// # Panics
    /// If `k` holds fewer than `kbits` bits.
    pub fn mul(&mut self, k: &[u64], kbits: usize, p: &Point<P>) -> Point<P> {
        if kbits > 50 {
            self.normalize_a24();
        }
        self.xmul(k, kbits, p)
    }

    /// C `xMUL`. Without a normalised `A24` it uses `A + 2C` as the ladder
    /// constant, exactly like the vendor code.
    fn xmul(&self, k: &[u64], kbits: usize, p: &Point<P>) -> Point<P> {
        let a24 = if self.a24_normalized {
            self.a24
        } else {
            let x = self.c + self.c;
            Point::new(x + self.a, x + x)
        };
        let (mut r0, mut r1) = (Point::IDENTITY, *p);
        let mut prevbit = 0;
        for i in (0..kbits).rev() {
            let bit = (k[i >> 6] >> (i & 63)) & 1;
            Point::cswap(&mut r0, &mut r1, mask(bit ^ prevbit));
            prevbit = bit;
            (r0, r1) = r0.xdbladd(&r1, p, &a24);
        }
        Point::cswap(&mut r0, &mut r1, mask(prevbit));
        r0
    }

    /// `p + [m] q` given `pq = p - q`, with `m` of `ORDER_WORDS` words; needs a
    /// normalised `A24` and non-zero coordinates of `pq` (C `ec_ladder3pt`).
    ///
    /// # Panics
    /// If `m` holds fewer than `P::ORDER_WORDS` words.
    pub fn ladder3pt(
        &self,
        m: &[u64],
        p: &Point<P>,
        q: &Point<P>,
        pq: &Point<P>,
    ) -> Option<Point<P>> {
        if self.a24.z.is_one() == 0 || pq.has_zero_coordinate() != 0 {
            return None;
        }
        let (mut x0, mut x1, mut x2) = (*q, *p, *pq);
        for &word in &m[..P::ORDER_WORDS] {
            for j in 0..64 {
                let ctl = mask(((word >> j) & 1) ^ 1);
                Point::cswap(&mut x1, &mut x2, ctl);
                (x0, x1) = x0.xdbladd(&x1, &x2, &self.a24);
                Point::cswap(&mut x1, &mut x2, ctl);
            }
        }
        Some(x1)
    }
}
