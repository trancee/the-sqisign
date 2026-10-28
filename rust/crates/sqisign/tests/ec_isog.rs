//! Differential tests: Rust `ec` isogenies and normalisation against the C reference.

#[macro_use]
mod support;

macro_rules! ec_isog_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            use sqisign::ec::{ChangeCoordMatrix, EcParams, IsogEven};
            use sqisign::gf::FpParams;

            const T: usize = <$params as FpParams>::T as usize;

            #[test]
            fn e0_constants_match_reference() {
                let (px, qx, cof) = ce::e0_constants();
                assert_eq!(enc(&<$params as EcParams>::BASIS_E0_PX), px.encode());
                assert_eq!(enc(&<$params as EcParams>::BASIS_E0_QX), qx.encode());
                assert_eq!(<$params as FpParams>::C, cof);
            }

            #[test]
            fn four_isogenies_match_reference() {
                let mut rng = Rng::new(40);
                for _ in 0..super::support::SAMPLES {
                    let (p, cp) = point(&mut rng);
                    let (kps, b) = p.xisog_4();
                    let (ckps, cb) = ce::xisog_4(&cp);
                    assert_point(&b, &cb, "xisog_4");
                    let qs: Vec<_> = (0..3).map(|_| point(&mut rng)).collect();
                    let cqs: Vec<_> = qs.iter().map(|q| q.1).collect();
                    for ((q, _), cr) in qs.iter().zip(ce::xeval_4(&cqs, &ckps)) {
                        assert_point(&kps.eval(q), &cr, "xeval_4");
                    }
                }
            }

            /// Compares `codomain` with C `iso_isogeny_2chain`; returns success.
            fn check_chain(e: &Cv, kernel: &Pt, length: usize) -> Option<Cv> {
                let phi = IsogEven {
                    curve: *e,
                    kernel: *kernel,
                    length,
                };
                let r = phi.codomain();
                let cphi = ce::IsogEven {
                    curve: c_curve(e),
                    kernel: c_point(kernel),
                    length: length as u32,
                };
                let (c, fail) = ce::isogeny_2chain(&cphi);
                assert_eq!(r.is_none(), fail != 0, "2chain status, length {length}");
                if let Some(r) = &r {
                    assert_curve(r, &c, "2chain codomain");
                }
                r
            }

            #[test]
            fn isogeny_chains_match_reference() {
                let mut rng = Rng::new(41);
                let mut e = e0();
                let b = e.basis_2f_from_hint(T, 0);
                let mut ok = 0;
                for len in [0, 2, 4, 6, 8, 10, 16, 30, 64, T] {
                    let m = scalar(&mut rng);
                    let k = e.ladder3pt(&m, &b.p, &b.q, &b.pmq).expect("valid basis");
                    for k in [k, b.p, b.q, b.pmq] {
                        let k = e.dbl_iter(&k, T - len);
                        ok += usize::from(check_chain(&e, &k, len).is_some());
                        // Wrong orders are rejected (unless no check runs).
                        let k2 = e.dbl(&k);
                        check_chain(&e, &k2, len);
                    }
                }
                assert!(ok > 10, "too few valid chains: {ok}");
                for _ in 0..8 {
                    let (k, _) = point(&mut rng);
                    let (c, _) = curve(&mut rng);
                    check_chain(&c, &k, 6);
                }
            }

            #[test]
            #[should_panic(expected = "unsupported isogeny length")]
            fn odd_isogeny_length_panics() {
                let phi = IsogEven {
                    curve: e0(),
                    kernel: Pt::IDENTITY,
                    length: 3,
                };
                let _ = phi.codomain();
            }

            #[test]
            #[should_panic(expected = "unsupported isogeny length")]
            fn oversized_isogeny_length_panics() {
                let phi = IsogEven {
                    curve: e0(),
                    kernel: Pt::IDENTITY,
                    length: 0x1_0000,
                };
                let _ = phi.codomain();
            }

            #[test]
            fn isomorphisms_match_reference() {
                let mut rng = Rng::new(42);
                for _ in 0..super::support::SAMPLES {
                    let [a, b, cc, d] = [0; 4].map(|_| fe(&rand_fe(&mut rng)));
                    let m = ChangeCoordMatrix {
                        a: a.0,
                        b: b.0,
                        c: cc.0,
                        d: d.0,
                    };
                    let cm = ce::ChangeCoordMatrix {
                        a: a.1,
                        b: b.1,
                        c: cc.1,
                        d: d.1,
                    };
                    let (p, cp) = point(&mut rng);
                    assert_point(
                        &p.apply_isomorphism(&m),
                        &ce::apply_isomorphism(&cp, &cm),
                        "iso",
                    );
                }
            }

            /// Theta null points: random, special and degenerate.
            fn thetas(rng: &mut Rng) -> Vec<Pt> {
                let (one, zero, i) = (F2::ONE, F2::ZERO, F2::ONE.mul_by_i(0));
                let mut v = vec![
                    Pt::new(zero, zero),
                    Pt::new(zero, one),
                    Pt::new(one, zero),
                    Pt::new(one, one),
                    Pt::new(one, -one),
                    Pt::new(i, one),
                    Pt::new(-i, one),
                ];
                v.extend((0..super::support::SAMPLES).map(|_| point(rng).0));
                v
            }

            #[test]
            fn theta_to_montgomery_matches_reference() {
                let mut rng = Rng::new(43);
                let mut ok = 0;
                for th in thetas(&mut rng) {
                    let r = Cv::from_theta(&th);
                    let (c, cm, cok) = ce::theta_to_montgomery(&c_point(&th));
                    assert_eq!(r.is_some(), cok, "theta status");
                    let Some((e, m)) = r else { continue };
                    ok += 1;
                    assert_curve(&e, &c, "theta curve");
                    for (x, cx) in [(m.a, cm.a), (m.b, cm.b), (m.c, cm.c), (m.d, cm.d)] {
                        assert_eq!(enc(&x), cx.encode(), "theta matrix");
                    }
                }
                assert!(ok > super::support::SAMPLES / 2, "too few valid thetas");
            }

            #[test]
            fn normalize_montgomery_matches_reference() {
                let mut rng = Rng::new(44);
                for th in thetas(&mut rng) {
                    let (mut b, cb) = basis(&mut rng);
                    let r = Cv::normalize_montgomery(&th, Some(&mut b));
                    let (c, cb, cok) =
                        ce::normalize_montgomery(&c_curve(&e0()), &c_point(&th), Some(&cb));
                    assert_eq!(r.is_some(), cok, "normalize status");
                    if let Some(r) = r {
                        assert_curve(&r, &c, "normalize curve");
                        assert_basis(&b, &cb.expect("basis"), "normalize basis");
                    }
                    let (_, none, _) =
                        ce::normalize_montgomery(&c_curve(&e0()), &c_point(&th), None);
                    assert!(none.is_none());
                    assert_eq!(Cv::normalize_montgomery(&th, None).is_some(), cok);
                }
            }
        }
    };
}

ec_isog_suite!(p324_3, sqisign::gf::P324);
ec_isog_suite!(p500_27, sqisign::gf::P500);
ec_isog_suite!(p664_17, sqisign::gf::P664);
