//! Greatest common divisors and modular inverses (vendor `mp.c`: `ibz_gcd`,
//! `ibz_xgcd`, `ibz_invmod`).
//!
//! Like C, the remainder sequence is computed with Jebelean's double-digit
//! Lehmer algorithm: it takes exactly the steps of Euclid's algorithm, so the
//! cofactors are those of the classical extended Euclidean algorithm. Variable
//! time, like C.

use super::div::{divrem, len, Words, W};
use super::{Ibz, MpParams};

/// The top two words of `x` at the word positions `m - 1` and `m - 2`.
fn top(x: &Words, m: usize) -> u128 {
    let lo = if m >= 2 { u128::from(x[m - 2]) } else { 0 };
    (u128::from(x[m - 1]) << 64) | lo
}

/// The matrix `[[a, b], [c, d]]` of the Euclid steps that the double-digit
/// prefixes `x >= y > 0` determine (C `mp_lehmer_inner`), or `None` if there
/// are none.
fn lehmer(mut x: u128, mut y: u128) -> Option<[i64; 4]> {
    const BOUND: i128 = 1 << 62;
    let (mut a, mut b, mut c, mut d) = (1i128, 0i128, 0i128, 1i128);
    let mut stepped = false;
    // Every quotient is at least 1 and `|d|` grows, so a step is only
    // accepted with a non-zero remainder and `y` stays positive.
    loop {
        let q = x / y;
        // Redundant with the bound on `nd`, but keeps `w * c` in range.
        if q >> 64 != 0 {
            break;
        }
        let w = q as i128;
        let (nc, nd) = (a - w * c, b - w * d);
        // `|nc| <= |nd|`, so bounding `nd` bounds both (C checks both).
        if nd.abs() > BOUND {
            break;
        }
        let r = x % y;
        if r < nd.unsigned_abs() || y - r < (nd - d).unsigned_abs() {
            break;
        }
        (a, b, c, d) = (c, d, nc, nd);
        (x, y) = (y, r);
        stepped = true;
    }
    stepped.then(|| [a, b, c, d].map(|t| t as i64))
}

/// `a * u + b * v` for a non-negative result (C `mp_lehmer_combine`).
fn combine(a: i64, u: &Words, b: i64, v: &Words) -> Words {
    let mut r: Words = [0; W];
    for (k, x) in [(a, u), (b, v)] {
        let m = u128::from(k.unsigned_abs());
        let (mut carry, mut c) = (0u128, false);
        for i in 0..W {
            let t = u128::from(x[i]) * m + carry;
            carry = t >> 64;
            (r[i], c) = if k < 0 {
                r[i].borrowing_sub(t as u64, c)
            } else {
                r[i].carrying_add(t as u64, c)
            };
        }
    }
    r
}

/// The number of significant bits of `x`.
fn bits(x: &Words) -> usize {
    let n = len(x);
    64 * n - x[n - 1].leading_zeros() as usize
}

impl<P: MpParams> Ibz<P> {
    /// A signed word as an integer.
    fn word(k: i64) -> Self {
        Self::from_digits(&[k.unsigned_abs()], 1).cneg(0u64.wrapping_sub(u64::from(k < 0)))
    }

    /// Euclid's algorithm on `|a|` and `|b|`, tracking `K` cofactor rows that
    /// start at `ca` for `|a|` and `cb` for `|b|`: the gcd and its row.
    fn euclid<const K: usize>(
        a: &Self,
        b: &Self,
        ca: [Self; K],
        cb: [Self; K],
    ) -> (Words, [Self; K]) {
        let (x, y) = (a.magnitude(), b.magnitude());
        let (mut u, mut v, mut cu, mut cv) = if x.iter().rev().cmp(y.iter().rev()).is_ge() {
            (x, y, ca, cb)
        } else {
            (y, x, cb, ca)
        };
        loop {
            let (m, n) = (len(&u), len(&v));
            if v[n - 1] == 0 {
                return (u, cu);
            }
            let step = if m - n >= 2 {
                None
            } else {
                lehmer(top(&u, m), top(&v, m))
            };
            if let Some([a, b, c, d]) = step {
                (u, v) = (combine(a, &u, b, &v), combine(c, &u, d, &v));
                let [a, b, c, d] = [a, b, c, d].map(Self::word);
                for k in 0..K {
                    (cu[k], cv[k]) = (a * cu[k] + b * cv[k], c * cu[k] + d * cv[k]);
                }
            } else {
                let (q, r) = divrem(&u, &v);
                let q = Self::signed(&q, (bits(&q) + 1).min(P::IBZ_MAX_BITS), false);
                for k in 0..K {
                    (cu[k], cv[k]) = (cv[k], cu[k] - q * cv[k]);
                }
                (u, v) = (v, r);
            }
        }
    }

    /// The non-negative gcd, with the larger bound of the operands (C
    /// `ibz_gcd`; `gcd(0, 0) = 0`).
    pub fn gcd(&self, b: &Self) -> Self {
        if self.is_zero() && b.is_zero() {
            return Self::ZERO;
        }
        let (g, []) = Self::euclid(self, b, [], []);
        Self::signed(&g, self.bitlen.max(b.bitlen), false)
    }

    /// `(g, u, v)` with `g = gcd(self, b) = u * self + v * b`, the cofactors
    /// of the extended Euclidean algorithm with tight bounds (C `ibz_xgcd`).
    pub fn xgcd(&self, b: &Self) -> (Self, Self, Self) {
        let (g, mut c) = Self::euclid(self, b, [Self::ONE, Self::ZERO], [Self::ZERO, Self::ONE]);
        for x in &mut c {
            x.bitlen = x.bitsize() + 1;
        }
        (
            Self::signed(&g, self.bitlen.max(b.bitlen), false),
            c[0].cneg(self.sign_mask()),
            c[1].cneg(b.sign_mask()),
        )
    }

    /// `self^-1 mod m` for positive `m`, and whether it exists; if it does
    /// not, the value is `u mod m` for the cofactor `u` of `gcd(self, m)`, as
    /// in C `ibz_invmod`.
    pub fn invmod(&self, m: &Self) -> (Self, bool) {
        let (g, [cu]) = Self::euclid(self, m, [Self::ONE], [Self::ZERO]);
        (
            cu.cneg(self.sign_mask()).modulo(m),
            len(&g) == 1 && g[0] == 1,
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lehmer_needs_a_small_quotient() {
        assert_eq!(lehmer(1 << 100, 1), None);
        assert_eq!(lehmer(3 << 64, 1 << 64), None);
    }

    #[test]
    fn lehmer_bounds_cofactors_by_2_to_the_62() {
        // Consecutive Fibonacci numbers have unit quotients and Fibonacci
        // cofactors, which pass `2^62` long before the remainders run out.
        let (mut y, mut x) = (0u128, 1u128);
        while let Some(z) = x.checked_add(y) {
            (y, x) = (x, z);
        }
        let m = lehmer(x, y).unwrap();
        let top = m.iter().map(|t| t.unsigned_abs()).max().unwrap();
        assert!(top <= 1 << 62 && top > 1 << 61);
    }

    #[test]
    fn top_takes_two_words_below_m() {
        let mut x = [0; W];
        (x[0], x[1]) = (2, 3);
        assert_eq!([top(&x, 1), top(&x, 2)], [2 << 64, (3 << 64) | 2]);
    }

    #[test]
    fn bits_counts_significant_bits() {
        let mut x = [0; W];
        x[1] = 5;
        assert_eq!(bits(&x), 67);
    }
}
