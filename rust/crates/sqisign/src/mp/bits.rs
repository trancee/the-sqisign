//! Bit queries (vendor `mp.c`: `ibz_bitsize`, `ibz_two_adic`,
//! `ibz_extract_*`).

use super::{Ibz, MpParams};
use crate::barrier::barrier;

impl<P: MpParams> Ibz<P> {
    /// The significant limbs of `|self|`, without copying the integer.
    /// Constant time in the value.
    fn abs_limbs(&self) -> impl Iterator<Item = u64> + '_ {
        let mask = self.sign_mask();
        self.limbs()
            .iter()
            .scan(u128::from(mask & 1), move |carry, &w| {
                let t = u128::from(w ^ mask) + *carry;
                *carry = t >> 64;
                Some(t as u64)
            })
    }

    /// Bits of `|self|`, 0 for 0 (C `ibz_bitsize`, `ibz_bitsize_ct`).
    /// Constant time in the value.
    pub fn bitsize(&self) -> usize {
        let mut r = 0;
        for (i, w) in self.abs_limbs().enumerate() {
            let nz = 0usize.wrapping_sub(usize::from(w != 0));
            let bits = 64 * i + 64 - w.leading_zeros() as usize;
            r = (bits & nz) | (r & !nz);
        }
        r
    }

    /// The index of the lowest set bit, 0 for 0 (C `ibz_two_adic`).
    pub fn two_adic(&self) -> usize {
        self.limbs()
            .iter()
            .position(|&w| w != 0)
            .map_or(0, |i| 64 * i + self.limbs[i].trailing_zeros() as usize)
    }

    /// The 64 bits of `|self|` starting at bit `offset`, zero beyond the
    /// bound (C `ibz_extract_u64`). Constant time in the value.
    pub fn extract_u64(&self, offset: i32) -> u64 {
        let word = i64::from(offset >> 6);
        let bit = (offset & 63) as u32;
        let (mut w0, mut w1) = (0u64, 0u64);
        for (i, w) in self.abs_limbs().enumerate() {
            let i = i as i64;
            w0 |= w & 0u64.wrapping_sub(u64::from(i == word));
            w1 |= w & 0u64.wrapping_sub(u64::from(i == word + 1));
        }
        (w0 >> bit) | ((w1 << 1) << (63 - bit))
    }

    /// `extract_u64(offset)` without its top bit, signed as `self` (C
    /// `ibz_extract_i64`).
    pub fn extract_i64(&self, offset: i32) -> i64 {
        let u = self.extract_u64(offset) & (u64::MAX >> 1);
        let neg = barrier(0u64.wrapping_sub(u64::from(!self.is_positive())));
        ((u ^ neg).wrapping_sub(neg)) as i64
    }
}
