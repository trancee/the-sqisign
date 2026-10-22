//! Branch-free machine-word helpers of the lattice reductions (vendor
//! `mp_ct.h`). Shifts wrap their amount like the C code's defined range.

use crate::mp::{Ibz, MpParams};

/// All ones if `x != 0`, else 0 (C `ct_mask`).
pub fn mask(x: u64) -> u64 {
    0u64.wrapping_sub((x | x.wrapping_neg()) >> 63)
}

/// All ones if `cond`, else 0.
pub fn mask_if(cond: bool) -> u64 {
    mask(u64::from(cond))
}

/// All ones if `x < 0`, else 0 (C `~ibz_is_positive`).
pub fn neg_mask<P: MpParams>(x: &Ibz<P>) -> u64 {
    mask_if(!x.is_positive())
}

/// `max(e, 0)` (C `ct_max0`).
pub fn max0(e: i32) -> i32 {
    e & !(e >> 31)
}

/// `a` if `cond`, else `b` (C `ct_select32`).
pub fn select32(cond: bool, a: i32, b: i32) -> i32 {
    let m = -i32::from(cond);
    (a & m) | (b & !m)
}

/// `max(a, b)` (C `ct_max`).
pub fn max(a: i32, b: i32) -> i32 {
    select32(a > b, a, b)
}

/// `min(a, b)` (C `ct_min`).
pub fn min(a: i32, b: i32) -> i32 {
    select32(a < b, a, b)
}

/// `a` if `cond`, else `b` (C `ct_select_u32`).
pub fn select_u32(cond: bool, a: u32, b: u32) -> u32 {
    let m = 0u32.wrapping_sub(u32::from(cond));
    (a & m) | (b & !m)
}

/// `min(a, b)` (C `ct_min_u32`).
pub fn min_u32(a: u32, b: u32) -> u32 {
    select_u32(a < b, a, b)
}

/// `max(a, b)` (C `ct_max_u64`).
pub fn max_u64(a: u64, b: u64) -> u64 {
    let m = mask_if(a > b);
    (a & m) | (b & !m)
}

/// Swaps `a` and `b` under the all-ones `m` (C `ct_swap_u64`).
pub fn swap_u64(m: u64, a: &mut u64, b: &mut u64) {
    let t = (*a ^ *b) & m;
    *a ^= t;
    *b ^= t;
}

/// Swaps `a` and `b` under the all-ones `m` (C `ct_swap_i64`).
pub fn swap_i64(m: u64, a: &mut i64, b: &mut i64) {
    let t = (*a ^ *b) & m as i64;
    *a ^= t;
    *b ^= t;
}

/// Swaps `a` and `b` under the all-ones `m` (C `ct_swap_i32`).
pub fn swap_i32(m: u32, a: &mut i32, b: &mut i32) {
    let t = (*a ^ *b) & m as i32;
    *a ^= t;
    *b ^= t;
}

/// `|x|` (C `ct_abs64`).
pub fn abs64(x: i64) -> u64 {
    abs_sign(x).0
}

/// `(|x|, 1 if x < 0 else 0)` (C `ct_abs_sign_i64`).
pub fn abs_sign(x: i64) -> (u64, u64) {
    let m = (x >> 63) as u64;
    (((x as u64) ^ m).wrapping_sub(m), m & 1)
}

/// `x >> s`, 0 for `s >= 64` (C `ct_shr_sat64`).
pub fn shr_sat64(x: u64, s: u32) -> u64 {
    (x >> (s & 63)) & mask_if(s < 64)
}

/// `floor(sqrt(n))` (C `ct_isqrt_32`).
pub fn isqrt_32(n: u32) -> u64 {
    let mut res = 0u32;
    for bit in (0..16).rev() {
        let t = res | (1 << bit);
        let m = 0u32.wrapping_sub(u32::from(t * t <= n));
        res = (m & t) | (!m & res);
    }
    res.into()
}

/// The high word of the signed product `a * b` (C `ct_mul_high`).
pub fn mul_high(a: i64, b: i64) -> i64 {
    ((i128::from(a) * i128::from(b)) >> 64) as i64
}

/// The high word of `a^2` (C `ct_sqr_high`).
pub fn sqr_high(a: i64) -> u64 {
    mul_high(a, a) as u64
}

/// `floor(x / y)`, `y != 0`, by restoring division (C
/// `ct_div_unsigned_nonzero`).
pub fn div_nonzero(x: u64, y: u64) -> u64 {
    let (mut q, mut r) = (0u64, 0u64);
    for i in (0..64).rev() {
        r = (r << 1) | ((x >> i) & 1);
        let diff = r.wrapping_sub(y);
        let borrow = ((!r & y) | ((!r | y) & diff)) >> 63;
        let m = 0u64.wrapping_sub(1 - borrow);
        r = (diff & m) | (r & !m);
        q = (q << 1) | (1 & m);
    }
    q
}

/// Leading redundant sign bits of `x` (C `__builtin_clrsbll`).
pub fn clrsb(x: i64) -> i32 {
    (x ^ (x >> 63)).leading_zeros() as i32 - 1
}

#[cfg(test)]
mod tests {
    use super::{isqrt_32, shr_sat64};

    #[test]
    fn shr_sat64_is_zero_from_64() {
        for s in 0..130 {
            assert_eq!(
                shr_sat64(u64::MAX, s),
                u64::MAX.checked_shr(s).unwrap_or(0),
                "{s}"
            );
        }
    }

    #[test]
    fn isqrt_32_is_the_floor_square_root() {
        for t in 1..=0xffffu32 {
            assert_eq!(isqrt_32(t * t), t.into(), "{t}^2");
            assert_eq!(isqrt_32(t * t - 1), (t - 1).into(), "{t}^2 - 1");
        }
        assert_eq!(isqrt_32(u32::MAX), 0xffff);
    }
}
