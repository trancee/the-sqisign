//! The word-level Lagrange-Gauss step over `Z[i]` (vendor
//! `lll/gaussian_xgcd.c`: `LG_gaussian_inner` and its helpers).

use super::word::{
    abs64, mask_if, max0, max_u64, min_u32, mul_high, select32, select_u32, shr_sat64, sqr_high,
    swap_i32, swap_i64, swap_u64,
};

pub const THRESHOLD: i32 = 27;
pub const W: i32 = 2 * THRESHOLD + 5;
const UP_SHIFT: i32 = 3;
const INNER_ITS: usize = THRESHOLD as usize + 1;

/// A Gaussian integer of machine words (C `i64_complex_t`, `i32_complex_t`).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Zw<T> {
    pub re: T,
    pub im: T,
}

pub type Z32 = Zw<i32>;
pub type Z64 = Zw<i64>;

/// `a * 2^k` (C `ct_shift_i64_complex`).
fn shl64(a: Z64, k: u32) -> Z64 {
    let s = |x: i64| (x as u64).wrapping_shl(k) as i64;
    Zw {
        re: s(a.re),
        im: s(a.im),
    }
}

/// `a * 2^k` (C `ct_shift_i32_complex`).
fn shl32(a: Z32, k: u32) -> Z32 {
    let s = |x: i32| (x as u32).wrapping_shl(k) as i32;
    Zw {
        re: s(a.re),
        im: s(a.im),
    }
}

/// C `ct_mul_i32_complex`.
fn mul32(a: Z32, b: Z32) -> Z32 {
    Zw {
        re: a
            .re
            .wrapping_mul(b.re)
            .wrapping_sub(a.im.wrapping_mul(b.im)),
        im: a
            .re
            .wrapping_mul(b.im)
            .wrapping_add(a.im.wrapping_mul(b.re)),
    }
}

/// `norm(a) / 2^64` (C `ct_norm64_approx`).
fn norm64(a: Z64) -> u64 {
    sqr_high(a.re).wrapping_add(sqr_high(a.im))
}

fn swap64(m: u64, a: &mut Z64, b: &mut Z64) {
    swap_i64(m, &mut a.re, &mut b.re);
    swap_i64(m, &mut a.im, &mut b.im);
}

fn swap32(m: u32, a: &mut Z32, b: &mut Z32) {
    swap_i32(m, &mut a.re, &mut b.re);
    swap_i32(m, &mut a.im, &mut b.im);
}

/// Leading zeros of the larger part of `a`.
fn clz(a: Z64) -> u32 {
    (abs64(a.re) | abs64(a.im) | 1).leading_zeros()
}

/// `ceil(log2(a) - log2(b))`, zero arguments read as 1 (C
/// `ceil_log2_diff_u64`).
fn ceil_log2_diff(a: u64, b: u64) -> i32 {
    let d = (b | 1).leading_zeros() as i32 - (a | 1).leading_zeros() as i32;
    let neg = 0u32.wrapping_sub(d as u32 >> 31);
    let sb = b.wrapping_shl(d as u32 & !neg);
    let sa = a.wrapping_shl(d.wrapping_neg() as u32 & neg);
    d + (sb.wrapping_sub(sa) >> 63) as i32
}

/// `round(a / b)` for `a / b` in `[-2, 2]` (C `round_ratio_62bit_ct`).
fn round_ratio(a: i64, b: u64) -> i32 {
    let max_abs = abs64(a);
    let t1 = b >> 1;
    let t2 = b.wrapping_add(t1);
    let res = u32::from(max_abs > t1) + u32::from(max_abs > t2);
    let sign = (a >> 63) as u32;
    (res ^ sign).wrapping_sub(sign) as i32
}

/// `x - (q * y) & valid` for `q = xr + yr i`.
fn sub_mul(x: &mut Z64, q: Z32, y: Z64, valid: u64) {
    let (xr, yr) = (i64::from(q.re), i64::from(q.im));
    let re = xr.wrapping_mul(y.re).wrapping_sub(yr.wrapping_mul(y.im));
    let im = xr.wrapping_mul(y.im).wrapping_add(yr.wrapping_mul(y.re));
    x.re = x.re.wrapping_sub(re & valid as i64);
    x.im = x.im.wrapping_sub(im & valid as i64);
}

/// The 2x2 `Z[i]` transform of up to `INNER_ITS` reduction steps on the
/// windows `(a, c)` and `(b, d)` of the two basis vectors; `swap` allows
/// exchanging them (C `LG_gaussian_inner`).
pub fn inner(v: [Z64; 4], swap: bool, from_v1: i32, from_v2: i32) -> [[Z32; 2]; 2] {
    let [mut a, mut b, mut c, mut d] = v;
    let (mut bf1, mut bf2) = (from_v1, from_v2);
    let one = Zw { re: 1, im: 0 };
    let (mut u00, mut u01, mut u10, mut u11) = (one, Z32::default(), Z32::default(), one);
    let mut valid = u64::MAX;
    let global = mask_if(swap);
    let stop_at = THRESHOLD + (64 - UP_SHIFT - W);
    for _ in 0..INNER_ITS {
        let mut bs1 = max0(min_u32(clz(a), clz(c)) as i32 - UP_SHIFT);
        let mut bs2 = max0(min_u32(clz(b), clz(d)) as i32 - UP_SHIFT);
        let stop1 = mask_if(bs1 > stop_at) & mask_if(bf1 > 0);
        let stop2 = mask_if(bs2 > stop_at) & mask_if(bf2 > 0);
        valid &= !(stop1 | stop2);
        let (mut ab, mut bb) = (shl64(a, bs1 as u32), shl64(b, bs2 as u32));
        let (mut cb, mut db) = (shl64(c, bs1 as u32), shl64(d, bs2 as u32));
        let mut na = norm64(ab).wrapping_add(norm64(cb));
        let mut nb = norm64(bb).wrapping_add(norm64(db));
        let rel = bs2 - bs1;
        let v1_right = select32(rel < 0, -2 * rel, 0) as u32;
        let v2_right = select32(rel > 0, 2 * rel, 0) as u32;
        let diff = shr_sat64(na, v1_right).wrapping_sub(shr_sat64(nb, v2_right));
        let swap_mask = 0u64.wrapping_sub(diff >> 63) & valid;
        valid &= !swap_mask | global;
        let actual = swap_mask & global;
        swap_u64(actual, &mut na, &mut nb);
        swap64(actual, &mut a, &mut b);
        swap64(actual, &mut c, &mut d);
        swap32(actual as u32, &mut u00, &mut u10);
        swap32(actual as u32, &mut u01, &mut u11);
        swap64(actual, &mut ab, &mut bb);
        swap64(actual, &mut cb, &mut db);
        swap_i32(actual as u32, &mut bs1, &mut bs2);
        swap_i32(actual as u32, &mut bf1, &mut bf2);
        let rel = bs2 - bs1;
        let x = mul_high(ab.re, bb.re)
            .wrapping_add(mul_high(ab.im, bb.im))
            .wrapping_add(mul_high(cb.re, db.re))
            .wrapping_add(mul_high(cb.im, db.im));
        let y = mul_high(ab.im, bb.re)
            .wrapping_sub(mul_high(ab.re, bb.im))
            .wrapping_add(mul_high(cb.im, db.re))
            .wrapping_sub(mul_high(cb.re, db.im));
        let s = ceil_log2_diff(max_u64(abs64(x), abs64(y)), nb) - 1;
        let k = select_u32(rel + s < 0, 0, (rel + s) as u32);
        let shift_diff = rel - k as i32;
        let (sl_x, sl_nb) = (
            max0(shift_diff) as u32,
            max0(shift_diff.wrapping_neg()) as u32,
        );
        let nb_adj = nb.wrapping_shl(sl_nb);
        let q = Zw {
            re: round_ratio((x as u64).wrapping_shl(sl_x) as i64, nb_adj),
            im: round_ratio((y as u64).wrapping_shl(sl_x) as i64, nb_adj),
        };
        valid &= !(mask_if(q.re == 0) & mask_if(q.im == 0));
        sub_mul(&mut a, q, shl64(b, k), valid);
        sub_mul(&mut c, q, shl64(d, k), valid);
        let (t10, t11) = (mul32(q, shl32(u10, k)), mul32(q, shl32(u11, k)));
        let v32 = valid as u32 as i32;
        u00 = Zw {
            re: u00.re.wrapping_sub(t10.re & v32),
            im: u00.im.wrapping_sub(t10.im & v32),
        };
        u01 = Zw {
            re: u01.re.wrapping_sub(t11.re & v32),
            im: u01.im.wrapping_sub(t11.im & v32),
        };
    }
    [[u00, u01], [u10, u11]]
}

#[cfg(test)]
mod tests {
    use super::super::probe::{Rng, RUNS};
    use super::{inner, Zw, THRESHOLD, UP_SHIFT, W};
    use sqisign_reference::p324_3::lll as c;

    /// A Gaussian window whose larger part has `bits` bits.
    fn window(bits: u32, rng: &mut Rng) -> [i64; 2] {
        let top = |rng: &mut Rng| {
            let x = (1u64 << (bits - 1)) | (rng.next() & ((1u64 << (bits - 1)) - 1));
            rng.signed(x << 1)
        };
        let small = |rng: &mut Rng| {
            let x = rng.next() >> (65 - bits);
            rng.signed(x << 1)
        };
        if rng.below(2) == 0 {
            [top(rng), small(rng)]
        } else {
            [small(rng), top(rng)]
        }
    }

    /// Windows sized around the stopping shift, either vector first.
    #[test]
    fn inner_matches_c_near_its_stopping_shift() {
        let stop_bits = 64 - UP_SHIFT - (THRESHOLD + (64 - UP_SHIFT - W));
        let mut rng = Rng::new(7);
        for _ in 0..RUNS / 10 {
            let size = |rng: &mut Rng| {
                match rng.below(2) {
                    0 => rng.around(i64::from(stop_bits)) as u32,
                    _ => 2 + rng.below(61) as u32,
                }
                .clamp(2, 62)
            };
            let (s1, s2) = (size(&mut rng), size(&mut rng));
            let v = [
                window(s1, &mut rng),
                window(s2, &mut rng),
                window(s1, &mut rng),
                window(s2, &mut rng),
            ];
            let swap = rng.below(2) == 0;
            let (f1, f2) = (rng.below(3) as i32 - 1, rng.below(3) as i32 - 1);
            let z = v.map(|[re, im]| Zw { re, im });
            let m = inner(z, swap, f1, f2).map(|row| row.map(|x| [x.re, x.im]));
            assert_eq!(
                m,
                c::gaussian_inner(v, swap, f1, f2),
                "{v:?} {swap} {f1} {f2}"
            );
        }
    }
}
