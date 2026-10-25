//! Primality and modular square roots (vendor `mp.c`: `ibz_probab_prime`,
//! `ibz_sqrt_m1_mod_verified`, `ibz_sqrt_mod_p`, and `primality_test.h`).
//!
//! The tests and roots are fully determined by the inputs and the bytes drawn
//! from the PRNG, so Rust follows the C sequence of steps and draws while
//! computing each residue with [`Monty`] instead of the C 61-bit radix.
//! Variable time, like C.

use super::div::{len, W};
use super::monty::Monty;
use super::{Ibz, MpParams};
use crate::prng::Domain;

/// The odd primes up to the C sieve bound, 509 at every level (C
/// `qlapoty_aggressive_primes`; `MODQBITS >= 1800` selects 509).
const SIEVE: [u32; 96] = [
    3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97,
    101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193,
    197, 199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307,
    311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421,
    431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509,
];

/// Small bases for a quadratic non-residue (C `qlapoty_jacobi_bases`).
const JACOBI_BASES: [u32; 34] = [
    2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97,
    101, 103, 107, 109, 113, 127, 131, 137, 139,
];

/// Witnesses drawn beyond this many requested rounds (C BPSW step 7).
const BPSW_ROUNDS: u32 = 24;

/// The Jacobi symbol `(a / n)` for odd one-word `n` (C
/// `probprime_jacobi_u64_internal`).
fn jacobi_u64(mut a: u64, mut n: u64) -> i32 {
    a %= n;
    let mut sign = 1;
    while a != 0 {
        while a & 1 == 0 {
            a >>= 1;
            if matches!(n & 7, 3 | 5) {
                sign = -sign;
            }
        }
        (a, n) = (n, a);
        if a & 3 == 3 && n & 3 == 3 {
            sign = -sign;
        }
        a %= n;
    }
    sign * i32::from(n == 1)
}

impl<P: MpParams> Ibz<P> {
    /// `self mod m` for a non-negative `self` and a one-word `m > 0` (C
    /// `qlapoty_mod_digit`).
    fn mod_u64(&self, m: u64) -> u64 {
        self.limbs().iter().rev().fold(0, |r, &w| {
            ((u128::from(r) << 64 | u128::from(w)) % u128::from(m)) as u64
        })
    }

    /// The first base `a` with `(a / self) = -1`, for `self = 1 mod 4` (C
    /// `qlapoty_find_jacobi_minus_one`).
    fn jacobi_minus_one_base(&self) -> Option<u32> {
        JACOBI_BASES.into_iter().find(|&a| {
            if a == 2 {
                return self.limbs[0] & 7 == 5;
            }
            let r = self.mod_u64(a.into());
            let e = (a - 1) / 2;
            r != 0 && Ibz::<P>::pow_small(r, e, a) == u64::from(a) - 1
        })
    }

    /// `b^e mod m` on words.
    fn pow_small(b: u64, e: u32, m: u32) -> u64 {
        let m = u64::from(m);
        (0..e).fold(1, |acc, _| acc * b % m)
    }

    /// A square root of -1 modulo `self = 1 mod 4`, `a^((self - 1) / 4)` for
    /// the first small `a` with `(a / self) = -1`, if it squares to -1; with
    /// the bound of `self` (C `ibz_sqrt_m1_mod_verified`).
    pub fn sqrt_m1_mod_verified(&self) -> Option<Self> {
        let a = self.jacobi_minus_one_base()?;
        let m = Monty::<P, W>::new(self);
        let x = m.pow(&m.small(a as i32), &self.div_2exp(2));
        let y = m.add(&m.sqr(&x), &m.one);
        (y == [0; W]).then(|| m.redc(&x, self.bitlen))
    }

    /// A square root of `self` modulo the prime `p`, `0 <= self < p`, with
    /// the bound of `p`, or `None` if there is none (C `ibz_sqrt_mod_p`). For
    /// `p = 1 mod 4` (Tonelli-Shanks) the root depends on a non-residue drawn
    /// uniformly from `[1, p - 1]` in `domain` (C `PRNG_default_domain`).
    pub fn sqrt_mod_p(&self, p: &Self, domain: &mut Domain) -> Option<Self> {
        match p.limbs[0] & 3 {
            3 => {
                let m = Monty::<P, W>::new(p);
                let a = m.to_mont(self);
                let r = m.pow(&a, &(*p + Self::ONE).div_2exp(2));
                (m.sqr(&r) == a).then(|| m.redc(&r, p.bitlen))
            }
            1 => self.tonelli_shanks(p, domain),
            _ => None,
        }
    }

    /// C `ibz_sqrt_mod_p_1mod4`.
    fn tonelli_shanks(&self, p: &Self, domain: &mut Domain) -> Option<Self> {
        let pm1 = *p - Self::ONE;
        let s = pm1.two_adic();
        let q = pm1.div_2exp(s);
        let z = loop {
            let z = Self::rand_interval(&Self::ONE, &pm1, domain);
            if z.legendre(p) == -1 {
                break z;
            }
        };
        let m = Monty::<P, W>::new(p);
        let a = m.to_mont(self);
        let mut c = m.pow(&m.to_mont(&z), &q);
        let mut t = m.pow(&a, &q);
        let mut r = m.pow(&a, &(q + Self::ONE).div_2exp(1));
        let mut big_m = s;
        while t != m.one {
            let mut tmp = t;
            let mut i = 0;
            while tmp != m.one {
                tmp = m.sqr(&tmp);
                i += 1;
                if i >= big_m {
                    return None;
                }
            }
            let b = (0..big_m - i - 1).fold(c, |b, _| m.sqr(&b));
            r = m.mul(&r, &b);
            c = m.sqr(&b);
            t = m.mul(&t, &c);
            big_m = i;
        }
        Some(m.redc(&r, p.bitlen))
    }

    /// Whether `self` is a probable prime (C `ibz_probab_prime`): BPSW (a
    /// sieve to 509, Miller-Rabin to base 2 and a strong Lucas-Selfridge
    /// test), then `reps - 24` Miller-Rabin rounds with witnesses drawn
    /// uniformly from `[2, self - 2]` in `domain` (C `PRNG_default_domain`),
    /// which needs `self` 65 bits narrower than the capacity (as in C).
    pub fn probab_prime(&self, reps: u32, domain: &mut Domain) -> bool {
        let mut n = *self;
        if n.is_positive() {
            n.set_bound(n.bitsize() + 1);
        }
        if n < Self::TWO {
            return false;
        }
        if n == Self::TWO || n == Self::THREE {
            return true;
        }
        if n.is_even() || !n.sieve() {
            return false;
        }
        if n.bitsize() <= 32 && n.limbs[0] <= 509 {
            return true;
        }
        // C stops when its MODQ words would not fit (`ctx_init` fails).
        let modq_limbs = (P::IBZ_MAX_BITS - 1).div_ceil(61);
        if (n.bitlen + 62) / 61 >= modq_limbs {
            return false;
        }
        // Residues exactly one word wider than `n` up to 11 words, so that
        // `Monty::mul` unrolls; larger sizes take `W` words.
        match len(&n.magnitude()) {
            ..4 => n.bpsw::<4>(reps, domain),
            4 => n.bpsw::<5>(reps, domain),
            5 => n.bpsw::<6>(reps, domain),
            6 => n.bpsw::<7>(reps, domain),
            7 => n.bpsw::<8>(reps, domain),
            8 => n.bpsw::<9>(reps, domain),
            9 => n.bpsw::<10>(reps, domain),
            10 => n.bpsw::<11>(reps, domain),
            11 => n.bpsw::<12>(reps, domain),
            _ => n.bpsw::<W>(reps, domain),
        }
    }

    /// [`Self::probab_prime`] for an odd `self > 509` passing the sieve, with
    /// residues in `L` words.
    fn bpsw<const L: usize>(&self, reps: u32, domain: &mut Domain) -> bool {
        let n = *self;
        let m = Monty::<P, L>::new(&n);
        let nm1 = n - Self::ONE;
        let s = nm1.two_adic();
        let d = nm1.div_2exp(s);
        let mr = |x: &[u64; L]| {
            let mut x = m.pow(x, &d);
            let minus_one = m.sub(&[0; L], &m.one);
            if x == m.one || x == minus_one {
                return true;
            }
            for _ in 1..s {
                x = m.sqr(&x);
                if x == minus_one {
                    return true;
                }
                if x == m.one {
                    return false;
                }
            }
            false
        };
        if !mr(&m.small(2)) || !n.strong_lucas(&m) {
            return false;
        }
        let nm2 = n - Self::TWO;
        (BPSW_ROUNDS..reps).all(|_| mr(&m.to_mont(&Self::rand_interval(&Self::TWO, &nm2, domain))))
    }

    /// Whether no sieve prime divides `self`, other than `self` itself.
    fn sieve(&self) -> bool {
        SIEVE.iter().all(|&q| {
            self.mod_u64(q.into()) != 0 || (self.bitsize() <= 32 && self.limbs[0] == q.into())
        })
    }

    /// The strong Lucas probable-prime test with Selfridge's parameters
    /// `P = 1`, `Q = (1 - D) / 4` (C `probprime_strong_lucas_internal`).
    fn strong_lucas<const L: usize>(&self, m: &Monty<P, L>) -> bool {
        let root = self.sqrt_floor();
        if root * root == *self {
            return false;
        }
        // The first `|D| = 5, 7, 9, ...` up to 1000001 whose symbol is not 1
        // decides: -1 runs the test, 0 (a shared factor) rejects, as does
        // running out of candidates.
        (5..=1_000_001u64)
            .step_by(2)
            .map(|dabs| (dabs, jacobi_u64(self.mod_u64(dabs), dabs)))
            .find(|&(_, j)| j != 1)
            .is_some_and(|(dabs, j)| j == -1 && self.lucas_selfridge(dabs, m))
    }

    /// The strong Lucas test with `P = 1`, `Q = (1 - D) / 4`, where `D = |D|`
    /// if `|D| = 1 mod 4` and `D = -|D|` otherwise.
    fn lucas_selfridge<const L: usize>(&self, dabs: u64, m: &Monty<P, L>) -> bool {
        let h = (dabs >> 2) as i32;
        let q = if dabs & 2 == 2 { h + 1 } else { -h };
        let np1 = *self + Self::ONE;
        let s = np1.two_adic();
        let d = np1.div_2exp(s);
        let (qm, m2q) = (m.small(q), m.small(-2 * q));
        let (mut u, mut v, mut qk) = (m.one, m.one, qm);
        let double = |v: &mut [u64; L], qk: &mut [u64; L]| {
            *v = m.sub(&m.sqr(v), &m.add(qk, qk));
            *qk = m.sqr(qk);
        };
        for pos in (0..d.bitsize() - 1).rev() {
            u = m.mul(&u, &v);
            double(&mut v, &mut qk);
            if (d.limbs[pos / 64] >> (pos % 64)) & 1 == 1 {
                let old = u;
                u = m.half(&m.add(&u, &v));
                v = m.add(&u, &m.mul(&old, &m2q));
                qk = m.mul(&qk, &qm);
            }
        }
        let zero = [0; L];
        if u == zero {
            return true;
        }
        for r in 0..s {
            if v == zero {
                return true;
            }
            if r + 1 < s {
                double(&mut v, &mut qk);
            }
        }
        false
    }
}

#[cfg(test)]
#[path = "prime_tests.rs"]
mod tests;
