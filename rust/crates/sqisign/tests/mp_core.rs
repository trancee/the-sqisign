//! Differential tests: Rust `mp` construction, bounds, predicates, comparison
//! and bit queries against the vendor C reference.

#[macro_use]
mod support;

macro_rules! mp_core_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::Rng;
            use core::cmp::Ordering;
            use sqisign::mp::Ibz;
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();

            fn ord(o: Ordering) -> i32 {
                o as i32
            }

            /// `bool` as a C predicate result.
            fn pred(b: bool) -> i32 {
                -i32::from(b)
            }

            #[test]
            fn capacity_matches_reference() {
                use sqisign::mp::MpParams;
                assert_eq!(<$params as MpParams>::IBZ_LIMBS, c::mp::IBZ_NLIMBS);
                assert_eq!(<$params as MpParams>::IBZ_MAX_BITS, CAP);
            }

            #[test]
            fn constants_match_reference() {
                for (r, v, b) in [
                    (I::ZERO, 0, 1),
                    (I::ONE, 1, 2),
                    (I::TWO, 2, 3),
                    (I::THREE, 3, 3),
                ] {
                    same(&r, &c::mp::set(v, b));
                }
                same(&I::default(), &c::mp::set(0, 0));
            }

            #[test]
            fn set_matches_reference() {
                let mut rng = Rng::new(1);
                let mut xs = vec![0, 1, -1, 2, -2, i64::from(i32::MAX), -i64::from(i32::MAX)];
                xs.extend((0..16).map(|_| rng.next_u64() as i64));
                for x in xs {
                    for b in [0, 1, 2, 3, 31, 32, 33, 64] {
                        let mask = u64::MAX >> (64 - b.clamp(1, 32));
                        if (x as u64) & mask == 1 << (b.clamp(1, 32) - 1) {
                            continue; // C asserts the minimum away.
                        }
                        same(&I::from_i64(x, b), &c::mp::set(x, b as i32));
                    }
                }
            }

            #[test]
            fn copy_bits_and_digits_match_reference() {
                let mut rng = Rng::new(2);
                let w: Vec<u64> = (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                for b in [
                    0,
                    1,
                    2,
                    63,
                    64,
                    65,
                    127,
                    128,
                    129,
                    CAP / 2,
                    CAP - 65,
                    CAP - 64,
                    CAP - 1,
                ] {
                    same(
                        &I::from_bits(&w, b),
                        &c::mp::copy_bits(&garbage(&mut rng), &w, b as i32, false),
                    );
                }
                for n in 0..c::mp::IBZ_NLIMBS {
                    let x = c::mp::copy_bits(&garbage(&mut rng), &w, n as i32, true);
                    same(&I::from_digits(&w, n), &x);
                }
            }

            #[test]
            fn to_digits_matches_reference() {
                for (r, x) in samples(3).into_iter().filter(|(r, _)| r.is_positive()) {
                    let (mut a, mut b) = ([7u64; 64], [7u64; 64]);
                    r.to_digits(&mut a);
                    c::mp::to_digits(&x, &mut b);
                    assert_eq!(a, b, "{r:?}");
                }
            }

            #[test]
            fn set_bound_matches_both_reference_variants() {
                for (r, x) in samples(4) {
                    for b in [vec![0], bounds(r.bound())].concat() {
                        let mut s = r;
                        s.set_bound(b);
                        same(&s, &c::mp::set_bound(&x, b as i32, false));
                        same(&s, &c::mp::set_bound(&x, b as i32, true));
                    }
                }
            }

            #[test]
            fn cswap_matches_reference() {
                let s = samples(5);
                for (i, (ra, xa)) in s.iter().enumerate().step_by(7) {
                    let (rb, xb) = s[(i * 31 + 5) % s.len()];
                    for cond in [0, u64::MAX] {
                        let (mut a, mut b) = (*ra, rb);
                        I::cswap(&mut a, &mut b, cond);
                        let (ca, cb) = c::mp::cswap(xa, &xb, cond);
                        same(&a, &ca);
                        same(&b, &cb);
                    }
                }
            }

            #[test]
            fn queries_match_reference() {
                for (r, x) in samples(6) {
                    assert_eq!(r.get(), c::mp::get(&x));
                    assert_eq!(pred(r.is_zero()), c::mp::is_zero(&x), "{r:?}");
                    assert_eq!(pred(r.is_one()), c::mp::is_one(&x), "{r:?}");
                    assert_eq!(pred(r.is_positive()), c::mp::is_positive(&x), "{r:?}");
                    assert_eq!(pred(r.is_even()), c::mp::is_even(&x), "{r:?}");
                    assert_eq!(pred(r.is_odd()), c::mp::is_odd(&x), "{r:?}");
                    assert_eq!(r.two_adic() as i32, c::mp::two_adic(&x), "{r:?}");
                    assert_eq!(r.bitsize() as i32, c::mp::bitsize(&x), "{r:?}");
                    assert_eq!(r.bitsize() as i32, c::mp::bitsize_ct(&x), "{r:?}");
                }
                for e in [1, 63, 64, 65, 200] {
                    let r = I::ONE + I::ONE.mul_2exp(e);
                    let x = c::mp::add(
                        &c::mp::set(1, 2),
                        &c::mp::shift(&c::mp::set(1, 2), e as u32, true),
                    );
                    same(&r, &x);
                    assert_eq!(pred(r.is_one()), c::mp::is_one(&x), "{r:?}");
                }
            }

            #[test]
            fn extract_matches_reference() {
                let mut rng = Rng::new(7);
                for (r, x) in samples(7) {
                    let b = r.bound() as i32;
                    let mut offs = vec![
                        -129,
                        -64,
                        -63,
                        -1,
                        0,
                        1,
                        63,
                        64,
                        65,
                        b - 64,
                        b - 1,
                        b,
                        b + 64,
                    ];
                    offs.push((rng.next_u32() % (b as u32 + 64)) as i32 - 32);
                    for o in offs {
                        assert_eq!(r.extract_u64(o), c::mp::extract_u64(&x, o), "{r:?} at {o}");
                        assert_eq!(r.extract_i64(o), c::mp::extract_i64(&x, o), "{r:?} at {o}");
                    }
                }
            }

            #[test]
            fn comparisons_match_reference() {
                let s = samples(8);
                for (ra, xa) in &s {
                    for (rb, xb) in s.iter().step_by(3) {
                        assert_eq!(ord(ra.cmp(rb)), c::mp::cmp(xa, xb), "{ra:?} vs {rb:?}");
                        assert_eq!(ra == rb, c::mp::cmp(xa, xb) == 0);
                        assert_eq!(ra.partial_cmp(rb).map(ord), Some(c::mp::cmp(xa, xb)));
                    }
                    for y in [0, 1, -1, 2, -2, i32::MAX, -i32::MAX, 0x1234_5678] {
                        assert_eq!(ord(ra.cmp_i32(y)), c::mp::cmp_int32(xa, y), "{ra:?} vs {y}");
                    }
                }
            }

            /// `div_2exp` leaves negatives unnormalised; queries must still
            /// agree on such values.
            #[test]
            fn unnormalised_values_match_reference() {
                for (r, x) in samples(9).into_iter().filter(|(r, _)| !r.is_positive()) {
                    for e in [1, 7, 64, 65] {
                        let (r, x) = (r.div_2exp(e), c::mp::shift(&x, e as u32, false));
                        same(&r, &x);
                        assert_eq!(r.bitsize() as i32, c::mp::bitsize(&x), "{r:?}");
                        assert_eq!(r.bitsize() as i32, c::mp::bitsize_ct(&x), "{r:?}");
                        assert_eq!(pred(r.is_positive()), c::mp::is_positive(&x));
                        assert_eq!(r.extract_u64(3), c::mp::extract_u64(&x, 3));
                        let mut s = r;
                        s.set_bound(r.bound());
                        same(&s, &c::mp::set_bound(&x, r.bound() as i32, false));
                        same(&s, &c::mp::set_bound(&x, r.bound() as i32, true));
                    }
                }
            }

            #[test]
            fn debug_shows_significant_limbs() {
                assert_eq!(format!("{:?}", I::TWO), "Ibz { bitlen: 3, limbs: [2] }");
                assert_eq!(I::ZERO.clone(), I::default());
            }
        }
    };
}

mp_core_suite!(p324_3, sqisign::gf::P324);
mp_core_suite!(p500_27, sqisign::gf::P500);
mp_core_suite!(p664_17, sqisign::gf::P664);
