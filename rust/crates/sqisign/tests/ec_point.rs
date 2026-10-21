//! Differential tests: Rust `ec` points and curves against the vendor C reference.

#[macro_use]
mod support;

macro_rules! ec_point_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            use super::support::SAMPLES;

            /// Random points plus the identity, `(0 : 1)` and `(1 : 1)`.
            fn points(seed: u64) -> Vec<(Pt, ce::Point)> {
                let mut rng = Rng::new(seed);
                let (zero, one) = (vec![0u8; BYTES], enc(&F2::ONE));
                let mut v = vec![
                    point_from(&one, &zero),
                    point_from(&zero, &one),
                    point_from(&one, &one),
                ];
                v.extend((0..SAMPLES).map(|_| point(&mut rng)));
                v
            }

            #[test]
            fn predicates_match_reference() {
                let ps = points(10);
                for (r, c) in &ps {
                    assert_eq!(r.is_zero(), ce::is_zero(c), "is_zero");
                    assert_eq!(r.has_zero_coordinate(), ce::has_zero_coordinate(c));
                    for (r2, c2) in ps.iter().step_by(7) {
                        assert_eq!(r.ct_eq(r2), ce::is_equal(c, c2), "is_equal");
                    }
                    // A scaled copy is the same projective point.
                    let k = F2::from_small(7);
                    let s = Pt::new(r.x * k, r.z * k);
                    let cs = ce::Point {
                        x: c.x.mul_small(7),
                        z: c.z.mul_small(7),
                    };
                    assert_eq!(r.ct_eq(&s), ce::is_equal(c, &cs), "is_equal scaled");
                    assert_ne!(r.ct_eq(&s), 0);
                }
            }

            #[test]
            fn formulas_match_reference() {
                let ps = points(11);
                let mut rng = Rng::new(12);
                for w in ps.windows(3) {
                    let ((p, cp), (q, cq), (d, cd)) = (&w[0], &w[1], &w[2]);
                    assert_point(&p.xdbl_e0(), &ce::xdbl_e0(cp), "xdbl_e0");
                    assert_point(&p.xdbl(q), &ce::xdbl(cp, cq), "xdbl");
                    for n in [false, true] {
                        assert_point(&p.xdbl_a24(q, n), &ce::xdbl_a24(cp, cq, n), "xdbl_a24");
                    }
                    assert_point(&p.xadd(q, d), &ce::xadd(cp, cq, cd), "xadd");
                    let (a24, ca24) = point(&mut rng);
                    let (r, s) = p.xdbladd(q, d, &a24);
                    let (cr, cs) = ce::xdbladd(cp, cq, cd, &ca24);
                    assert_point(&r, &cr, "xdbladd 2P");
                    assert_point(&s, &cs, "xdbladd P+Q");
                    let b = BaryCoords::from_points(p, q, d);
                    let cb = ce::bary(cp, cq, cd);
                    let got = [enc(&b.u), enc(&b.v), enc(&b.w)].concat();
                    assert_eq!(got, [cb.u.encode(), cb.v.encode(), cb.w.encode()].concat());
                    if p.z.is_zero() == 0 {
                        assert_point(&p.normalize(), &ce::normalize_point(cp), "normalize");
                    }
                }
            }

            #[test]
            fn select_and_cswap_follow_the_mask() {
                let ps = points(13);
                let ((a, _), (b, _)) = (&ps[3], &ps[4]);
                assert_eq!(penc(&Pt::select(a, b, 0)), penc(a));
                assert_eq!(penc(&Pt::select(a, b, u32::MAX)), penc(b));
                let (mut x, mut y) = (*a, *b);
                Pt::cswap(&mut x, &mut y, 0);
                assert_eq!((penc(&x), penc(&y)), (penc(a), penc(b)));
                Pt::cswap(&mut x, &mut y, u32::MAX);
                assert_eq!((penc(&x), penc(&y)), (penc(b), penc(a)));
            }

            #[test]
            fn curve_validity_matches_reference() {
                let two = F2::from_small(2);
                let mut rng = Rng::new(14);
                let mut inputs = vec![enc(&two), enc(&-two), enc(&F2::ZERO)];
                inputs.extend((0..SAMPLES).map(|_| rand_fe(&mut rng)));
                for a in &inputs {
                    let (ra, ca) = fe(a);
                    let ok = ce::curve_verify_a(&ca);
                    assert_eq!(Cv::verify_a(&ra), ok == 1, "verify_a");
                    let (cv, cok) = ce::curve_init_from_a(&ca);
                    assert_eq!(cok, ok);
                    match Cv::from_a(&ra) {
                        Some(e) => assert_curve(&e, &cv, "from_a"),
                        None => assert_eq!(cok, 0),
                    }
                }
                assert_curve(&Cv::default(), &ce::curve_init(), "init");
            }

            #[test]
            fn normalisations_match_reference() {
                let mut rng = Rng::new(15);
                let one = enc(&F2::ONE);
                for i in 0..SAMPLES {
                    let (mut r, c) = if i % 4 == 0 {
                        curve_from(&rand_fe(&mut rng), &one)
                    } else {
                        curve(&mut rng)
                    };
                    let mut e = r;
                    e.normalize();
                    assert_curve(&e, &ce::normalize_curve(&c), "normalize");
                    let mut e = r;
                    e.normalize_a24();
                    let c24 = ce::curve_normalize_a24(&c);
                    assert_curve(&e, &c24, "normalize_a24");
                    e.normalize_a24();
                    assert_curve(&e, &c24, "normalize_a24 idempotent");
                    r.normalize_with_a24();
                    let cn = ce::normalize_curve_and_a24(&c);
                    assert_curve(&r, &cn, "normalize_with_a24");
                    r.normalize_with_a24();
                    assert_curve(&r, &cn, "normalize_with_a24 idempotent");
                }
            }

            #[test]
            fn torsion_tests_match_reference() {
                let mut rng = Rng::new(16);
                let (zero, one) = (vec![0u8; BYTES], enc(&F2::ONE));
                for _ in 0..SAMPLES {
                    // x1 and 1/x1 are the roots of x^2 + A x + 1 for A = -(x1 + 1/x1).
                    let (x1, _) = fe(&rand_fe(&mut rng));
                    let x2 = x1.inv();
                    let ra = -(x1 + x2);
                    let (r, c) = curve_from(&enc(&ra), &one);
                    let mut cases = vec![
                        point_from(&enc(&x1), &one),
                        point_from(&enc(&x2), &one),
                        point_from(&zero, &one),
                        point_from(&one, &one),
                        point_from(&one, &zero),
                        point(&mut rng),
                    ];
                    cases.push(point(&mut rng));
                    let (mut rn, cn) = (r, ce::curve_normalize_a24(&c));
                    rn.normalize_a24();
                    for (p, cp) in &cases {
                        assert_eq!(r.is_two_torsion(p), ce::is_two_torsion(cp, &c), "2-torsion");
                        assert_eq!(r.is_four_torsion(p), ce::is_four_torsion(cp, &c), "4-t");
                        assert_eq!(rn.is_four_torsion(p), ce::is_four_torsion(cp, &cn), "4-t n");
                    }
                    assert_ne!(r.is_two_torsion(&cases[0].0), 0);
                    assert_ne!(rn.is_four_torsion(&cases[3].0), 0);
                }
            }
        }
    };
}

ec_point_suite!(p324_3, sqisign::gf::P324);
ec_point_suite!(p500_27, sqisign::gf::P500);
ec_point_suite!(p664_17, sqisign::gf::P664);
