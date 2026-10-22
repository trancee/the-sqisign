//! Integer helpers of the quaternion module (vendor `integers.c`).

use super::lll::dim2_sum_of_squares;
use crate::mp::{Ibz, MpParams};

impl<P: MpParams> Ibz<P> {
    /// `a^2 + b^2` (C `ibz_sum_two_squares`).
    pub fn sum_two_squares(a: &Self, b: &Self) -> Self {
        *a * *a + *b * *b
    }

    /// `self / b` rounded to the nearest integer, ties towards zero (C
    /// `ibz_rounded_div`).
    ///
    /// # Panics
    ///
    /// If `b` is zero.
    pub fn rounded_div(&self, b: &Self) -> Self {
        let (q, r) = self.div_rem(b);
        let r = r.abs();
        let away = i64::from(r + r > b.abs());
        let sign = if self.is_positive() == b.is_positive() {
            1
        } else {
            -1
        };
        q + Self::from_i64(sign * away, 2)
    }

    /// `(x, y)` with `x^2 + y^2 = self` and `x <= y` for a prime `self`, or
    /// `None` if there is none (C `ibz_cornacchia_prime`).
    pub fn cornacchia_prime(&self) -> Option<(Self, Self)> {
        if *self == Self::TWO {
            return Some((Self::from_i64(1, 2), Self::from_i64(1, 2)));
        }
        if self.is_one() {
            return Some((Self::ZERO, Self::from_i64(1, 2)));
        }
        if self.get() & 3 != 1 {
            return None;
        }
        let root = self.sqrt_m1_mod_verified()?;
        let (mut x, mut y) = dim2_sum_of_squares(self, &root);
        let found = x * x + y * y == *self;
        if x > y {
            core::mem::swap(&mut x, &mut y);
        }
        found.then_some((x, y))
    }
}
