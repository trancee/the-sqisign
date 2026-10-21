//! Fixed-width non-negative scalars for the ladders (the subset of vendor
//! `ibz_t` that `ec.c` uses; its values there are always in `[0, 2^(kbits + 2))`).

use core::cmp::Ordering;

/// Words of storage: the largest `NWORDS_ORDER` (11) plus one for `4 * s`.
const WORDS: usize = 12;

/// A little-endian unsigned integer of `WORDS` words.
#[derive(Clone, Copy)]
pub(super) struct Scalar([u64; WORDS]);

impl Scalar {
    /// The low `kbits` bits of `src` (C `ibz_copy_bits`).
    ///
    /// # Panics
    /// If `src` holds fewer than `kbits` bits or `kbits > 64 * (WORDS - 1)`.
    pub(super) fn from_bits(src: &[u64], kbits: usize) -> Self {
        assert!(kbits <= 64 * (WORDS - 1), "scalar too wide");
        let n = kbits.div_ceil(64);
        let mut w = [0u64; WORDS];
        w[..n].copy_from_slice(&src[..n]);
        Self(w).truncated(kbits)
    }

    /// `self mod 2^kbits` (constant time in the value; `kbits` is public).
    fn truncated(mut self, kbits: usize) -> Self {
        for (i, w) in self.0.iter_mut().enumerate() {
            let lo = 64 * i;
            *w &= if kbits >= lo + 64 {
                u64::MAX
            } else if kbits <= lo {
                0
            } else {
                (1u64 << (kbits - lo)) - 1
            };
        }
        self
    }

    /// `(self - 1) mod 2^kbits`, in constant time.
    pub(super) fn dec_mod2exp(&self, kbits: usize) -> Self {
        let mut r = *self;
        let mut borrow = 1u64;
        for w in r.0.iter_mut() {
            let (d, b) = w.overflowing_sub(borrow);
            *w = d;
            borrow = u64::from(b);
        }
        r.truncated(kbits)
    }

    /// The least significant bit.
    pub(super) fn bit0(&self) -> u64 {
        self.0[0] & 1
    }

    /// The little-endian words.
    pub(super) fn words(&self) -> &[u64] {
        &self.0
    }

    /// Whether the value is zero (variable time).
    pub(super) fn is_zero(&self) -> bool {
        self.0.iter().all(|&w| w == 0)
    }

    /// Whether the value is one (variable time).
    pub(super) fn is_one(&self) -> bool {
        self.0[0] == 1 && self.0[1..].iter().all(|&w| w == 0)
    }

    /// `self >>= 1`.
    pub(super) fn shr1(&mut self) {
        for i in 0..WORDS {
            let hi = if i + 1 < WORDS {
                self.0[i + 1] << 63
            } else {
                0
            };
            self.0[i] = (self.0[i] >> 1) | hi;
        }
    }

    /// `4 * self` (the top two bits of the last word must be clear).
    pub(super) fn shl2(&self) -> Self {
        let mut r = [0u64; WORDS];
        for (i, w) in r.iter_mut().enumerate() {
            let lo = if i > 0 { self.0[i - 1] >> 62 } else { 0 };
            *w = (self.0[i] << 2) | lo;
        }
        Self(r)
    }

    /// `self -= b`, for `self >= b`.
    pub(super) fn sub_assign(&mut self, b: &Self) {
        let mut borrow = false;
        for (x, &y) in self.0.iter_mut().zip(b.0.iter()) {
            let (d, b1) = x.overflowing_sub(y);
            let (d, b2) = d.overflowing_sub(u64::from(borrow));
            *x = d;
            borrow = b1 | b2;
        }
    }

    /// Swaps `a` and `b` when `mask` is all ones (constant time).
    pub(super) fn cswap(a: &mut Self, b: &mut Self, mask: u64) {
        for (x, y) in a.0.iter_mut().zip(b.0.iter_mut()) {
            let t = (*x ^ *y) & mask;
            *x ^= t;
            *y ^= t;
        }
    }

    /// Numeric comparison (variable time).
    pub(super) fn cmp(&self, b: &Self) -> Ordering {
        self.0.iter().rev().cmp(b.0.iter().rev())
    }
}
