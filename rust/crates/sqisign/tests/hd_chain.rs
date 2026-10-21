//! Differential tests: Rust `hd` isogeny chains against the C reference.

#[macro_use]
mod support;

macro_rules! hd_chain_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            hd_helpers!($module, $params);
            hd_chain_checks!();
            /// Whether the gluing succeeds, so that the C chain is defined.
            fn glues(e12: &Cc, ker: &Kr, n: usize, verify: bool) -> bool {
                let k1 = ker.t1.dbl_iter(n - 1, e12);
                let k2 = ker.t2.dbl_iter(n - 1, e12);
                Gluing::compute(e12, &k1, &k2, false, verify).is_some()
            }

            #[test]
            fn isotropic_chains_match_reference() {
                let mut rng = Rng::new(71);
                let mut ran = 0;
                for n in [2, 3, 4, 7, 15, 16, 17, 30, 33, 40] {
                    for det in [u64::MAX, u64::MAX, 7, 7] {
                        let (e12, ker, b) = isotropic_kernel(&mut rng, n, det);
                        let pts = [
                            Cp {
                                p1: b.p,
                                p2: Pt::IDENTITY,
                            },
                            Cp {
                                p1: Pt::IDENTITY,
                                p2: b.q,
                            },
                        ];
                        for mode in [0, 1, 2, -1] {
                            if glues(&e12, &ker, n, mode == -1) {
                                check_chain(mode, n, &e12, &ker, &pts);
                                ran += 1;
                            }
                        }
                    }
                }
                assert!(ran > 0, "no defined chain was exercised");
            }

            /// C reads uninitialised memory for `n < 2`; the port refuses.
            #[test]
            fn chains_shorter_than_two_fail() {
                let (mut e12, ker, _) = isotropic_kernel(&mut Rng::new(72), 2, u64::MAX);
                for n in [0, 1] {
                    assert!(e12.chain_compute_and_eval(n, &ker, &mut []).is_none());
                    assert!(e12.chain_compute_and_eval_e1(n, &ker, &mut []).is_none());
                    assert!(e12.chain_compute_and_eval_e2(n, &ker, &mut []).is_none());
                    assert!(e12
                        .chain_compute_and_eval_verify(n, &ker, &mut [])
                        .is_none());
                }
            }

            /// C runs on uninitialised memory when the gluing fails; the port
            /// reports the failure (verification rejects such kernels).
            #[test]
            fn chains_with_malformed_gluing_fail() {
                let mut rng = Rng::new(73);
                let (e12, ker, b) = isotropic_kernel(&mut rng, 5, u64::MAX);
                let dependent = Kr {
                    t1: ker.t1,
                    t2: ker.t1,
                };
                let short = Kr {
                    t1: ker.t1.dbl(&e12),
                    t2: ker.t2.dbl(&e12),
                };
                let pts = [Cp { p1: b.p, p2: b.q }];
                for bad in [dependent, short] {
                    let mut r12 = e12;
                    assert!(r12
                        .chain_compute_and_eval_verify(5, &bad, &mut [])
                        .is_none());
                }
                let mut r12 = e12;
                assert!(
                    r12.chain_compute_and_eval(5, &ker, &mut pts.clone())
                        .is_none(),
                    "non-special point"
                );
            }
        }
    };
}

hd_chain_suite!(p324_3, sqisign::gf::P324);
hd_chain_suite!(p500_27, sqisign::gf::P500);
hd_chain_suite!(p664_17, sqisign::gf::P664);
