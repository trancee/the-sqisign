//! Two-scalar multiplication `[k] P + [l] Q` (vendor `ec.c`).

use super::scalar::Scalar;
use super::{mask, Basis, Curve, Point};
use crate::gf::FpParams;
use core::cmp::Ordering;

/// Largest supported `kbits` (`64 * NWORDS_ORDER` for p664).
const MAX_KBITS: usize = 64 * 11;

impl<P: FpParams> Curve<P> {
    /// `[k] P + [l] Q` for the low `kbits` bits of `k`, `l` and basis `b`, in
    /// constant time; `None` on degenerate inputs (C `ec_biscalar_mul`).
    ///
    /// # Panics
    /// If `k` or `l` holds fewer than `kbits` bits, or `kbits > 64 * P::ORDER_WORDS`.
    pub fn biscalar_mul(
        &self,
        k: &[u64],
        l: &[u64],
        kbits: usize,
        b: &Basis<P>,
    ) -> Option<Point<P>> {
        if b.pmq.z.is_zero() != 0 {
            return None;
        }
        if kbits == 1 {
            if self.is_two_torsion(&b.p) == 0
                || self.is_two_torsion(&b.q) == 0
                || self.is_two_torsion(&b.pmq) == 0
            {
                return None;
            }
            let (bp, bq) = (mask(k[0] & 1), mask(l[0] & 1));
            let t0 = Point::select(&Point::IDENTITY, &b.q, bq);
            let t1 = Point::select(&b.p, &b.pmq, bq);
            return Some(Point::select(&t0, &t1, bp));
        }
        assert!(
            kbits <= 64 * P::ORDER_WORDS,
            "kbits exceeds the order width"
        );
        let mut e = *self;
        if self.a.is_zero() == 0 {
            e.normalize_a24();
        }
        let (sk, sl) = (Scalar::from_bits(k, kbits), Scalar::from_bits(l, kbits));
        e.xdblmul(&b.p, &sk, &b.q, &sl, &b.pmq, kbits)
    }

    /// C `xDBLMUL`: the constant-time Montgomery biladder.
    fn xdblmul(
        &self,
        p: &Point<P>,
        k: &Scalar,
        q: &Point<P>,
        l: &Scalar,
        pq: &Point<P>,
        kbits: usize,
    ) -> Option<Point<P>> {
        if (p.has_zero_coordinate() | q.has_zero_coordinate() | pq.has_zero_coordinate()) != 0 {
            return None;
        }
        let (r, sigma0) = recode(k, l, kbits);
        let (bitk0, bitl0) = (k.bit0(), l.bit0());
        // All ones iff exactly one scalar is even.
        let mevens = mask(bitk0 ^ bitl0);

        let s0 = mask(sigma0);
        let mut rr = [
            Point::IDENTITY,
            Point::select(p, q, s0),
            Point::select(q, p, s0),
        ];
        let (mut diff1a, mut diff1b) = (rr[1], rr[2]);
        rr[2] = rr[1].xadd(&rr[2], pq);
        if rr[2].has_zero_coordinate() != 0 {
            return None;
        }
        let (mut diff2a, mut diff2b) = (rr[2], *pq);
        let a_is_zero = self.a.is_zero() != 0;

        for i in (0..kbits).rev() {
            let h = u64::from(r[2 * i] + r[2 * i + 1]);
            let t0 = Point::select(&rr[0], &rr[1], mask(h & 1));
            let t0 = Point::select(&t0, &rr[2], mask(h >> 1));
            let t0 = if a_is_zero {
                t0.xdbl_e0()
            } else {
                t0.xdbl_a24(&self.a24, true)
            };
            let m = mask(u64::from(r[2 * i + 1]));
            let t1 = Point::select(&rr[0], &rr[1], m);
            let t2 = Point::select(&rr[1], &rr[2], m);
            Point::cswap(&mut diff1a, &mut diff1b, m);
            let t1 = t1.xadd(&t2, &diff1a);
            let t2 = rr[0].xadd(&rr[2], &diff2a);
            Point::cswap(&mut diff2a, &mut diff2b, mask(h & 1));
            rr = [t0, t1, t2];
        }
        let s = Point::select(&rr[0], &rr[1], mevens);
        Some(Point::select(&s, &rr[2], mask(bitk0 & bitl0)))
    }

    /// `[k] P + [l] Q` in variable time (Algorithm 9 of eprint 2017/212);
    /// normalises `A24` unless `A = 0` (C `ec_biscalar_mul_verif`).
    ///
    /// # Panics
    /// If `k` or `l` holds fewer than `kbits` bits, or `kbits > 704`.
    pub fn biscalar_mul_verif(
        &mut self,
        k: &[u64],
        l: &[u64],
        kbits: usize,
        b: &Basis<P>,
    ) -> Option<Point<P>> {
        let zc = |x: &Point<P>| x.has_zero_coordinate() != 0;
        if zc(&b.p) || zc(&b.q) || zc(&b.pmq) {
            return None;
        }
        let (mut s0, mut s1) = (Scalar::from_bits(k, kbits), Scalar::from_bits(l, kbits));
        if s0.is_zero() && s1.is_zero() {
            return None;
        }
        let (mut x0, mut x1, mut xd) = (b.p, b.q, b.pmq);
        if self.a.is_zero() == 0 {
            self.normalize_a24();
        }
        let dbl = |x: &Point<P>| x.xdbl_a24(&self.a24, self.a24_normalized);
        while !s0.is_zero() {
            if s1.cmp(&s0) == Ordering::Less {
                core::mem::swap(&mut s0, &mut s1);
                core::mem::swap(&mut x0, &mut x1);
            }
            if s1.cmp(&s0.shl2()) == Ordering::Less {
                s1.sub_assign(&s0);
                (x0, xd) = (x1.xadd(&x0, &xd), x0);
                if zc(&x0) {
                    return None;
                }
            } else if s1.bit0() == s0.bit0() {
                s1.sub_assign(&s0);
                s1.shr1();
                x0 = x1.xadd(&x0, &xd);
                x1 = dbl(&x1);
                if zc(&x0) | zc(&x1) {
                    return None;
                }
            } else if s1.bit0() == 0 {
                s1.shr1();
                xd = x1.xadd(&xd, &x0);
                x1 = dbl(&x1);
                if zc(&xd) | zc(&x1) {
                    return None;
                }
            } else {
                s0.shr1();
                xd = x0.xadd(&xd, &x1);
                x0 = dbl(&x0);
                if zc(&xd) | zc(&x0) {
                    return None;
                }
            }
        }
        while s1.bit0() == 0 {
            s1.shr1();
            x1 = dbl(&x1);
        }
        if !s1.is_one() {
            x1 = self.mul(s1.words(), kbits, &x1);
        }
        (!zc(&x1)).then_some(x1)
    }
}

/// The joint scalar recoding of `xDBLMUL`: digit pairs `r[2i], r[2i + 1]` and
/// the final `sigma[0]`, which selects the starting points.
fn recode(k: &Scalar, l: &Scalar, kbits: usize) -> ([u8; 2 * MAX_KBITS], u64) {
    let (bitk0, bitl0) = (k.bit0(), l.bit0());
    let mut sigma = [bitk0 ^ 1, bitl0 ^ 1];
    let mevens = 0u64.wrapping_sub((sigma[0] + sigma[1]) & 1);
    sigma[0] &= mevens;
    sigma[1] = (sigma[1] & mevens) | (1 & !mevens);

    let mut kt = k.dec_mod2exp(kbits);
    let mut lt = l.dec_mod2exp(kbits);
    let mut tmp = *k;
    Scalar::cswap(&mut kt, &mut tmp, 0u64.wrapping_sub(bitk0));
    let mut tmp = *l;
    Scalar::cswap(&mut lt, &mut tmp, 0u64.wrapping_sub(bitl0));

    let mut r = [0u8; 2 * MAX_KBITS];
    let mut pre_sigma = 0;
    for i in 0..kbits {
        Scalar::cswap(&mut kt, &mut lt, 0u64.wrapping_sub(sigma[0] ^ pre_sigma));
        let (b1n, b2n) = if i == kbits - 1 {
            (0, 0)
        } else {
            let b1 = kt.bit0();
            kt.shr1();
            let b2 = lt.bit0();
            lt.shr1();
            (b1, b2)
        };
        let r0 = kt.bit0() ^ b1n;
        let r1 = lt.bit0() ^ b2n;
        r[2 * i] = r0 as u8;
        r[2 * i + 1] = r1 as u8;

        pre_sigma = sigma[0];
        let m = 0u64.wrapping_sub(r1);
        let temp = ((sigma[0] ^ sigma[1]) & m) ^ sigma[0];
        sigma[1] = ((sigma[1] ^ sigma[0]) & m) ^ sigma[1];
        sigma[0] = temp;
    }
    (r, sigma[0])
}
