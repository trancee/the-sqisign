//! Differential tests: Rust GF(p^2) against the vendor C reference.

mod support;

macro_rules! fp2_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::{Rng, SAMPLES};
            use sqisign::gf::Fp2;
            use sqisign_reference::$module as c;

            type F2 = Fp2<$params>;
            const BYTES: usize = 2 * c::FP_BYTES;

            /// Canonical encodings: boundary values, then random elements.
            fn samples(seed: u64) -> Vec<Vec<u8>> {
                let one = c::Fp2::one();
                let i = c::Fp2::one().mul_by_i(1);
                let mut v = vec![
                    vec![0u8; BYTES],
                    one.encode(),
                    i.encode(),
                    one.neg().encode(),
                    one.add(&i).encode(),
                    c::Fp2::small(5).encode(),
                    one.neg().half().encode(),
                ];
                let mut rng = Rng::new(seed);
                for _ in 0..SAMPLES {
                    let re = c::Fp::decode_reduce(&rng.bytes(c::FP_BYTES + 9)).encode();
                    let im = c::Fp::decode_reduce(&rng.bytes(c::FP_BYTES + 9)).encode();
                    v.push([re, im].concat());
                }
                v
            }

            fn rs(b: &[u8]) -> F2 {
                let (x, ok) = F2::decode(b);
                assert_eq!(ok, u32::MAX, "canonical input must decode");
                x
            }

            fn cr(b: &[u8]) -> c::Fp2 {
                c::Fp2::decode(b).0
            }

            fn enc(x: &F2) -> Vec<u8> {
                let mut out = vec![0u8; BYTES];
                x.encode(&mut out);
                out
            }

            #[test]
            fn binary_operations_match_reference() {
                let s = samples(1);
                for a in &s {
                    for b in s.iter().step_by(5) {
                        let (ra, rb, ca, cb) = (rs(a), rs(b), cr(a), cr(b));
                        assert_eq!(enc(&(ra + rb)), ca.add(&cb).encode(), "add");
                        assert_eq!(enc(&(ra - rb)), ca.sub(&cb).encode(), "sub");
                        assert_eq!(enc(&(ra * rb)), ca.mul(&cb).encode(), "mul");
                        assert_eq!(ra.ct_eq(&rb), ca.is_equal(&cb), "is_equal");
                        assert_eq!(ra.less_than(&rb), ca.less_than(&cb), "less_than");
                    }
                }
            }

            #[test]
            fn unary_operations_match_reference() {
                let mut rng = Rng::new(2);
                for a in samples(3) {
                    let (ra, ca) = (rs(&a), cr(&a));
                    assert_eq!(enc(&-ra), ca.neg().encode(), "neg");
                    assert_eq!(enc(&ra.square()), ca.sqr().encode(), "sqr");
                    assert_eq!(enc(&ra.half()), ca.half().encode(), "half");
                    assert_eq!(enc(&ra.frob()), ca.frob().encode(), "frob");
                    assert_eq!(enc(&ra.add_one()), ca.add_one().encode(), "add_one");
                    assert_eq!(enc(&ra.inv()), ca.inv().encode(), "inv");
                    assert_eq!(enc(&ra.sqrt()), ca.sqrt().encode(), "sqrt");
                    let ((r, rok), (cv, cok)) = (ra.sqrt_verify(), ca.sqrt_verify());
                    assert_eq!((enc(&r), rok), (cv.encode(), cok), "sqrt_verify");
                    assert_eq!(ra.is_square(), ca.is_square(), "is_square");
                    assert_eq!(ra.is_zero(), ca.is_zero(), "is_zero");
                    assert_eq!(ra.is_one(), ca.is_one(), "is_one");
                    for ctl in [0, 1] {
                        assert_eq!(
                            enc(&ra.mul_by_i(ctl)),
                            ca.mul_by_i(ctl).encode(),
                            "mul_by_i"
                        );
                    }
                    let k = rng.next_u32() >> 1;
                    assert_eq!(enc(&ra.mul_small(k)), ca.mul_small(k).encode(), "mul_small");
                    assert_eq!(
                        enc(&F2::from_small(k)),
                        c::Fp2::small(u64::from(k)).encode()
                    );
                }
            }

            #[test]
            fn constants_match_reference() {
                assert_eq!(enc(&F2::ZERO), vec![0u8; BYTES]);
                assert_eq!(enc(&F2::ONE), c::Fp2::one().encode());
            }

            #[test]
            fn decode_requires_both_coordinates_canonical() {
                let good = samples(4)[9].clone();
                for half in 0..2 {
                    let mut bad = good.clone();
                    bad[half * c::FP_BYTES..(half + 1) * c::FP_BYTES].fill(0xff);
                    assert_eq!(F2::decode(&bad).1, 0);
                    assert_eq!(c::Fp2::decode(&bad).1, 0);
                }
            }

            #[test]
            fn batched_inversion_matches_reference() {
                let s = samples(5);
                for len in [1, 2, 7] {
                    let r: Vec<F2> = s[9..9 + len].iter().map(|b| rs(b)).collect();
                    let cv: Vec<c::Fp2> = s[9..9 + len].iter().map(|b| cr(b)).collect();
                    let mut out = r.clone();
                    F2::batched_inv(&mut out);
                    let expected: Vec<_> = c::Fp2::batched_inv(&cv)
                        .iter()
                        .map(|x| x.encode())
                        .collect();
                    assert_eq!(out.iter().map(enc).collect::<Vec<_>>(), expected);
                }
                F2::batched_inv(&mut []);
            }

            #[test]
            fn vartime_power_matches_reference() {
                let s = samples(6);
                let mut rng = Rng::new(7);
                for exp in [
                    vec![],
                    vec![0],
                    vec![1],
                    vec![rng.next_u64(), rng.next_u64()],
                ] {
                    let (ra, ca) = (rs(&s[10]), cr(&s[10]));
                    assert_eq!(enc(&ra.pow_vartime(&exp)), ca.pow_vartime(&exp).encode());
                }
            }

            #[test]
            fn select_and_cswap_match_reference() {
                let s = samples(8);
                for ctl in [0, u32::MAX] {
                    let (ra, rb, ca, cb) = (rs(&s[11]), rs(&s[12]), cr(&s[11]), cr(&s[12]));
                    assert_eq!(
                        enc(&F2::select(&ra, &rb, ctl)),
                        c::Fp2::select(&ca, &cb, ctl).encode()
                    );
                    let (mut x, mut y) = (ra, rb);
                    F2::cswap(&mut x, &mut y, ctl);
                    let (cx, cy) = c::Fp2::cswap(&ca, &cb, ctl);
                    assert_eq!((enc(&x), enc(&y)), (cx.encode(), cy.encode()));
                }
            }
        }
    };
}

fp2_suite!(p324_3, sqisign::gf::P324);
fp2_suite!(p500_27, sqisign::gf::P500);
fp2_suite!(p664_17, sqisign::gf::P664);
