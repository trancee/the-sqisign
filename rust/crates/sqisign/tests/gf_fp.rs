//! Differential tests: Rust GF(p) against the vendor C reference.

mod support;

macro_rules! fp_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::{Rng, SAMPLES};
            use sqisign::gf::Fp;
            use sqisign_reference::$module as c;

            type F = Fp<$params>;
            const BYTES: usize = c::FP_BYTES;
            // The C reference converts `set_small`/`mul_small` arguments via `int`.
            const SMALL_MAX: u32 = i32::MAX as u32;

            /// Canonical encodings: boundary values, then random elements.
            fn samples(seed: u64) -> Vec<Vec<u8>> {
                let one = c::Fp::one();
                let mut v = vec![
                    vec![0u8; BYTES],
                    one.encode(),
                    c::Fp::small(2).encode(),
                    c::Fp::small(3).encode(),
                    c::Fp::small(u64::from(SMALL_MAX)).encode(),
                    one.neg().encode(),
                    c::Fp::small(2).neg().encode(),
                    one.neg().half().encode(),
                    one.half().encode(),
                ];
                let mut rng = Rng::new(seed);
                for _ in 0..SAMPLES {
                    v.push(c::Fp::decode_reduce(&rng.bytes(2 * BYTES)).encode());
                }
                v
            }

            fn rs(b: &[u8]) -> F {
                let (x, ok) = F::decode(b);
                assert_eq!(ok, u32::MAX, "canonical input must decode");
                x
            }

            fn cr(b: &[u8]) -> c::Fp {
                c::Fp::decode(b).0
            }

            fn enc(x: &F) -> Vec<u8> {
                let mut out = vec![0u8; BYTES];
                x.encode(&mut out);
                out
            }

            #[test]
            fn encoded_length_matches_reference() {
                assert_eq!(F::ENCODED_BYTES, BYTES);
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
                    assert_eq!(enc(&ra.div3()), ca.div3().encode(), "div3");
                    assert_eq!(enc(&ra.exp3div4()), ca.exp3div4().encode(), "exp3div4");
                    assert_eq!(enc(&ra.inv()), ca.inv().encode(), "inv");
                    assert_eq!(enc(&ra.sqrt()), ca.sqrt().encode(), "sqrt");
                    assert_eq!(ra.is_square(), ca.is_square(), "is_square");
                    assert_eq!(ra.is_zero(), ca.is_zero(), "is_zero");
                    let k = rng.next_u32() & SMALL_MAX;
                    assert_eq!(enc(&ra.mul_small(k)), ca.mul_small(k).encode(), "mul_small");
                    let k = rng.next_u32() & SMALL_MAX;
                    assert_eq!(enc(&F::from_small(k)), c::Fp::small(u64::from(k)).encode());
                }
            }

            #[test]
            fn small_scalars_are_unsigned_32_bit() {
                let k = u32::MAX;
                let mut bytes = vec![0u8; BYTES];
                bytes[..4].copy_from_slice(&k.to_le_bytes());
                assert_eq!(enc(&F::from_small(k)), bytes);
                let x = rs(&samples(4)[12]);
                assert_eq!(enc(&x.mul_small(k)), enc(&(x * rs(&bytes))));
            }

            #[test]
            fn mixed_operation_chains_match_reference() {
                let s = samples(5);
                let mut rng = Rng::new(6);
                let (mut r, mut c) = (rs(&s[10]), cr(&s[10]));
                for _ in 0..2000 {
                    let b = &s[(rng.next_u64() as usize) % s.len()];
                    match rng.next_u64() % 6 {
                        0 => (r, c) = (r + rs(b), c.add(&cr(b))),
                        1 => (r, c) = (r - rs(b), c.sub(&cr(b))),
                        2 => (r, c) = (r * rs(b), c.mul(&cr(b))),
                        3 => (r, c) = (-r, c.neg()),
                        4 => (r, c) = (r.half(), c.half()),
                        _ => (r, c) = (r.mul_small(3), c.mul_small(3)),
                    }
                    assert_eq!(enc(&r), c.encode());
                    assert_eq!(r.is_zero(), c.is_zero());
                }
            }

            #[test]
            fn decode_rejects_non_canonical_input_like_reference() {
                let mut p = c::Fp::one().neg().encode();
                let carry = p.iter_mut().try_for_each(|byte| {
                    *byte = byte.wrapping_add(1);
                    if *byte == 0 {
                        Ok(())
                    } else {
                        Err(())
                    }
                });
                assert!(carry.is_err());
                for bad in [p, vec![0xffu8; BYTES]] {
                    let (r, ok) = F::decode(&bad);
                    let (cv, cok) = c::Fp::decode(&bad);
                    assert_eq!((ok, cok), (0, 0));
                    assert_eq!(enc(&r), cv.encode());
                }
            }

            #[test]
            fn decode_reduce_matches_reference() {
                let mut rng = Rng::new(7);
                for len in [
                    0,
                    1,
                    7,
                    8,
                    9,
                    BYTES - 1,
                    BYTES,
                    BYTES + 1,
                    2 * BYTES,
                    3 * BYTES + 5,
                    200,
                ] {
                    for bytes in [vec![0xffu8; len], rng.bytes(len)] {
                        let expected = c::Fp::decode_reduce(&bytes).encode();
                        assert_eq!(enc(&F::decode_reduce(&bytes)), expected, "len {len}");
                    }
                }
            }

            #[test]
            fn select_and_cswap_match_reference() {
                let s = samples(8);
                for ctl in [0, u32::MAX] {
                    let (ra, rb, ca, cb) = (rs(&s[11]), rs(&s[12]), cr(&s[11]), cr(&s[12]));
                    assert_eq!(
                        enc(&F::select(&ra, &rb, ctl)),
                        c::Fp::select(&ca, &cb, ctl).encode()
                    );
                    let (mut x, mut y) = (ra, rb);
                    F::cswap(&mut x, &mut y, ctl);
                    let (cx, cy) = c::Fp::cswap(&ca, &cb, ctl);
                    assert_eq!((enc(&x), enc(&y)), (cx.encode(), cy.encode()));
                }
            }
        }
    };
}

fp_suite!(p324_3, sqisign::gf::P324);
fp_suite!(p500_27, sqisign::gf::P500);
fp_suite!(p664_17, sqisign::gf::P664);
