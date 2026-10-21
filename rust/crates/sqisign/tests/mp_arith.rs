//! Differential tests: Rust `mp` arithmetic against the vendor C reference.

#[macro_use]
mod support;

macro_rules! mp_arith_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::Rng;
            use sqisign::mp::Ibz;
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();

            const SMALL: [i32; 9] = [0, 1, -1, 2, -3, 0x7654_3210, i32::MAX, -i32::MAX, i32::MIN];

            #[test]
            fn add_sub_mul_match_reference() {
                let s = samples(10);
                for (ra, xa) in &s {
                    for (rb, xb) in &s {
                        same(&(*ra + *rb), &c::mp::add(xa, xb));
                        same(&(*ra - *rb), &c::mp::sub(xa, xb));
                        same(&(*ra * *rb), &c::mp::mul(xa, xb));
                    }
                }
            }

            #[test]
            fn negation_matches_reference() {
                for (r, x) in samples(11) {
                    same(&-r, &c::mp::neg(&x));
                    same(&r.abs(), &c::mp::abs(&x));
                    same(&r.cneg(0), &c::mp::cneg(&x, 0));
                    same(&r.cneg(u64::MAX), &c::mp::cneg(&x, u64::MAX));
                }
            }

            #[test]
            fn power_of_two_shifts_match_reference() {
                let mut rng = Rng::new(12);
                for (r, x) in samples(12) {
                    let b = r.bound();
                    let mut es = vec![
                        0,
                        1,
                        2,
                        63,
                        64,
                        65,
                        127,
                        128,
                        129,
                        b - 1,
                        b,
                        b + 1,
                        CAP - 1,
                        CAP,
                    ];
                    es.push(rng.next_u64() as usize % (CAP + 1));
                    for e in es.into_iter().filter(|&e| e <= CAP) {
                        same(&r.mul_2exp(e), &c::mp::shift(&x, e as u32, true));
                        same(&r.div_2exp(e), &c::mp::shift(&x, e as u32, false));
                    }
                }
            }

            #[test]
            fn bounded_helpers_match_reference() {
                let s = samples(13);
                for (i, (r, x)) in s.iter().enumerate() {
                    let (rb, xb) = &s[(i * 17 + 3) % s.len()];
                    for k in bounds(r.bound()) {
                        let ck = k as i32;
                        same(&r.add_bounded(rb, k), &c::mp::add_and_set_bound(x, xb, ck));
                        for v in SMALL {
                            same(
                                &r.mul_i32_bounded(v, k),
                                &c::mp::mul_by_int_and_set_bound(x, v, ck),
                            );
                            if v != i32::MIN {
                                // C asserts the minimum away.
                                same(
                                    &r.add_i32_bounded(v, k),
                                    &c::mp::add_int_and_set_bound(x, v, ck),
                                );
                            }
                        }
                    }
                }
            }

            /// Products, sums and negations wrap at the capacity like C.
            #[test]
            fn capacity_wraps_like_reference() {
                let s: Vec<_> = samples(14)
                    .into_iter()
                    .filter(|(r, _)| r.bound() >= CAP - 64)
                    .collect();
                for (r, x) in &s {
                    let (mut rr, mut xx) = (*r, *x);
                    for _ in 0..4 {
                        rr = rr * rr + *r;
                        xx = c::mp::add(&c::mp::mul(&xx, &xx), x);
                        same(&rr, &xx);
                        same(&-rr, &c::mp::neg(&xx));
                    }
                }
            }
        }
    };
}

mp_arith_suite!(p324_3, sqisign::gf::P324);
mp_arith_suite!(p500_27, sqisign::gf::P500);
mp_arith_suite!(p664_17, sqisign::gf::P664);
