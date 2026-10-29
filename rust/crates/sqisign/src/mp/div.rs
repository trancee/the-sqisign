//! Division and square roots (vendor `mp.c`: `ibz_div`, `ibz_mod`,
//! `ibz_mod2exp`, `ibz_divides`, `ibz_sqrt_floor`).
//!
//! C divides with Burnikel–Ziegler or schoolbook depending on the operand
//! sizes, and takes square roots by a reciprocal Newton iteration; both
//! produce fully determined results, which Rust computes with Knuth's
//! algorithm D and an integer Newton iteration. Variable time, like C.

use super::arith::cneg_limbs;
use super::{nlimbs, Ibz, Limbs, MpParams, MAX_IBZ_LIMBS};

/// Unsigned scratch words, one more than the widest integer.
pub(super) const W: usize = MAX_IBZ_LIMBS + 1;
pub(super) type Words = [u64; W];

/// The number of significant words of `x`, at least 1.
pub(super) fn len(x: &[u64]) -> usize {
    x.iter().rposition(|&w| w != 0).map_or(1, |i| i + 1)
}

/// `x << s` over `n` words, `s < 64`, into the first `n + 1` words of `r`.
fn shl(r: &mut Words, x: &[u64], n: usize, s: u32) {
    let mut carry = 0;
    for (o, &w) in r.iter_mut().zip(&x[..n]) {
        *o = (w << s) | carry;
        carry = if s == 0 { 0 } else { w >> (64 - s) };
    }
    r[n] = carry;
}

/// Unsigned `u / v` with `v != 0` (Knuth, TAOCP 4.3.1, algorithm D):
/// quotient and remainder. Writes them into `q` and `r`, which must be zero,
/// rather than returning them, to spare copies of the full-width words.
pub(super) fn divrem(u: &[u64], v: &[u64], q: &mut [u64], r: &mut [u64]) {
    let (m, n) = (len(u), len(v));
    if m < n {
        r[..m].copy_from_slice(&u[..m]);
        return;
    }
    if n == 1 {
        let d = u128::from(v[0]);
        let mut rem = 0u128;
        for i in (0..m).rev() {
            let num = (rem << 64) | u128::from(u[i]);
            let qi = num / d;
            q[i] = qi as u64;
            rem = num - qi * d;
        }
        r[0] = rem as u64;
        return;
    }
    let s = v[n - 1].leading_zeros();
    let (mut vn, mut un) = ([0; W], [0; W]);
    shl(&mut vn, v, n, s);
    shl(&mut un, u, m, s);
    let (top, next) = (u128::from(vn[n - 1]), u128::from(vn[n - 2]));
    for j in (0..=m - n).rev() {
        let num = (u128::from(un[j + n]) << 64) | u128::from(un[j + n - 1]);
        let mut qhat = num / top;
        let mut rhat = num - qhat * top;
        while rhat >> 64 == 0
            && (qhat >> 64 != 0 || qhat * next > (rhat << 64) | u128::from(un[j + n - 2]))
        {
            qhat -= 1;
            rhat += top;
        }
        let (mut carry, mut borrow) = (0u128, false);
        for i in 0..n {
            let p = qhat * u128::from(vn[i]) + carry;
            carry = p >> 64;
            (un[i + j], borrow) = un[i + j].borrowing_sub(p as u64, borrow);
        }
        (un[j + n], borrow) = un[j + n].borrowing_sub(carry as u64, borrow);
        if borrow {
            // The estimate was one too large (probability about 2^-64).
            qhat -= 1;
            let mut c = false;
            for i in 0..n {
                (un[i + j], c) = un[i + j].carrying_add(vn[i], c);
            }
            un[j + n] = un[j + n].wrapping_add(u64::from(c));
        }
        q[j] = qhat as u64;
    }
    for i in 0..n {
        r[i] = if s == 0 {
            un[i]
        } else {
            (un[i] >> s) | (un[i + 1] << (64 - s))
        };
    }
}

impl<P: MpParams> Ibz<P> {
    /// `|self|` as unsigned words.
    pub(super) fn magnitude(&self) -> Words {
        let mut r = [0; W];
        let n = nlimbs(self.bitlen);
        r[..n].copy_from_slice(&self.limbs[..n]);
        cneg_limbs(&mut r[..n], self.sign_mask());
        r
    }

    /// `|self|` in its significant limbs, and their number.
    fn abs_words(&self) -> (P::IbzLimbs, usize) {
        let mut r = self.limbs;
        let n = nlimbs(self.bitlen);
        cneg_limbs(&mut r[..n], self.sign_mask());
        (r, n)
    }

    /// `|q|` or `|r|` with the bound `bitlen`, negated if `neg`.
    pub(super) fn signed(w: &[u64], bitlen: usize, neg: bool) -> Self {
        let mut r = Self::zero(bitlen);
        let n = nlimbs(bitlen);
        r.limbs[..n].copy_from_slice(&w[..n]);
        r.cneg_assign(0u64.wrapping_sub(u64::from(neg)));
        r
    }

    /// The quotient rounded towards zero and the remainder, of the signs of
    /// `self / b` and `self`, with the bounds of `self` and `b` (C
    /// `ibz_div`; the quotient wraps for `-2^(bound-1) / -1`).
    ///
    /// # Panics
    ///
    /// If `b` is zero.
    pub fn div_rem(&self, b: &Self) -> (Self, Self) {
        // Both fit the capacity: `|q| <= |self|` and `|r| < |b|`.
        let (mut q, mut r) = (P::IbzLimbs::zeroed(), P::IbzLimbs::zeroed());
        let ((a, m), (v, n)) = (self.abs_words(), b.abs_words());
        divrem(&a[..m], &v[..n], q.as_mut(), r.as_mut());
        let (a_neg, b_neg) = (!self.is_positive(), !b.is_positive());
        (
            Self::signed(q.as_ref(), self.bitlen, a_neg != b_neg),
            Self::signed(r.as_ref(), b.bitlen, a_neg),
        )
    }

    /// `self mod b` in `[0, |b|)` (C `ibz_mod`). The bound is that of `b`,
    /// plus one if `self` is negative and not a multiple of `b`.
    ///
    /// # Panics
    ///
    /// If `b` is zero.
    pub fn modulo(&self, b: &Self) -> Self {
        let r = self.div_rem(b).1;
        match (r.is_positive(), b.is_positive()) {
            (true, _) => r,
            (false, true) => r + *b,
            (false, false) => r - *b,
        }
    }

    /// `self mod 2^exp` in `[0, 2^exp)`, with the bound `exp + 1` (C
    /// `ibz_mod2exp`).
    ///
    /// # Panics
    ///
    /// If `exp` is not below the capacity.
    pub fn mod_2exp(&self, exp: usize) -> Self {
        assert!(exp < P::IBZ_MAX_BITS, "exponent beyond the capacity");
        let mut r = *self;
        r.set_bound(exp + 1);
        let (q, rem) = (exp / 64, exp % 64);
        r.limbs[q] &= (1 << rem) - 1;
        r
    }

    /// Whether `b` divides `self` (C `ibz_divides`).
    ///
    /// # Panics
    ///
    /// If `b` is zero.
    pub fn divides(&self, b: &Self) -> bool {
        self.div_rem(b).1.is_zero()
    }

    /// `floor(sqrt(self))` for non-negative `self`, with the bound
    /// `ceil(bitsize / 2) + 1` (C `ibz_sqrt_floor`).
    pub fn sqrt_floor(&self) -> Self {
        let bits = self.bitsize();
        if bits == 0 {
            return Self::ZERO;
        }
        let a = self.magnitude();
        let half = bits.div_ceil(2);
        let mut x = [0; W];
        x[half / 64] = 1 << (half % 64);
        // Newton from above: x >= sqrt(a) decreases strictly to the floor.
        loop {
            let (mut q, mut r) = ([0; W], [0; W]);
            divrem(&a, &x, &mut q, &mut r);
            let (mut y, mut c) = ([0; W], false);
            for i in 0..W {
                (y[i], c) = x[i].carrying_add(q[i], c);
            }
            let y = shr1(&y);
            if y[..].iter().rev().cmp(x[..].iter().rev()).is_ge() {
                return Self::from_bits(&x, half);
            }
            x = y;
        }
    }
}

/// `x >> 1`.
fn shr1(x: &Words) -> Words {
    let mut r = [0; W];
    for i in 0..W {
        r[i] = (x[i] >> 1) | x.get(i + 1).map_or(0, |&h| h << 63);
    }
    r
}

#[cfg(test)]
mod tests {
    use super::{divrem, len, W};

    fn words(x: &[u64]) -> [u64; W] {
        let mut r = [0; W];
        r[..x.len()].copy_from_slice(x);
        r
    }

    /// The estimated quotient digit is one too large and the remainder must
    /// be added back (the 64-bit form of a Hacker's Delight vector).
    #[test]
    fn divrem_adds_back() {
        let (mut q, mut r) = ([0; W], [0; W]);
        divrem(
            &words(&[3, 0, 1 << 63]),
            &words(&[1, 0, 1 << 61]),
            &mut q,
            &mut r,
        );
        assert_eq!(q, words(&[3]));
        assert_eq!(r, words(&[0, 0, 1 << 61]));
    }

    /// The first estimate from the top two words is two too large, which only
    /// the `rhat` test removes: the add-back corrects a single unit.
    #[test]
    fn divrem_corrects_an_estimate_two_too_large() {
        let (mut q, mut r) = ([0; W], [0; W]);
        divrem(
            &words(&[0xd5f4_b3b2_e4b0_6ce6, u64::MAX, 0x9027_c4d1_c386_bbc2]),
            &words(&[0xffff_ffff_ffff_fffe, 0x9027_c4d1_c386_bbc4]),
            &mut q,
            &mut r,
        );
        assert_eq!(q, words(&[0xffff_ffff_ffff_fffc]));
        assert_eq!(r, words(&[0xd5f4_b3b2_e4b0_6cde, 0x409f_1347_0e1a_ef15]));
    }

    #[test]
    fn len_counts_significant_words() {
        assert_eq!([len(&[0, 0]), len(&[1, 0]), len(&[0, 1])], [1, 1, 2]);
    }
}
