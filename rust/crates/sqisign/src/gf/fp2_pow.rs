//! GF(p^2) inversion, square roots and exponentiation.

use super::fp::Fp;
use super::fp2::Fp2;
use super::params::FpParams;

impl<P: FpParams> Fp2<P> {
    /// The inverse `conj / norm`, or zero for zero.
    pub fn inv(&self) -> Self {
        let norm_inv = (self.re.square() + self.im.square()).inv();
        Self {
            re: self.re * norm_inv,
            im: -(self.im * norm_inv),
        }
    }

    /// Whether `self` is a square, via the norm (zero included).
    pub fn is_square(&self) -> u32 {
        (self.re.square() + self.im.square()).is_square()
    }

    /// A square root if `self` is a square.
    ///
    /// Algorithm of Aardal et al., "Optimized One-Dimensional SQIsign
    /// Verification on Intel and Cortex-M4" (eprint 2024/1563), matching the
    /// vendor root choice.
    pub fn sqrt(&self) -> Self {
        let (a0, a1) = (self.re, self.im);
        // delta = sqrt(a0^2 + a1^2); if a1 = 0 use delta = a0 (avoids delta = -a0).
        let delta = (a0.square() + a1.square()).sqrt();
        let delta = Fp::select(&delta, &a0, a1.is_zero());
        let x0 = delta + a0;
        let t0 = x0 + x0;
        let x1 = t0.exp3div4();
        let x0 = x0 * x1;
        let x1 = x1 * a1;
        let t1 = (x0 + x0).square();
        let f = (t0 - t1).is_zero();
        Self {
            re: Fp::select(&x1, &x0, f),
            im: Fp::select(&-x0, &x1, f),
        }
    }

    /// The square root and `0xFFFF_FFFF` iff it squares back to `self`.
    pub fn sqrt_verify(&self) -> (Self, u32) {
        let r = self.sqrt();
        (r, r.square().ct_eq(self))
    }

    /// Inverts every element in place with one field inversion (Montgomery's trick).
    pub fn batched_inv(xs: &mut [Self]) {
        let Some(&first) = xs.first() else { return };
        let mut prefix = Vec::with_capacity(xs.len());
        prefix.push(first);
        for x in &xs[1..] {
            let last = prefix[prefix.len() - 1];
            prefix.push(last * *x);
        }
        let mut inv = prefix[xs.len() - 1].inv();
        for i in (1..xs.len()).rev() {
            let xi = xs[i];
            xs[i] = inv * prefix[i - 1];
            inv = inv * xi;
        }
        xs[0] = inv;
    }

    /// `self^exp` for a public little-endian limb exponent (variable time).
    pub fn pow_vartime(&self, exp: &[u64]) -> Self {
        let mut acc = *self;
        let mut out = Self::ONE;
        for &word in exp {
            for i in 0..64 {
                if (word >> i) & 1 == 1 {
                    out = out * acc;
                }
                acc = acc.square();
            }
        }
        out
    }
}
