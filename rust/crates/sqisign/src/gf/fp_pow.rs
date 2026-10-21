//! Fixed exponentiations in GF(p): inversion, square roots and residuosity.
//!
//! All exponents depend only on the public prime, so the square-and-multiply
//! schedules are constant time in the base.

use super::fp::Fp;
use super::params::FpParams;

impl<P: FpParams> Fp<P> {
    /// `self^(2^k - 1)` for `k >= 1`, via an addition chain on `k`'s bits.
    fn pow_ones(&self, k: u32) -> Self {
        let mut x = *self;
        let mut e = 1;
        for i in (0..31 - k.leading_zeros()).rev() {
            let mut y = x;
            for _ in 0..e {
                y = y.square();
            }
            x = y * x;
            e <<= 1;
            if (k >> i) & 1 == 1 {
                x = x.square() * *self;
                e += 1;
            }
        }
        x
    }

    /// `self^((p - 3) / 4)`, with `(p - 3) / 4 = (C - 1) 2^(T-2) + 2^(T-2) - 1`.
    pub fn exp3div4(&self) -> Self {
        let m = self.pow_ones(P::T - 2);
        let ma = m * *self;
        let e = P::C - 1;
        let mut z = ma;
        for i in (0..63 - e.leading_zeros()).rev() {
            z = z.square();
            if (e >> i) & 1 == 1 {
                z = z * ma;
            }
        }
        z * m
    }

    /// `self^(p - 2)`: the inverse, or zero for zero.
    pub fn inv(&self) -> Self {
        let t = self.exp3div4().square().square();
        t * *self
    }

    /// `self^((p + 1) / 4)`: a square root if `self` is a square.
    pub fn sqrt(&self) -> Self {
        self.exp3div4() * *self
    }

    /// Whether `self` is a square (zero included).
    pub fn is_square(&self) -> u32 {
        let t = self.exp3div4().square() * *self;
        t.ct_eq(&Self::ONE) | self.is_zero()
    }
}
