//! GF(p) arithmetic over saturated 64-bit limbs in Montgomery form.
//!
//! Mirrors the vendor sat64 backend: values are partially reduced to
//! `[0, 2^BITS)` (so zero has two representations, `0` and `p`) and every
//! operation is constant time in its operands. Predicates return the C
//! convention `0x0000_0000` (false) or `0xFFFF_FFFF` (true).

use super::consts::MAX_LIMBS;
use super::params::FpParams;
use core::ops::{Add, Mul, Neg, Sub};

/// An element of GF(p) for the parameter set `P`.
pub struct Fp<P: FpParams> {
    pub(super) v: P::Limbs,
}

impl<P: FpParams> Clone for Fp<P> {
    fn clone(&self) -> Self {
        *self
    }
}

impl<P: FpParams> Copy for Fp<P> {}

/// Expands a C-style `u32` mask to 64 bits.
pub(super) fn mask64(ctl: u32) -> u64 {
    ctl as i32 as i64 as u64
}

/// `0xFFFF_FFFF` if `v == other`, else `0`, in constant time.
pub(super) fn limbs_eq(v: &[u64], other: &[u64]) -> u32 {
    let r = v.iter().zip(other).fold(0, |acc, (a, b)| acc | (a ^ b));
    (((r | r.wrapping_neg()) >> 63) as u32).wrapping_sub(1)
}

/// `d += mask & m` over `n` limbs (wrapping), returning nothing.
fn add_masked(d: &mut [u64], m: &[u64], mask: u64) {
    let mut carry = 0u128;
    for (x, y) in d.iter_mut().zip(m) {
        let t = u128::from(*x) + u128::from(y & mask) + carry;
        *x = t as u64;
        carry = t >> 64;
    }
}

/// `d -= mask & m` over `n` limbs (wrapping), returning the final borrow.
pub(super) fn sub_masked(d: &mut [u64], m: &[u64], mask: u64) -> u64 {
    let mut borrow = 0u64;
    for (x, y) in d.iter_mut().zip(m) {
        let t = u128::from(*x)
            .wrapping_sub(u128::from(y & mask))
            .wrapping_sub(u128::from(borrow));
        *x = t as u64;
        borrow = ((t >> 64) as u64) & 1;
    }
    borrow
}

impl<P: FpParams> Fp<P> {
    /// Zero.
    pub const ZERO: Self = Self { v: P::ZERO };
    /// One.
    pub const ONE: Self = Self { v: P::ONE };
    /// Length of the canonical encoding.
    pub const ENCODED_BYTES: usize = P::ENCODED_BYTES;

    pub(super) fn limbs(&self) -> &[u64] {
        self.v.as_ref()
    }

    /// The element with the given Montgomery limbs (vendor sat64 precomputed tables).
    pub(crate) const fn from_montgomery(v: P::Limbs) -> Self {
        Self { v }
    }

    pub(super) fn from_raw(raw: &[u64]) -> Self {
        let mut r = Self::ZERO;
        r.v.as_mut().copy_from_slice(&raw[..P::N]);
        r
    }

    /// Montgomery product `a * b / R`; inputs and output in `[0, 2^BITS)`.
    ///
    /// `p = C * 2^T - 1` gives `-1/p = 1 (mod 2^64)`, so each step uses the
    /// low limb as the quotient digit and adds it times `p + 1` (one limb).
    fn mont_mul(a: &[u64], b: &[u64]) -> Self {
        let n = P::N;
        let k = (P::T / 64) as usize;
        let pp1 = P::C << (P::T % 64);
        let mut z = [0u64; MAX_LIMBS + 2];
        for &bi in b {
            let mut carry = 0u128;
            for (zj, &aj) in z.iter_mut().zip(a) {
                let t = u128::from(*zj) + u128::from(aj) * u128::from(bi) + carry;
                *zj = t as u64;
                carry = t >> 64;
            }
            let t = u128::from(z[n]) + carry;
            z[n] = t as u64;
            z[n + 1] = (t >> 64) as u64;
            // z + m*p = (z - m) + m*(p+1), and z[0] - m = 0: shift, then add.
            let m = z[0];
            z.copy_within(1..n + 2, 0);
            z[n + 1] = 0;
            let prod = u128::from(m) * u128::from(pp1);
            let t = u128::from(z[k - 1]) + (prod & u128::from(u64::MAX));
            z[k - 1] = t as u64;
            let mut carry = (t >> 64) + (prod >> 64);
            for zj in &mut z[k..=n] {
                let t = u128::from(*zj) + carry;
                *zj = t as u64;
                carry = t >> 64;
            }
        }
        Self::from_raw(&z[..n])
    }

    /// Small integer `x`.
    pub fn from_small(x: u32) -> Self {
        let mut t = [0u64; MAX_LIMBS];
        t[0] = u64::from(x);
        Self::mont_mul(&t[..P::N], P::R2.as_ref())
    }

    /// `self * x` for a small unsigned `x`.
    pub fn mul_small(&self, x: u32) -> Self {
        let mut w = [0u64; MAX_LIMBS + 1];
        let mut carry = 0u128;
        for (wi, &ai) in w.iter_mut().zip(self.limbs()) {
            carry += u128::from(ai) * u128::from(x);
            *wi = carry as u64;
            carry >>= 64;
        }
        w[P::N] = carry as u64;
        Self::partial_reduce(&w[..=P::N])
    }

    /// Reduces `w` (`N + 1` limbs, `< 2^(T + 64)`) to `[0, 2^BITS)`, mod p.
    pub(super) fn partial_reduce(w: &[u64]) -> Self {
        let n = P::N;
        let (ti, ts) = ((P::T / 64) as usize, P::T % 64);
        // w = lo + h * 2^T and 2^T = (1 + p) / C, so h * 2^T = q + r * 2^T (mod p).
        let h = (w[ti] >> ts) | (w[ti + 1] << (64 - ts));
        let (q, r) = (h / P::C, h % P::C);
        let mut out = [0u64; MAX_LIMBS];
        out[..ti].copy_from_slice(&w[..ti]);
        out[ti] = w[ti] & ((1u64 << ts) - 1);
        let mut add = [0u64; MAX_LIMBS];
        add[0] = q;
        add[ti] = r << ts;
        add_masked(&mut out[..n], &add[..n], u64::MAX);
        let mask = 0u64.wrapping_sub(out[n - 1] >> Self::top_bits());
        sub_masked(&mut out[..n], P::P.as_ref(), mask);
        Self::from_raw(&out)
    }

    /// Bits of `2^BITS` stored in the top limb.
    fn top_bits() -> u32 {
        P::BITS - 64 * (P::N as u32 - 1)
    }

    /// `self^2`.
    pub fn square(&self) -> Self {
        *self * *self
    }

    /// `self / 2`.
    pub fn half(&self) -> Self {
        *self * Self { v: P::TWO_INV }
    }

    /// `self / 3`.
    pub fn div3(&self) -> Self {
        *self * Self { v: P::THREE_INV }
    }

    /// `a0` if `ctl == 0`, `a1` if `ctl == 0xFFFF_FFFF`.
    pub fn select(a0: &Self, a1: &Self, ctl: u32) -> Self {
        let m = mask64(ctl);
        let mut d = *a0;
        for (x, y) in d.v.as_mut().iter_mut().zip(a1.limbs()) {
            *x ^= m & (*x ^ y);
        }
        d
    }

    /// Swaps `a` and `b` if `ctl == 0xFFFF_FFFF`; no-op if `ctl == 0`.
    pub fn cswap(a: &mut Self, b: &mut Self, ctl: u32) {
        let m = mask64(ctl);
        for (x, y) in a.v.as_mut().iter_mut().zip(b.v.as_mut()) {
            let t = m & (*x ^ *y);
            *x ^= t;
            *y ^= t;
        }
    }

    /// Whether the element is zero (either representation).
    pub fn is_zero(&self) -> u32 {
        limbs_eq(self.limbs(), P::ZERO.as_ref()) | limbs_eq(self.limbs(), P::P.as_ref())
    }

    /// Constant-time equality.
    pub fn ct_eq(&self, other: &Self) -> u32 {
        (*self - *other).is_zero()
    }
}

impl<P: FpParams> Add for Fp<P> {
    type Output = Self;

    fn add(self, rhs: Self) -> Self {
        let mut d = self;
        add_masked(d.v.as_mut(), rhs.limbs(), u64::MAX);
        for _ in 0..2 {
            let mask = 0u64.wrapping_sub(d.limbs()[P::N - 1] >> Self::top_bits());
            sub_masked(d.v.as_mut(), P::P.as_ref(), mask);
        }
        d
    }
}

impl<P: FpParams> Sub for Fp<P> {
    type Output = Self;

    fn sub(self, rhs: Self) -> Self {
        let mut d = self;
        let borrow = sub_masked(d.v.as_mut(), rhs.limbs(), u64::MAX);
        add_masked(d.v.as_mut(), P::P.as_ref(), 0u64.wrapping_sub(borrow));
        let mask = 0u64.wrapping_sub(d.limbs()[P::N - 1] >> 63);
        add_masked(d.v.as_mut(), P::P.as_ref(), mask);
        d
    }
}

impl<P: FpParams> Mul for Fp<P> {
    type Output = Self;

    fn mul(self, rhs: Self) -> Self {
        Self::mont_mul(self.limbs(), rhs.limbs())
    }
}

impl<P: FpParams> Neg for Fp<P> {
    type Output = Self;

    fn neg(self) -> Self {
        Self::ZERO - self
    }
}
