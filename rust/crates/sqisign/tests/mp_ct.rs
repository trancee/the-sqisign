//! Differential tests: Rust `mp` constant-time shifts, high products, masks,
//! reciprocals and rounding against the vendor C reference.

#[macro_use]
mod support;

macro_rules! mp_ct_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::Rng;
            use sqisign::mp::Ibz;
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();

            /// Shift amounts of both signs, including whole and partial words
            /// and shifts past the capacity.
            fn shifts() -> Vec<i32> {
                let s = [0, 1, 5, 63, 64, 65, 127, 200, CAP - 1, CAP + 70];
                s.iter().flat_map(|&k| [k as i32, -(k as i32)]).collect()
            }

            /// Every seventh sample.
            fn some(seed: u64) -> Vec<(I, c::mp::Ibz)> {
                samples(seed).into_iter().step_by(7).collect()
            }

            #[test]
            fn shift_matches_reference() {
                for (r, x) in samples(40) {
                    for s in shifts() {
                        for max in [s.unsigned_abs(), 2 * CAP as u32] {
                            for out in [1, 64, 65, r.bound(), CAP] {
                                let got = r.ct_shift(s, max, out);
                                let (m, o) = (max as i32, out as i32);
                                same(&got, &c::mp::ct_shift(&x, s, m, o, false));
                                if s >= 0 {
                                    same(&got, &c::mp::ct_shift(&x, s, m, o, true));
                                }
                            }
                        }
                    }
                }
            }

            #[test]
            fn highmul_matches_reference() {
                let s = some(41);
                for (ra, xa) in &s {
                    for (rb, xb) in &s {
                        for out in [1, 64, 100, 129, (ra.bound() + rb.bound()).min(CAP)] {
                            for p in [0, 1, 63, 64, 65, 200, CAP] {
                                let got = I::ct_highmul(ra, rb, -(p as i32), p as u32, out);
                                same(&got, &c::mp::ct_highmul_p(xa, xb, p as i32, out as i32));
                            }
                            for sh in shifts() {
                                let max = 2 * CAP as u32;
                                let got = I::ct_highmul(ra, rb, sh, max, out);
                                let want = c::mp::ct_highmul_s(xa, xb, sh, max as i32, out as i32);
                                same(&got, &want);
                            }
                        }
                    }
                }
            }

            #[test]
            fn masks_match_reference() {
                let s = samples(42);
                for (ra, xa) in &s {
                    assert_eq!(ra.ct_nonzero_mask(), c::mp::ct_nonzero_mask(xa));
                    assert_eq!(
                        ra.ct_nonzero_mask(),
                        0u64.wrapping_sub(u64::from(!ra.is_zero()))
                    );
                    for (rb, xb) in &s {
                        assert_eq!(ra.ct_lt_mask(rb), c::mp::ct_lt_mask(xa, xb));
                        assert_eq!(ra.ct_lt_mask(rb), 0u64.wrapping_sub(u64::from(ra < rb)));
                    }
                }
            }

            /// Positive divisors: random, with only the top bit, and all ones.
            fn divisors(rng: &mut Rng) -> Vec<(I, c::mp::Ibz)> {
                let mut v = Vec::new();
                for bits in [1, 2, 63, 64, 65, 200, CAP / 2, CAP - 7] {
                    let n = c::mp::IBZ_NLIMBS;
                    let mut ones = vec![0; n];
                    for k in 0..bits {
                        ones[k / 64] |= 1 << (k % 64);
                    }
                    let mut top = vec![0; n];
                    top[(bits - 1) / 64] = 1 << ((bits - 1) % 64);
                    let mut rand: Vec<u64> = (0..n).map(|_| rng.next_u64()).collect();
                    for (w, t) in rand.iter_mut().zip(&ones) {
                        *w &= t;
                    }
                    rand[(bits - 1) / 64] |= top[(bits - 1) / 64];
                    for w in [ones, top, rand] {
                        v.push(pair(&w, bits + 1, rng));
                    }
                }
                v
            }

            #[test]
            fn fp_recip_matches_reference() {
                let mut rng = Rng::new(43);
                for (r, x) in divisors(&mut rng) {
                    for p in [64, 65, 127, 128, 300, CAP - 6] {
                        for n in 0..=5 {
                            let (w, s) = r.ct_fp_recip(p, n);
                            let (cw, cs) = c::mp::ct_fp_recip(&x, p as i32, n as i32);
                            same(&w, &cw);
                            assert_eq!(s as i32, cs);
                        }
                    }
                }
            }

            #[test]
            fn fp_recip_approximates_the_reciprocal() {
                let mut rng = Rng::new(44);
                for (d, _) in divisors(&mut rng)
                    .into_iter()
                    .filter(|(d, _)| d.bitsize() < 600)
                {
                    let (w, s) = d.ct_fp_recip(256, 4);
                    let err = (w * d - I::ONE.mul_2exp(256 + s)).abs();
                    assert!(err <= d.mul_2exp(3), "W = {w:?} for D = {d:?}");
                }
            }

            #[test]
            fn round_shift_matches_reference() {
                let limbs = c::mp::IBZ_NLIMBS;
                for (r, x) in samples(45) {
                    for pl in [0, 1, 3, limbs - 2] {
                        for nq in [1, 2, limbs - pl] {
                            if pl + nq <= limbs {
                                let (p, out) = (64 * pl, 64 * nq);
                                let want = c::mp::ct_round_shift_limb(&x, p as i32, out as i32);
                                same(&r.ct_round_shift(p, out), &want);
                            }
                        }
                    }
                }
            }
        }
    };
}

mp_ct_suite!(p324_3, sqisign::gf::P324);
mp_ct_suite!(p500_27, sqisign::gf::P500);
mp_ct_suite!(p664_17, sqisign::gf::P664);
