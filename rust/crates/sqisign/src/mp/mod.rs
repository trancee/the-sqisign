//! Bounded signed integers (vendor `mp/ref`, C `ibz_t`).
//!
//! An [`Ibz`] is a two's-complement integer over at most
//! [`MpParams::IBZ_LIMBS`] 64-bit limbs with a bit bound `bitlen`, sign bit
//! included: `-2^(bitlen-1) <= x < 2^(bitlen-1)`. Only the low
//! `nlimbs(bitlen)` limbs are significant, and the highest of them is
//! sign-extended above bit `bitlen - 1`. As in C, operations widen the bound
//! (addition by one bit, multiplication to the sum of the bounds) up to the
//! capacity `64 * IBZ_LIMBS`, where results wrap.

mod arith;
mod bits;
mod ct;
mod div;
mod gcd;
mod modular;
mod monty;
mod prime;
mod rand;

use crate::barrier::barrier;
use core::cmp::Ordering;
use core::fmt;
use core::ops::{IndexMut, Range, RangeFrom, RangeTo};

use crate::gf::{FpParams, P324, P500, P664};

/// Limbs of the widest [`Ibz`] (C `IBZ_NLIMBS` of `p664_17`), which sizes the
/// scratch buffers shared by all parameter sets.
pub const MAX_IBZ_LIMBS: usize = 55;

/// The limb array of an [`Ibz`]: `[u64; IBZ_LIMBS]`, indexable like a slice.
pub trait Limbs:
    Copy
    + AsRef<[u64]>
    + AsMut<[u64]>
    + IndexMut<usize, Output = u64>
    + IndexMut<Range<usize>, Output = [u64]>
    + IndexMut<RangeFrom<usize>, Output = [u64]>
    + IndexMut<RangeTo<usize>, Output = [u64]>
{
}

impl<const N: usize> Limbs for [u64; N] {}

/// Integer capacity of a parameter set (vendor `encoded_sizes.h`).
pub trait MpParams: FpParams {
    /// Limbs of an [`Ibz`] (C `IBZ_NLIMBS`, `RADIX == 64`).
    const IBZ_LIMBS: usize;
    /// Largest bound (C `IBZ_MAX_BITS`).
    const IBZ_MAX_BITS: usize = 64 * Self::IBZ_LIMBS;
    /// `[u64; IBZ_LIMBS]`, so an [`Ibz`] carries no unused limbs.
    type IbzLimbs: Limbs;
    /// The limbs of 0, 1, 2 and 3, for the constants [`Ibz::ZERO`] to
    /// [`Ibz::THREE`].
    const SMALL_LIMBS: [Self::IbzLimbs; 4];
}

/// `[0, 1, 2, 3]` as `N`-limb integers.
const fn small_limbs<const N: usize>() -> [[u64; N]; 4] {
    let mut s = [[0; N]; 4];
    s[1][0] = 1;
    s[2][0] = 2;
    s[3][0] = 3;
    s
}

macro_rules! mp_params {
    ($p:ty, $limbs:literal) => {
        impl MpParams for $p {
            const IBZ_LIMBS: usize = $limbs;
            type IbzLimbs = [u64; $limbs];
            const SMALL_LIMBS: [Self::IbzLimbs; 4] = small_limbs();
        }
    };
}

mp_params!(P324, 30);
mp_params!(P500, 40);
mp_params!(P664, 55);

/// Limbs holding `bits` bits (C `NUM_LIMBS`).
const fn nlimbs(bits: usize) -> usize {
    bits.div_ceil(64)
}

/// A bounded signed integer (C `ibz_t`).
pub struct Ibz<P: MpParams> {
    bitlen: usize,
    limbs: P::IbzLimbs,
}

impl<P: MpParams> Clone for Ibz<P> {
    fn clone(&self) -> Self {
        *self
    }
}

impl<P: MpParams> Copy for Ibz<P> {}

impl<P: MpParams> Default for Ibz<P> {
    /// Zero (C `ibz_init`).
    fn default() -> Self {
        Self::ZERO
    }
}

impl<P: MpParams> fmt::Debug for Ibz<P> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Ibz")
            .field("bitlen", &self.bitlen)
            .field("limbs", &self.limbs())
            .finish()
    }
}

impl<P: MpParams<IbzLimbs = [u64; N]>, const N: usize> Ibz<P> {
    /// The precomputed constant `{.bitlen = bitlen, .limbs = limbs}`, whose
    /// `limbs` are already reduced to the bound (a vendor `precomp` table).
    ///
    /// # Panics
    ///
    /// At compile time, if `limbs` exceeds the capacity.
    pub(crate) const fn from_raw(bitlen: usize, limbs: &[u64]) -> Self {
        let mut l = [0; N];
        let mut i = 0;
        while i < limbs.len() {
            l[i] = limbs[i];
            i += 1;
        }
        Self { bitlen, limbs: l }
    }
}

impl<P: MpParams> Ibz<P> {
    /// `v` with the bound `bitlen`.
    fn small(v: u64, bitlen: usize) -> Self {
        let mut r = Self::ZERO;
        r.bitlen = bitlen;
        r.limbs[0] = v;
        r
    }

    /// 0 (C `ibz_const_zero`).
    pub const ZERO: Self = Self {
        bitlen: 1,
        limbs: P::SMALL_LIMBS[0],
    };
    /// 1 (C `ibz_const_one`).
    pub const ONE: Self = Self {
        bitlen: 2,
        limbs: P::SMALL_LIMBS[1],
    };
    /// 2 (C `ibz_const_two`).
    pub const TWO: Self = Self {
        bitlen: 3,
        limbs: P::SMALL_LIMBS[2],
    };
    /// 3 (C `ibz_const_three`).
    pub const THREE: Self = Self {
        bitlen: 3,
        limbs: P::SMALL_LIMBS[3],
    };

    /// `x` truncated to `bitlen` bits, `bitlen` clamped to `[1, 32]` (C
    /// `ibz_set`).
    pub fn from_i64(x: i64, bitlen: usize) -> Self {
        let mut r = Self::small(x as u64, bitlen.clamp(1, 32));
        r.normalise();
        r
    }

    /// The low `bitlen` bits of the little-endian `src`, as a non-negative
    /// integer of bound `bitlen + 1` (C `ibz_copy_bits`).
    pub fn from_bits(src: &[u64], bitlen: usize) -> Self {
        let n = nlimbs(bitlen);
        let mut r = Self::small(0, bitlen + 1);
        r.limbs[..n].copy_from_slice(&src[..n]);
        let n1 = nlimbs(r.bitlen);
        if n1 > n {
            r.limbs[n1 - 1] = 0;
        } else {
            r.limbs[n1 - 1] &= (1 << (bitlen - 64 * (n1 - 1))) - 1;
        }
        r
    }

    /// The `words` little-endian words of `src`, as a non-negative integer
    /// (C `ibz_copy_digits`).
    pub fn from_digits(src: &[u64], words: usize) -> Self {
        Self::from_bits(src, 64 * words)
    }

    /// Writes the significant words of a non-negative `self` to `out` (C
    /// `ibz_to_digits`).
    pub fn to_digits(&self, out: &mut [u64]) {
        let n = nlimbs(self.bitsize());
        out[..n].copy_from_slice(&self.limbs[..n]);
    }

    /// The significant limbs.
    pub fn limbs(&self) -> &[u64] {
        &self.limbs[..nlimbs(self.bitlen)]
    }

    /// The bound (C `ibz_get_bound`).
    pub fn bound(&self) -> usize {
        self.bitlen
    }

    /// The low 32 bits (C `ibz_get`).
    pub fn get(&self) -> i32 {
        self.limbs[0] as i32
    }

    /// Clears the bits above `bitlen` in the top limb and sign-extends bit
    /// `bitlen - 1` (C `normalise`). The C check for `-2^(bitlen-1)` can never
    /// fire (its loop stops one limb short) and is not ported.
    fn normalise(&mut self) {
        let n = nlimbs(self.bitlen);
        let b = self.bitlen - 64 * (n - 1);
        let top = self.limbs[n - 1] & (u64::MAX >> (64 - b));
        let v = 1u64 << (b - 1);
        self.limbs[n - 1] = (top & (v - 1)).wrapping_sub(top & v);
    }

    /// Changes the bound to `bitlen`, clamped to `[1, IBZ_MAX_BITS]`:
    /// sign-extends when widening, truncates when narrowing (C
    /// `ibz_set_bound`, `ibz_set_bound_ct`; constant time in the value).
    pub fn set_bound(&mut self, bitlen: usize) {
        let bitlen = bitlen.min(P::IBZ_MAX_BITS);
        if bitlen >= self.bitlen {
            let fill = self.sign_mask();
            self.limbs[nlimbs(self.bitlen)..nlimbs(bitlen)].fill(fill);
            self.bitlen = bitlen;
        } else {
            self.bitlen = bitlen.max(1);
            self.normalise();
        }
    }

    /// Swaps `a` and `b` if `cond` is all ones, keeps them if it is 0 (C
    /// `ibz_cswap`; other masks are meaningless there). Constant time.
    pub fn cswap(a: &mut Self, b: &mut Self, cond: u64) {
        let cond = barrier(cond);
        for (x, y) in a.limbs.as_mut().iter_mut().zip(b.limbs.as_mut()) {
            let t = cond & (*x ^ *y);
            *x ^= t;
            *y ^= t;
        }
        let t = (cond as usize) & (a.bitlen ^ b.bitlen);
        a.bitlen ^= t;
        b.bitlen ^= t;
    }

    /// All ones if negative, else 0.
    fn sign_mask(&self) -> u64 {
        0u64.wrapping_sub(self.limbs[nlimbs(self.bitlen) - 1] >> 63)
    }

    /// `self >= 0` (C `ibz_is_positive`).
    pub fn is_positive(&self) -> bool {
        self.sign_mask() == 0
    }

    /// `self == 0` (C `ibz_is_zero`).
    pub fn is_zero(&self) -> bool {
        self.limbs().iter().all(|&w| w == 0)
    }

    /// `self == 1` (C `ibz_is_one`).
    pub fn is_one(&self) -> bool {
        self.limbs[0] == 1 && self.limbs()[1..].iter().all(|&w| w == 0)
    }

    /// Whether `self` is even (C `ibz_is_even`).
    pub fn is_even(&self) -> bool {
        self.limbs[0] & 1 == 0
    }

    /// Whether `self` is odd (C `ibz_is_odd`).
    pub fn is_odd(&self) -> bool {
        !self.is_even()
    }

    /// Compares with `y` (C `ibz_cmp_int32`).
    pub fn cmp_i32(&self, y: i32) -> Ordering {
        self.cmp(&Self::from_i64(y.into(), 32))
    }
}

impl<P: MpParams> Ord for Ibz<P> {
    /// Value order (C `ibz_cmp`).
    fn cmp(&self, other: &Self) -> Ordering {
        let (a_hi, b_hi) = (self.sign_mask(), other.sign_mask());
        if a_hi != b_hi {
            return b_hi.cmp(&a_hi);
        }
        let (la, lb) = (nlimbs(self.bitlen), nlimbs(other.bitlen));
        for i in (0..la.max(lb)).rev() {
            let a = if i < la { self.limbs[i] } else { a_hi };
            let b = if i < lb { other.limbs[i] } else { b_hi };
            if a != b {
                return a.cmp(&b);
            }
        }
        Ordering::Equal
    }
}

impl<P: MpParams> PartialOrd for Ibz<P> {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl<P: MpParams> PartialEq for Ibz<P> {
    /// Value equality, whatever the bounds.
    fn eq(&self, other: &Self) -> bool {
        self.cmp(other) == Ordering::Equal
    }
}

impl<P: MpParams> Eq for Ibz<P> {}

#[cfg(test)]
mod tests {
    use super::{nlimbs, small_limbs, Ibz};
    use crate::gf::P324;

    #[test]
    fn nlimbs_rounds_up() {
        assert_eq!([0, 1, 64, 65, 128].map(nlimbs), [0, 1, 1, 2, 2]);
    }

    /// `small_limbs` otherwise only runs in const items, at compile time.
    #[test]
    fn small_limbs_are_zero_to_three() {
        assert_eq!(small_limbs::<2>(), [[0, 0], [1, 0], [2, 0], [3, 0]]);
    }

    /// `from_raw` otherwise only runs in const items, at compile time.
    #[test]
    fn from_raw_keeps_the_bound_and_limbs() {
        let x = Ibz::<P324>::from_raw(66, &[5, 3]);
        assert_eq!((x.bound(), x.limbs()), (66, &[5, 3][..]));
    }
}
