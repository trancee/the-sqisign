//! GF(p) arithmetic over saturated 64-bit limbs in Montgomery form.
//!
//! Mirrors the vendor sat64 backend: values are partially reduced to
//! `[0, 2^BITS)` (so zero has two representations, `0` and `p`) and every
//! operation is constant time in its operands. Predicates return the C
//! convention `0x0000_0000` (false) or `0xFFFF_FFFF` (true).

use super::consts::MAX_LIMBS;
use super::limbs::LimbArray;
use super::params::FpParams;
use crate::barrier::barrier;
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
    barrier(ctl as i32 as i64 as u64)
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
    /// Not inlined: one unrolled copy per prime keeps the hot code in the instruction cache.
    #[inline(never)]
    fn mont_mul(a: &P::Limbs, b: &P::Limbs) -> Self {
        Self {
            v: a.mont_mul(b, P::C << (P::T % 64)),
        }
    }

    /// `(a0 + a1) (b0 + b1)`, as the vendor arm64 `fp2_mul`: the sums are not reduced,
    /// which [`super::consts::check_shape`] allows for.
    pub(super) fn mul_sums(a0: &Self, a1: &Self, b0: &Self, b1: &Self) -> Self {
        let (mut a, mut b) = (a0.v, b0.v);
        a.add_masked(&a1.v, u64::MAX);
        b.add_masked(&b1.v, u64::MAX);
        Self::mont_mul(&a, &b)
    }

    /// `(a0 + a1) b`, with the sum not reduced (see [`Self::mul_sums`]).
    pub(super) fn mul_sum(a0: &Self, a1: &Self, b: &Self) -> Self {
        let mut a = a0.v;
        a.add_masked(&a1.v, u64::MAX);
        Self::mont_mul(&a, &b.v)
    }

    /// Small integer `x`.
    pub fn from_small(x: u32) -> Self {
        let mut t = P::ZERO;
        t.as_mut()[0] = u64::from(x);
        Self::mont_mul(&t, &P::R2)
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

/// The all-ones mask of a subtraction borrow, ORed with the opaque `zero`. LLVM
/// branches on the borrow flag behind an opaque zero on x86 and 32-bit Arm
/// (`ct_branches`), so there the mask takes the full barrier.
#[cfg(target_arch = "aarch64")]
#[inline(always)]
fn borrow_mask(borrow: u64, zero: u64) -> u64 {
    0u64.wrapping_sub(borrow) | zero
}

#[cfg(not(target_arch = "aarch64"))]
#[inline(always)]
fn borrow_mask(borrow: u64, _zero: u64) -> u64 {
    barrier(0u64.wrapping_sub(borrow))
}

impl<P: FpParams> Add for Fp<P> {
    type Output = Self;

    fn add(self, rhs: Self) -> Self {
        // The masks are ORed with an opaque zero instead of passing through `barrier`:
        // they are just as opaque to the optimiser, but the store and reload of
        // `black_box` happen once, off the carry chain.
        let zero = barrier(0u64);
        let mut d = self;
        d.v.add_masked(&rhs.v, u64::MAX);
        for _ in 0..2 {
            let mask = 0u64.wrapping_sub(d.limbs()[P::N - 1] >> Self::top_bits()) | zero;
            d.v.sub_masked(&P::P, mask);
        }
        d
    }
}

impl<P: FpParams> Sub for Fp<P> {
    type Output = Self;

    fn sub(self, rhs: Self) -> Self {
        let zero = barrier(0u64);
        let mut d = self;
        let borrow = d.v.sub_masked(&rhs.v, u64::MAX);
        d.v.add_masked(&P::P, borrow_mask(borrow, zero));
        let mask = 0u64.wrapping_sub(d.limbs()[P::N - 1] >> 63) | zero;
        d.v.add_masked(&P::P, mask);
        d
    }
}

impl<P: FpParams> Mul for Fp<P> {
    type Output = Self;

    fn mul(self, rhs: Self) -> Self {
        Self::mont_mul(&self.v, &rhs.v)
    }
}

impl<P: FpParams> Neg for Fp<P> {
    type Output = Self;

    fn neg(self) -> Self {
        Self::ZERO - self
    }
}
