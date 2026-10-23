//! Constant-time fixed-point and wide arithmetic (vendor `mp.c`: `ibz_ct_*`).
//! Constant time in the limb values; bounds, shift limits, precisions and
//! round counts are public.

use super::{nlimbs, Ibz, MpParams, MAX_IBZ_LIMBS};
use crate::barrier::barrier;

/// Room for the guarded product of two integers.
const WIDE: usize = 2 * MAX_IBZ_LIMBS + 1;

/// Word-ladder stages covering any word shift up to `max_shift / 64`.
fn stages(max_shift: u32) -> u32 {
    u32::BITS - (max_shift >> 6).leading_zeros()
}

/// `t << shift` for `shift >= 0`, else `t >> -shift` sign-extending, over
/// the words of `t`; `shift` is secret and at most `max_shift` in size (C
/// `mp_shift_ct`, `mp_shl_ct`, `mp_ashr_bits`).
fn shift_words(t: &mut [u64], shift: i32, max_shift: u32) {
    let n = t.len();
    let right = (shift >> 31) as u64;
    let mag = shift.unsigned_abs();
    let (ws, bs) = (mag >> 6, mag & 63);
    let sign = 0u64.wrapping_sub(t[n - 1] >> 63);
    let mut src = [0u64; WIDE];
    for b in 0..stages(max_shift) {
        let off = 1usize << b;
        let m = 0u64.wrapping_sub(u64::from((ws >> b) & 1));
        src[..n].copy_from_slice(t);
        for (i, o) in t.iter_mut().enumerate() {
            let l = if i >= off { src[i - off] } else { 0 };
            let r = if i + off < n { src[i + off] } else { sign };
            let c = l ^ ((l ^ r) & right);
            *o ^= (*o ^ c) & m;
        }
    }
    src[..n].copy_from_slice(t);
    for (i, o) in t.iter_mut().enumerate() {
        let l = if i > 0 { src[i - 1] } else { 0 };
        let r = if i + 1 < n { src[i + 1] } else { sign };
        let vl = (src[i] << bs) | ((l >> 1) >> (63 - bs));
        let vr = (src[i] >> bs) | ((r << 1) << (63 - bs));
        *o = vl ^ ((vl ^ vr) & right);
    }
}

/// `floor((xhi * 2^64 + xlo) / y)` for `xhi < y`, by restoring division.
fn div128(xhi: u64, xlo: u64, y: u64) -> u64 {
    let (mut r, mut q) = (xhi, 0);
    for i in (0..64).rev() {
        let hi = r >> 63;
        r = (r << 1) | ((xlo >> i) & 1);
        let ge = barrier(hi | u64::from(!r.overflowing_sub(y).1));
        r = r.wrapping_sub(y & 0u64.wrapping_sub(ge));
        q = (q << 1) | ge;
    }
    q
}

impl<P: MpParams> Ibz<P> {
    /// The low `nlimbs(out_bitlen)` words of `buf`, wrapped to the bound
    /// `out_bitlen`.
    fn narrow(buf: &[u64], out_bitlen: usize) -> Self {
        let n = nlimbs(out_bitlen);
        let mut r = Self::ZERO;
        r.limbs[..n].copy_from_slice(&buf[..n]);
        r.bitlen = 64 * n;
        r.set_bound(out_bitlen);
        r
    }

    /// `self * 2^shift`, rounded down for negative `shift`, wrapped to the
    /// bound `out_bitlen`; `shift` is secret with `|shift| <= max_shift` (C
    /// `ibz_ct_shift`, `ibz_ct_shl`).
    pub fn ct_shift(&self, shift: i32, max_shift: u32, out_bitlen: usize) -> Self {
        let work = self.bitlen.max(out_bitlen);
        let mut t = *self;
        t.set_bound(work);
        shift_words(&mut t.limbs[..nlimbs(work)], shift, max_shift);
        Self::narrow(t.limbs.as_ref(), out_bitlen)
    }

    /// `a * b * 2^shift`, rounded down for negative `shift`, wrapped to the
    /// bound `out_bitlen`, which needs at most as many words as the
    /// operands plus one; `shift` is secret with `|shift| <= max_shift` (C
    /// `ibz_ct_highmul_s`, and `ibz_ct_highmul_p` for `shift = -P`).
    pub fn ct_highmul(a: &Self, b: &Self, shift: i32, max_shift: u32, out_bitlen: usize) -> Self {
        let (sa, sb) = (a.sign_mask(), b.sign_mask());
        let (x, y) = (a.cneg(sa), b.cneg(sb));
        let (na, nb) = (nlimbs(a.bitlen), nlimbs(b.bitlen));
        let mut w = [0u64; WIDE];
        for i in 0..na {
            let mut carry = 0u128;
            for j in 0..nb {
                let t =
                    u128::from(x.limbs[i]) * u128::from(y.limbs[j]) + u128::from(w[i + j]) + carry;
                w[i + j] = t as u64;
                carry = t >> 64;
            }
            w[i + nb] = carry as u64;
        }
        let n = na + nb + 1;
        // C also clears the sign for a zero operand; negating zero is zero.
        let sign = sa ^ sb;
        let mut carry = u128::from(sign & 1);
        for o in &mut w[..n] {
            let t = u128::from(*o ^ sign) + carry;
            *o = t as u64;
            carry = t >> 64;
        }
        shift_words(&mut w[..n], shift, max_shift);
        Self::narrow(&w, out_bitlen)
    }

    /// All ones if `self < b`, else 0 (C `ibz_ct_lt_mask`).
    pub fn ct_lt_mask(&self, b: &Self) -> u64 {
        let (la, lb) = (nlimbs(self.bitlen), nlimbs(b.bitlen));
        let (sa, sb) = (self.sign_mask(), b.sign_mask());
        let (mut top, mut borrow) = (0, false);
        for i in 0..=la.max(lb) {
            let x = if i < la { self.limbs[i] } else { sa };
            let y = if i < lb { b.limbs[i] } else { sb };
            (top, borrow) = x.borrowing_sub(y, borrow);
        }
        0u64.wrapping_sub(top >> 63)
    }

    /// All ones if `self != 0`, else 0 (C `ibz_ct_nonzero_mask`).
    pub fn ct_nonzero_mask(&self) -> u64 {
        let acc = self.limbs().iter().fold(0, |a, &w| a | w);
        0u64.wrapping_sub((acc | acc.wrapping_neg()) >> 63)
    }

    /// For positive `self` with `s` bits and `p >= 64`: `(W, s)` with
    /// `W ~ 2^(p + s) / self` of bound `p + 4`, from a 64-bit seed refined by
    /// `nrounds` Newton steps (C `ibz_ct_fp_recip`).
    pub fn ct_fp_recip(&self, p: usize, nrounds: usize) -> (Self, usize) {
        let s = self.bitsize();
        let wbits = p + 4;
        let max_shift = p.max(self.bitlen) as u32;
        let d = self.ct_shift(p as i32 - s as i32, max_shift, wbits);
        let q = div128(u64::MAX >> 1, u64::MAX, d.extract_u64(p as i32 - 64));
        let mut w = Self::from_digits(&[q], 1).ct_shift(p as i32 - 63, p as u32 - 63, wbits);
        // C widens `two` and `t` to `p + 6` bits; products depend only on values.
        let two = Self::ONE.mul_2exp(p + 1);
        let (down, max) = (-(p as i32), p as u32);
        for _ in 0..nrounds {
            let t = Self::ct_highmul(&d, &w, down, max, wbits);
            w = Self::ct_highmul(&w, &(two - t), down, max, wbits);
        }
        (w, s)
    }

    /// `self / 2^p` rounded to nearest, ties up, as a non-negative bound
    /// `out_bitlen`; both multiples of 64 (C `ibz_ct_round_shift_limb`).
    pub fn ct_round_shift(&self, p: usize, out_bitlen: usize) -> Self {
        let (pl, nq) = (p / 64, out_bitlen / 64);
        let mut x = *self;
        x.set_bound(64 * (pl + nq));
        let mut carry = pl > 0 && x.limbs[pl - 1] >> 63 == 1;
        let mut r = Self::ZERO;
        for i in 0..nq {
            (r.limbs[i], carry) = x.limbs[pl + i].carrying_add(0, carry);
        }
        r.bitlen = out_bitlen;
        r
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stages_cover_the_word_shift() {
        assert_eq!(
            [stages(63), stages(64), stages(191), stages(192)],
            [0, 1, 2, 2]
        );
    }

    #[test]
    fn div128_is_floor_division() {
        for (hi, lo, y) in [
            (0, 7, 2),
            (1, 0, 3),
            (u64::MAX >> 1, u64::MAX, 1 << 63),
            (5, 9, u64::MAX),
        ] {
            let x = (u128::from(hi) << 64) | u128::from(lo);
            assert_eq!(u128::from(div128(hi, lo, y)), x / u128::from(y));
        }
    }
}
