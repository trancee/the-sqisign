//! Differential tests: Rust PRNG streams and `mp` sampling against the
//! vendor C reference, seeded through the NIST CTR-DRBG.

#[macro_use]
mod support;

macro_rules! prng_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::Rng;
            use sqisign::mp::Ibz;
            use sqisign::prng::{Prng, Separator, SEED_BYTES};
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();

            const SEPARATORS: [Separator; 4] = [
                Separator::Default,
                Separator::Response,
                Separator::Commitment,
                Separator::Keygen,
            ];

            /// The Rust and C PRNGs seeded from the same DRBG entropy.
            fn seeded(seed: u64) -> Prng {
                let entropy: [u8; SEED_BYTES] =
                    Rng::new(seed).bytes(SEED_BYTES).try_into().unwrap();
                Prng::new(c::mp::prng_seed(&entropy))
            }

            #[test]
            fn separators_are_the_c_strings() {
                let names: Vec<&[u8; 3]> = SEPARATORS.iter().map(|s| s.bytes()).collect();
                assert_eq!(names, [b"def", b"res", b"com", b"key"]);
            }

            #[test]
            fn domain_streams_match_reference() {
                for seed in 0..4 {
                    let p = seeded(seed);
                    for sep in SEPARATORS {
                        let mut r = p.domain(sep);
                        let mut x = c::mp::Domain::new(sep.bytes()).unwrap();
                        for len in [0, 1, 7, 135, 136, 137, 300, 1000] {
                            let mut got = vec![0; len];
                            r.fill(&mut got);
                            assert_eq!(got, x.fill(len), "{sep:?} {len}");
                        }
                    }
                    c::mp::prng_clear();
                }
            }

            #[test]
            fn rand_interval_matches_reference() {
                let mut p = seeded(10);
                let mut rd = p.domain(Separator::Response);
                let mut xd = c::mp::Domain::new(b"res").unwrap();
                let s: Vec<_> = samples(11)
                    .into_iter()
                    .filter(|(r, _)| r.bound() + 64 < CAP)
                    .step_by(3)
                    .collect();
                let mut pairs = 0;
                for (ra, xa) in &s {
                    for (rb, xb) in &s {
                        let mut fits = *ra;
                        fits.set_bound(rb.bound());
                        if ra > rb || fits != *ra {
                            continue;
                        }
                        let got = I::rand_interval(ra, rb, &mut rd);
                        let (want, ret) = c::mp::rand_interval(xa, xb, Some(&mut xd));
                        assert_eq!(ret, -1);
                        same(&got, &want);
                        assert!(*ra <= got && got <= *rb);
                        let got = I::rand_interval(ra, rb, p.default_domain());
                        same(&got, &c::mp::rand_interval(xa, xb, None).0);
                        pairs += 1;
                    }
                }
                assert!(pairs > 200, "{pairs} pairs");
            }

            #[test]
            fn rand_interval_minm_m_matches_reference() {
                let mut p = seeded(20);
                let mut rd = p.domain(Separator::Keygen);
                let mut xd = c::mp::Domain::new(b"key").unwrap();
                let mut rng = Rng::new(21);
                let ms = [1, 2, 3, 100, 1 << 20, i32::MAX];
                let random = (0..20).map(|_| (rng.next_u32() >> 1).max(1) as i32);
                for m in ms.into_iter().chain(random) {
                    for _ in 0..5 {
                        let got = I::rand_interval_minm_m(m, &mut rd);
                        let (want, ret) = c::mp::rand_interval_minm_m(m, Some(&mut xd));
                        assert_eq!(ret, -1);
                        same(&got, &want);
                        assert!(got.cmp_i32(-m).is_ge() && got.cmp_i32(m).is_le());
                        let got = I::rand_interval_minm_m(m, p.default_domain());
                        same(&got, &c::mp::rand_interval_minm_m(m, None).0);
                    }
                }
            }
        }
    };
}

prng_suite!(p324_3, sqisign::gf::P324);
prng_suite!(p500_27, sqisign::gf::P500);
prng_suite!(p664_17, sqisign::gf::P664);
