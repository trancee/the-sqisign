//! GF(p^2) = GF(p)[i] / (i^2 + 1).

use super::fp::Fp;
use super::params::FpParams;
use core::ops::{Add, Mul, Neg, Sub};

/// An element `re + im * i` of GF(p^2).
pub struct Fp2<P: FpParams> {
    /// Real part.
    pub re: Fp<P>,
    /// Imaginary part.
    pub im: Fp<P>,
}

impl<P: FpParams> Clone for Fp2<P> {
    fn clone(&self) -> Self {
        *self
    }
}

impl<P: FpParams> Copy for Fp2<P> {}

impl<P: FpParams> Fp2<P> {
    /// Zero.
    pub const ZERO: Self = Self {
        re: Fp::ZERO,
        im: Fp::ZERO,
    };
    /// One.
    pub const ONE: Self = Self {
        re: Fp::ONE,
        im: Fp::ZERO,
    };
    /// Length of the canonical encoding (real part first).
    pub const ENCODED_BYTES: usize = 2 * P::ENCODED_BYTES;

    /// Small integer `x`.
    pub fn from_small(x: u32) -> Self {
        Self {
            re: Fp::from_small(x),
            im: Fp::ZERO,
        }
    }

    /// `self * x` for a small unsigned `x`.
    pub fn mul_small(&self, x: u32) -> Self {
        Self {
            re: self.re.mul_small(x),
            im: self.im.mul_small(x),
        }
    }

    /// Constant-time equality.
    pub fn ct_eq(&self, other: &Self) -> u32 {
        self.re.ct_eq(&other.re) & self.im.ct_eq(&other.im)
    }

    /// Whether the element is zero.
    pub fn is_zero(&self) -> u32 {
        self.re.is_zero() & self.im.is_zero()
    }

    /// Whether the element is one.
    pub fn is_one(&self) -> u32 {
        self.re.ct_eq(&Fp::ONE) & self.im.is_zero()
    }

    /// `self / 2`.
    pub fn half(&self) -> Self {
        Self {
            re: self.re.half(),
            im: self.im.half(),
        }
    }

    /// `self + 1`.
    pub fn add_one(&self) -> Self {
        Self {
            re: self.re + Fp::ONE,
            im: self.im,
        }
    }

    /// `self^2`, as `(a + b)(a - b) + 2ab * i`.
    pub fn square(&self) -> Self {
        let ab = self.re * self.im;
        Self {
            re: Fp::mul_sum(&self.re, &self.im, &(self.re - self.im)),
            im: ab + ab,
        }
    }

    /// `a0` if `ctl == 0`, `a1` if `ctl == 0xFFFF_FFFF`.
    pub fn select(a0: &Self, a1: &Self, ctl: u32) -> Self {
        Self {
            re: Fp::select(&a0.re, &a1.re, ctl),
            im: Fp::select(&a0.im, &a1.im, ctl),
        }
    }

    /// Swaps `a` and `b` if `ctl == 0xFFFF_FFFF`; no-op if `ctl == 0`.
    pub fn cswap(a: &mut Self, b: &mut Self, ctl: u32) {
        Fp::cswap(&mut a.re, &mut b.re, ctl);
        Fp::cswap(&mut a.im, &mut b.im, ctl);
    }

    /// Writes the encoding (real part, then imaginary part) into `dst`.
    ///
    /// # Panics
    /// If `dst.len() != ENCODED_BYTES`.
    pub fn encode(&self, dst: &mut [u8]) {
        let (re, im) = dst.split_at_mut(P::ENCODED_BYTES);
        self.re.encode(re);
        self.im.encode(im);
    }

    /// Decodes an encoding; the mask is `0xFFFF_FFFF` iff both parts are canonical.
    ///
    /// # Panics
    /// If `src.len() != ENCODED_BYTES`.
    pub fn decode(src: &[u8]) -> (Self, u32) {
        assert_eq!(src.len(), Self::ENCODED_BYTES, "encoded length");
        let (re, re_ok) = Fp::decode(&src[..P::ENCODED_BYTES]);
        let (im, im_ok) = Fp::decode(&src[P::ENCODED_BYTES..]);
        (Self { re, im }, re_ok & im_ok)
    }

    /// `self * i` if `ctl == 1`, `self * (-i)` if `ctl == 0` (C convention).
    pub fn mul_by_i(&self, ctl: u32) -> Self {
        let (nre, nim) = (-self.re, -self.im);
        let mask = 0u32.wrapping_sub(ctl);
        Self {
            re: Fp::select(&self.im, &nim, mask),
            im: Fp::select(&nre, &self.re, mask),
        }
    }

    /// The Frobenius conjugate `re - im * i`.
    pub fn frob(&self) -> Self {
        Self {
            re: self.re,
            im: -self.im,
        }
    }

    /// Lexicographic comparison of the encodings, from the first byte.
    ///
    /// Returns `0xFFFF_FFFF` if `self < other`, else `0`.
    pub fn less_than(&self, other: &Self) -> u32 {
        let mut a = [0u8; 2 * super::consts::MAX_LIMBS * 8];
        let mut b = [0u8; 2 * super::consts::MAX_LIMBS * 8];
        let n = Self::ENCODED_BYTES;
        self.encode(&mut a[..n]);
        other.encode(&mut b[..n]);
        let mut is_less = 0u32;
        for (&x, &y) in a[..n].iter().zip(&b[..n]) {
            let lt = u32::from(x).wrapping_sub(u32::from(y)) >> 31;
            let gt = u32::from(y).wrapping_sub(u32::from(x)) >> 31;
            let eq = 1 ^ (lt | gt);
            is_less = (eq & is_less) | lt;
        }
        0u32.wrapping_sub(is_less)
    }
}

impl<P: FpParams> Add for Fp2<P> {
    type Output = Self;

    fn add(self, rhs: Self) -> Self {
        Self {
            re: self.re + rhs.re,
            im: self.im + rhs.im,
        }
    }
}

impl<P: FpParams> Sub for Fp2<P> {
    type Output = Self;

    fn sub(self, rhs: Self) -> Self {
        Self {
            re: self.re - rhs.re,
            im: self.im - rhs.im,
        }
    }
}

impl<P: FpParams> Mul for Fp2<P> {
    type Output = Self;

    /// Karatsuba: three GF(p) multiplications.
    fn mul(self, rhs: Self) -> Self {
        let t0 = Fp::mul_sums(&self.re, &self.im, &rhs.re, &rhs.im);
        let t1 = self.im * rhs.im;
        let re = self.re * rhs.re;
        Self {
            re: re - t1,
            im: t0 - t1 - re,
        }
    }
}

impl<P: FpParams> Neg for Fp2<P> {
    type Output = Self;

    fn neg(self) -> Self {
        Self {
            re: -self.re,
            im: -self.im,
        }
    }
}
