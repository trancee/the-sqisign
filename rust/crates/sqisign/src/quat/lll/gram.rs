//! The 2x2 Gram kernel of the dimension-4 reduction (vendor
//! `lll/lll_lg2.c`): materialise a block Gram from the LDL state, then
//! reduce it by Lehmer-style Lagrange-Gauss steps on its leading bits.

use super::config::LllConfig;
use super::word::{abs64, mask, mask_if, max, max0, neg_mask, select32, swap_i32, swap_u64};
use super::{bounded, highmul_p};
use crate::mp::Ibz;
use crate::quat::Mat2;

/// Bitsize threshold between Euclid and division steps.
const THRESHOLD: i32 = 29;
/// Bitsize of the extraction window.
const W: i32 = 2 * THRESHOLD + 3;

/// A block Gram `[[a, b], [b, c]]` (C `quat_lll_gram2_t`; its exponent is
/// never read and not ported).
pub struct Gram2<P: LllConfig> {
    a: Ibz<P>,
    b: Ibz<P>,
    c: Ibz<P>,
}

/// Proven bound on the extra shift (C `LG_EXTRA_SHIFT_MAX`).
fn extra_shift_max<P: LllConfig>() -> u32 {
    ((P::GRAM_TOT - THRESHOLD as usize + 2) / 2) as u32
}

/// The block Gram of `D_k`, `D_{k+1}` and `mu` at scale `2^prec`, scaled
/// down to `GRAM_TOT` bits (C `quat_lll_materialise_block`).
pub fn materialise<P: LllConfig>(dk: &Ibz<P>, dkp1: &Ibz<P>, mu: &Ibz<P>, prec: usize) -> Gram2<P> {
    let wbits = P::E + 16;
    let b_full = highmul_p(mu, dk, prec, wbits);
    let c_full = dkp1.add_bounded(&highmul_p(mu, &b_full, prec, wbits), wbits);
    let sum = dk.add_bounded(&c_full, wbits + 1);
    let e = max0(sum.bitsize() as i32 - (P::GRAM_TOT as i32 - 1));
    let max_e = (wbits + 1) as u32;
    let down = |x: &Ibz<P>| x.ct_shift(-e, max_e, P::GRAM_TOT);
    Gram2 {
        a: down(dk),
        b: down(&b_full),
        c: down(&c_full),
    }
}

/// `k` minimising `|a / b - 2^k|`, with `clz_b` the leading zeros of `b`.
fn log2_ratio(a: u64, b: u64, clz_b: u32) -> i32 {
    let clz_a = (a | 1).leading_zeros();
    let norm_a = (a << clz_a) >> 2;
    let norm_b = (b << clz_b) >> 2;
    let three_b = 3 * norm_b;
    let inc = i32::from(norm_a << 1 >= three_b);
    let dec = i32::from(norm_a << 2 < three_b);
    clz_b as i32 - clz_a as i32 + inc - dec
}

/// Lagrange-Gauss steps on the leading words of `[[a, b], [b, c]]`, as a
/// column unimodular transform; without `swap` only size reduction (C
/// `partial_gram_msb`; its step count is never read).
fn partial_msb(mut a: u64, mut b: i64, mut c: u64, swap: bool) -> [[i32; 2]; 2] {
    let (mut u00, mut u01, mut u10, mut u11) = (1i32, 0i32, 0i32, 1i32);
    let mut valid = u64::MAX;
    let global_swap = mask_if(swap);
    let limit = (64 - W + THRESHOLD) as u32;
    for _ in 0..THRESHOLD / 2 + 2 {
        valid &= !mask_if((a == 0) | (c == 0));
        let negate = (mask_if(b < 0) & valid) as u32;
        let b_abs = abs64(b);
        b = b_abs as i64;
        u00 = ((u00 as u32 ^ negate).wrapping_sub(negate)) as i32;
        u10 = ((u10 as u32 ^ negate).wrapping_sub(negate)) as i32;
        let swap_mask = mask_if(c < a) & valid;
        valid &= !swap_mask | global_swap;
        let actual = swap_mask & global_swap;
        swap_u64(actual, &mut a, &mut c);
        swap_i32(actual as u32, &mut u00, &mut u01);
        swap_i32(actual as u32, &mut u10, &mut u11);
        valid &= !mask_if(b_abs.wrapping_mul(2) <= a);
        let clz_a = (a | 1).leading_zeros();
        let clz_c = (c | 1).leading_zeros();
        valid &= !mask(u64::from(clz_a >= limit) | u64::from(clz_c >= limit));
        let k = max(0, log2_ratio(b_abs, a, clz_a)) as u32;
        let step = b_abs
            .wrapping_shl(k + 1)
            .wrapping_sub(a.wrapping_shl(2 * k));
        c = c.wrapping_sub(step & valid);
        b = b.wrapping_sub((a.wrapping_shl(k) & valid) as i64);
        u01 = u01.wrapping_sub(((u00 as u32).wrapping_shl(k) & valid as u32) as i32);
        u11 = u11.wrapping_sub(((u10 as u32).wrapping_shl(k) & valid as u32) as i32);
    }
    [[u00, u01], [u10, u11]]
}

/// `a * m0 + b * m1 + c * m2` over `wbits`.
fn combine<P: LllConfig>(terms: [(&Ibz<P>, i32); 3], wbits: usize) -> Ibz<P> {
    let [(a, m0), (b, m1), (c, m2)] = terms;
    let r = a.mul_i32_bounded(m0, wbits);
    let r = r.add_bounded(&b.mul_i32_bounded(m1, wbits), wbits);
    r.add_bounded(&c.mul_i32_bounded(m2, wbits), wbits)
}

/// `M^T G M`, with `a` and `b` pre-scaled by the extra shift (C
/// `apply_matrix_2x2_shift`).
fn apply_gram<P: LllConfig>(g: &mut Gram2<P>, m: &[[i32; 2]; 2], extra: i32, wbits: usize) {
    let [[m00, m01], [m10, m11]] = *m;
    let max = extra_shift_max::<P>();
    let na = combine(
        [
            (&g.a, m00.wrapping_mul(m00)),
            (&g.b, 2i32.wrapping_mul(m00).wrapping_mul(m10)),
            (&g.c, m10.wrapping_mul(m10)),
        ],
        wbits,
    );
    let a_sh = g.a.ct_shift(extra, max, wbits);
    let b_sh = g.b.ct_shift(extra, max, wbits);
    let nb = combine(
        [
            (&a_sh, m00.wrapping_mul(m01)),
            (
                &g.b,
                m00.wrapping_mul(m11).wrapping_add(m01.wrapping_mul(m10)),
            ),
            (&g.c, m10.wrapping_mul(m11)),
        ],
        wbits,
    );
    let a_sh2 = g.a.ct_shift(2 * extra, 2 * max, wbits);
    let nc = combine(
        [
            (&a_sh2, m01.wrapping_mul(m01)),
            (&b_sh, 2i32.wrapping_mul(m01).wrapping_mul(m11)),
            (&g.c, m11.wrapping_mul(m11)),
        ],
        wbits,
    );
    *g = Gram2 {
        a: na,
        b: nb,
        c: nc,
    };
}

/// `U M`, the second column of `U` pre-scaled by the extra shift (C
/// `apply_matrix_to_U_shift`).
fn apply_u<P: LllConfig>(u: &mut Mat2<P>, m: &[[i32; 2]; 2], extra: i32) {
    let bits = P::U;
    let max = extra_shift_max::<P>();
    for row in u.iter_mut() {
        let sh = row[0].ct_shift(extra, max, bits);
        let lin = |x: &Ibz<P>, mx: i32, y: &Ibz<P>, my: i32| {
            let r = x.mul_i32_bounded(mx, bits);
            r.add_bounded(&y.mul_i32_bounded(my, bits), bits)
        };
        let n0 = lin(&row[0], m[0][0], &row[1], m[1][0]);
        let n1 = lin(&sh, m[0][1], &row[1], m[1][1]);
        *row = [n0, n1];
    }
}

/// Reduces `g` by `outer` Lehmer passes; returns the column unimodular
/// transform (C `quat_lll_gram_lehmer`).
pub fn lehmer<P: LllConfig>(g: &mut Gram2<P>, outer: usize) -> Mat2<P> {
    let one = bounded(Ibz::ONE, P::U);
    let zero = bounded(Ibz::ZERO, P::U);
    let mut u = [[one, zero], [zero, one]];
    for x in [&mut g.a, &mut g.b, &mut g.c] {
        x.set_bound(P::GRAM_WORK);
    }
    for _ in 0..outer {
        let swap = neg_mask(&(g.c - g.a));
        Ibz::cswap(&mut g.a, &mut g.c, swap);
        for row in u.iter_mut() {
            let [x, y] = row;
            Ibz::cswap(x, y, swap);
        }
        let bits_a = g.a.bitsize() as i32;
        let bits_c = g.c.bitsize() as i32;
        let rho = bits_c - bits_a;
        let large = rho >= THRESHOLD;
        let from_c = max(bits_c - W, 0);
        let extra = select32(large, (rho - THRESHOLD + 2) / 2, 0);
        let from_a = select32(large, from_c - 2 * extra, from_c);
        let from_b = select32(large, from_c - extra, from_c);
        let m = partial_msb(
            g.a.extract_u64(from_a),
            g.b.extract_i64(from_b),
            g.c.extract_u64(from_c),
            !large,
        );
        apply_gram(g, &m, extra, P::GRAM_WORK);
        apply_u(&mut u, &m, extra);
    }
    u
}

#[cfg(test)]
mod tests {
    use super::super::config::LllConfig;
    use super::super::probe::{same, to_c, Rng, RUNS};
    use super::{lehmer, log2_ratio, partial_msb, Gram2, THRESHOLD};
    use crate::gf::P324;
    use sqisign_reference::p324_3::lll as c;

    #[test]
    fn log2_ratio_matches_c() {
        let mut rng = Rng::new(1);
        for _ in 0..RUNS {
            let b = rng.word();
            let a = rng.near(b);
            let clz_b = (b | 1).leading_zeros();
            assert_eq!(
                log2_ratio(a, b, clz_b),
                c::fast_log2_ratio(a, b, clz_b),
                "{a:#x} {b:#x}"
            );
        }
    }

    #[test]
    fn partial_msb_matches_c() {
        let mut rng = Rng::new(2);
        for _ in 0..RUNS {
            let a = rng.word();
            let c_ = rng.near(a);
            let b = match rng.below(3) {
                0 => rng.signed(a),
                1 => {
                    let x = rng.near(a) << 1;
                    rng.signed(x)
                }
                _ => {
                    let x = rng.word();
                    rng.signed(x)
                }
            };
            let swap = rng.below(2) == 0;
            assert_eq!(
                partial_msb(a, b, c_, swap),
                c::partial_gram_msb(a, b, c_, swap),
                "{a:#x} {b:#x} {c_:#x} {swap}"
            );
        }
    }

    /// A positive definite Gram `[[a, b], [b, c]]` of `GRAM_TOT` bits whose
    /// diagonal sizes differ by about the Euclid/division threshold.
    #[test]
    fn lehmer_matches_c_near_the_threshold() {
        let tot = <P324 as LllConfig>::GRAM_TOT;
        let mut rng = Rng::new(6);
        for _ in 0..RUNS / 100 {
            let small = 1 + rng.below(tot as u64 - 40) as usize;
            let large = rng.around((small + THRESHOLD as usize) as i64).min(tot - 1);
            let (ga, gc) = if rng.below(2) == 0 {
                (small, large)
            } else {
                (large, small)
            };
            let gb = rng.below(((ga + gc) / 2) as u64) as usize;
            let b = rng.value(gb, tot);
            let b = if rng.below(2) == 0 { b } else { -b };
            let mut g = Gram2::<P324> {
                a: rng.value(ga, tot),
                b,
                c: rng.value(gc, tot),
            };
            let outer = 1 + rng.below(4) as usize;
            let x = c::Gram2 {
                a: to_c(&g.a),
                b: to_c(&g.b),
                c: to_c(&g.c),
                e: 0,
            };
            let u = lehmer(&mut g, outer);
            let (x, xu) = c::gram_lehmer(&x, outer as i32);
            same(&g.a, &x.a);
            same(&g.b, &x.b);
            same(&g.c, &x.c);
            u.iter()
                .flatten()
                .zip(xu.iter().flatten())
                .for_each(|(r, x)| same(r, x));
        }
    }
}
