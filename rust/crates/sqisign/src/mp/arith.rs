//! Arithmetic (vendor `mp.c`: `ibz_add`, `ibz_sub`, `ibz_mul`, negation,
//! power-of-two shifts and the bounded helpers).

use core::ops::{Add, Mul, Neg, Sub};

use super::{nlimbs, Ibz, MpParams};

impl<P: MpParams> Ibz<P> {
    /// `a + b` or `a - b` over `max(bounds) + 1` bits, limited to the
    /// capacity (C `ibz_add`, `ibz_sub`).
    fn add_sub(a: &Self, b: &Self, sub: bool) -> Self {
        let (la, lb) = (nlimbs(a.bitlen), nlimbs(b.bitlen));
        let (a_hi, b_hi) = (a.sign_mask(), b.sign_mask());
        let mut r = Self::ZERO;
        r.bitlen = (a.bitlen.max(b.bitlen) + 1).min(P::IBZ_MAX_BITS);
        let mut carry = false;
        for i in 0..nlimbs(r.bitlen) {
            let x = if i < la { a.limbs[i] } else { a_hi };
            let y = if i < lb { b.limbs[i] } else { b_hi };
            (r.limbs[i], carry) = if sub {
                x.borrowing_sub(y, carry)
            } else {
                x.carrying_add(y, carry)
            };
        }
        r
    }

    /// `a` if `mask` is 0, `-a` if it is all ones, over the bound of `a` (C
    /// `ibz_cneg`). Constant time.
    pub fn cneg(&self, mask: u64) -> Self {
        let mut r = *self;
        let mut carry = u128::from(mask & 1);
        for (o, &w) in r
            .limbs
            .as_mut()
            .iter_mut()
            .zip(self.limbs.as_ref())
            .take(nlimbs(self.bitlen))
        {
            let t = u128::from(w ^ mask) + carry;
            *o = t as u64;
            carry = t >> 64;
        }
        r
    }

    /// `|self|` (C `ibz_abs`).
    pub fn abs(&self) -> Self {
        self.cneg(self.sign_mask())
    }

    /// `self * 2^exp`, the bound widened by `exp` up to the capacity (C
    /// `ibz_mul_2exp`).
    pub fn mul_2exp(&self, exp: usize) -> Self {
        let mut r = *self;
        if exp == 0 {
            return r;
        }
        let exp = exp.min(P::IBZ_MAX_BITS);
        r.set_bound(self.bitlen + exp);
        let n = nlimbs(r.bitlen);
        let (words, bits) = (exp / 64, exp % 64);
        for i in (0..n).rev() {
            let lo = if i >= words { r.limbs[i - words] } else { 0 };
            let hi = if i > words { r.limbs[i - words - 1] } else { 0 };
            r.limbs[i] = if bits == 0 {
                lo
            } else {
                (lo << bits) | (hi >> (64 - bits))
            };
        }
        r
    }

    /// The significant limbs of `self` shifted right by `exp` bits, filling
    /// with zeros, and the bound narrowed by `exp` (C `ibz_div_2exp`; this is
    /// `floor(self / 2^exp)` for non-negative `self` only).
    pub fn div_2exp(&self, exp: usize) -> Self {
        let mut r = *self;
        if exp == 0 {
            return r;
        }
        let exp = exp.min(P::IBZ_MAX_BITS);
        let n = nlimbs(self.bitlen);
        let (words, bits) = (exp / 64, exp % 64);
        for i in 0..n {
            let lo = if i + words < n { r.limbs[i + words] } else { 0 };
            let hi = if i + words + 1 < n {
                r.limbs[i + words + 1]
            } else {
                0
            };
            r.limbs[i] = if bits == 0 {
                lo
            } else {
                (lo >> bits) | (hi << (64 - bits))
            };
        }
        r.bitlen = if exp < self.bitlen {
            self.bitlen - exp
        } else {
            1
        };
        r
    }

    /// `self * b`, computed over `nlimbs(bound)` limbs and set to the bound
    /// `bound` (C `ibz_mul_by_int_and_set_bound`). Constant time in the
    /// values.
    pub fn mul_i32_bounded(&self, b: i32, bound: usize) -> Self {
        let b_sign = 0u64.wrapping_sub(u64::from(b < 0));
        let b_abs = ((i64::from(b) as u64) ^ b_sign).wrapping_sub(b_sign);
        let la = nlimbs(self.bitlen);
        let a_sign = self.sign_mask();
        let mut r = self.cneg(a_sign);
        let n = nlimbs(bound);
        let mut carry = 0u128;
        for i in 0..n {
            let a = if i < la { r.limbs[i] } else { 0 };
            let t = u128::from(a) * u128::from(b_abs) + carry;
            r.limbs[i] = t as u64;
            carry = t >> 64;
        }
        r.bitlen = 64 * n;
        let mut r = r.cneg(a_sign ^ b_sign);
        r.set_bound(bound);
        r
    }

    /// `a + b` set to the bound `bound` (C `ibz_add_and_set_bound`).
    pub fn add_bounded(&self, b: &Self, bound: usize) -> Self {
        let mut r = *self + *b;
        r.set_bound(bound);
        r
    }

    /// `a + b` set to the bound `bound` (C `ibz_add_int_and_set_bound`).
    pub fn add_i32_bounded(&self, b: i32, bound: usize) -> Self {
        self.add_bounded(&Self::from_i64(b.into(), 32), bound)
    }
}

impl<P: MpParams> Add for Ibz<P> {
    type Output = Self;

    /// C `ibz_add`.
    fn add(self, b: Self) -> Self {
        Self::add_sub(&self, &b, false)
    }
}

impl<P: MpParams> Sub for Ibz<P> {
    type Output = Self;

    /// C `ibz_sub`.
    fn sub(self, b: Self) -> Self {
        Self::add_sub(&self, &b, true)
    }
}

impl<P: MpParams> Neg for Ibz<P> {
    type Output = Self;

    /// Two's-complement negation over the bound (C `ibz_neg`).
    fn neg(self) -> Self {
        self.cneg(u64::MAX)
    }
}

impl<P: MpParams> Mul for Ibz<P> {
    type Output = Self;

    /// The product over `a.bound() + b.bound() - 1` bits, limited to the
    /// capacity (C `ibz_mul`).
    fn mul(self, b: Self) -> Self {
        let (a_neg, b_neg) = (self.sign_mask(), b.sign_mask());
        let (m1, m2) = (self.cneg(a_neg), b.cneg(b_neg));
        let mut r = Self::ZERO;
        r.bitlen = (self.bitlen + b.bitlen - 1).min(P::IBZ_MAX_BITS);
        let (n1, n2) = (nlimbs(m1.bitlen), nlimbs(m2.bitlen));
        for i in 0..n1 {
            let mut carry = 0u64;
            for j in 0..n2.min(P::IBZ_LIMBS - i) {
                let t = u128::from(m1.limbs[i]) * u128::from(m2.limbs[j])
                    + u128::from(r.limbs[i + j])
                    + u128::from(carry);
                r.limbs[i + j] = t as u64;
                carry = (t >> 64) as u64;
            }
            if i + n2 < P::IBZ_LIMBS {
                r.limbs[i + n2] = carry;
            }
        }
        r.cneg(a_neg ^ b_neg)
    }
}
