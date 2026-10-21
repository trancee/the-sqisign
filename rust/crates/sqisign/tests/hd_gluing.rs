//! Differential tests: Rust `hd` gluing (2,2)-isogenies against the C reference.

#[macro_use]
mod support;

macro_rules! hd_gluing_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            hd_helpers!($module, $params);
            use super::support::SAMPLES;

            fn couple(rng: &mut Rng) -> Cp {
                Cp {
                    p1: point(rng).0,
                    p2: point(rng).0,
                }
            }

            fn bary(rng: &mut Rng) -> (BaryCoords<$params>, ce::Bary) {
                let b = BaryCoords {
                    u: rand_f2(rng),
                    v: rand_f2(rng),
                    w: rand_f2(rng),
                };
                let c = ce::Bary {
                    u: c_fe(&b.u),
                    v: c_fe(&b.v),
                    w: c_fe(&b.w),
                };
                (b, c)
            }

            /// Compares `Gluing::compute` with C; returns the Rust result.
            fn check_compute(
                e12: &Cc,
                k1: &Cp,
                k2: &Cp,
                dual: bool,
                verify: bool,
            ) -> Option<Gluing<$params>> {
                let r = Gluing::compute(e12, k1, k2, dual, verify);
                let (c, ok) =
                    ch::gluing_compute(&c_cc(e12), &c_couple(k1), &c_couple(k2), dual, verify);
                assert_eq!(
                    r.is_some(),
                    ok == 1,
                    "gluing flag (dual {dual}, verify {verify})"
                );
                if let Some(g) = &r {
                    assert_gluing(g, &c, "gluing");
                }
                r
            }

            #[test]
            fn change_of_coordinates_matches_reference() {
                let mut rng = Rng::new(90);
                for _ in 0..SAMPLES / 2 {
                    let (k1, k2, t) = (couple(&mut rng), couple(&mut rng), couple(&mut rng));
                    let m = GluingMatrix::from_four_torsion(&k1, &k2);
                    assert_matrix(
                        &m,
                        &ch::basis_compute(&c_couple(&k1), &c_couple(&k2)),
                        "basis",
                    );
                    let g = random_gluing(&mut rng);
                    let c = ch::couple_point_to_theta(&c_matrix(&g.m), &c_couple(&t));
                    assert_theta(&g.m.clone().couple_point_to_theta(&t), &c, "to theta");
                }
            }

            #[test]
            fn gluings_of_isotropic_kernels_match_reference() {
                let mut rng = Rng::new(91);
                let mut glued = 0;
                for n in [2, 5, 9] {
                    for det in [u64::MAX, 7] {
                        let (e12, ker, b) = isotropic_kernel(&mut rng, n, det);
                        let (k1, k2) = (ker.t1.dbl_iter(n - 1, &e12), ker.t2.dbl_iter(n - 1, &e12));
                        let special = [
                            Cp {
                                p1: b.p,
                                p2: Pt::IDENTITY,
                            },
                            Cp {
                                p1: Pt::IDENTITY,
                                p2: b.q,
                            },
                        ];
                        for (dual, verify) in
                            [(false, false), (true, false), (false, true), (true, true)]
                        {
                            let Some(g) = check_compute(&e12, &k1, &k2, dual, verify) else {
                                continue;
                            };
                            glued += 1;
                            for p in special.iter().copied().chain([couple(&mut rng)]) {
                                let r = g.eval_special(&p, dual);
                                let (c, ok) = ch::eval_special(&c_couple(&p), &c_gluing(&g), dual);
                                assert_eq!(r.is_some(), ok == 1, "special flag");
                                if let Some(r) = r {
                                    assert_theta(&r, &c, "special");
                                }
                            }
                        }
                        // Kernels of the wrong order or with dependent generators.
                        check_compute(&e12, &k1.dbl(&e12), &k2, false, true);
                        check_compute(&e12, &k1, &k1, false, true);
                        check_compute(&e12, &k1, &k2.dbl(&e12), false, false);
                    }
                }
                assert!(glued > 0, "no gluing succeeded");
            }

            /// Every pair of order-dividing-8 points against a genuine
            /// kernel point reaches each degenerate coordinate check.
            #[test]
            fn degenerate_gluings_match_reference() {
                let mut rng = Rng::new(94);
                for det in [u64::MAX, 7] {
                    let (e12, ker, b) = isotropic_kernel(&mut rng, 1, det);
                    let e = e0();
                    let pts: Vec<_> = (0..64)
                        .map(|i| {
                            e.biscalar_mul(&small_scalar(i / 8), &small_scalar(i % 8), 3, &b)
                                .expect("basis")
                        })
                        .collect();
                    for &p1 in &pts {
                        for &p2 in &pts {
                            let k = Cp { p1, p2 };
                            for g in [&ker.t1, &ker.t2] {
                                check_compute(&e12, &k, g, false, false);
                                check_compute(&e12, g, &k, false, false);
                            }
                        }
                    }
                }
            }

            #[test]
            fn random_gluings_match_reference() {
                let mut rng = Rng::new(92);
                for _ in 0..SAMPLES / 4 {
                    let e12 = Cc {
                        e1: curve(&mut rng).0,
                        e2: curve(&mut rng).0,
                    };
                    let (k1, k2) = (couple(&mut rng), couple(&mut rng));
                    for verify in [false, true] {
                        check_compute(&e12, &k1, &k2, false, verify);
                    }
                }
            }

            #[test]
            fn barycentric_evaluation_matches_reference() {
                let mut rng = Rng::new(93);
                for k in 0..SAMPLES {
                    let g = random_gluing(&mut rng);
                    let ((b1, c1), (b2, c2)) = (bary(&mut rng), bary(&mut rng));
                    let aux = theta(&mut rng);
                    let dual = k % 2 == 0;
                    let c = ch::eval_bary(&c1, &c2, &c_gluing(&g), &c_theta(&aux), dual);
                    assert_theta(&g.eval_bary(&b1, &b2, &aux, dual), &c, "bary");
                    // A random matrix is no gluing: special points are rejected.
                    let p = Cp {
                        p1: point(&mut rng).0,
                        p2: Pt::IDENTITY,
                    };
                    let r = g.clone().eval_special(&p, dual);
                    let (c, ok) = ch::eval_special(&c_couple(&p), &c_gluing(&g), dual);
                    assert_eq!(r.is_some(), ok == 1, "special flag");
                    if let Some(r) = r {
                        assert_theta(&r, &c, "special");
                    }
                }
            }
        }
    };
}

hd_gluing_suite!(p324_3, sqisign::gf::P324);
hd_gluing_suite!(p500_27, sqisign::gf::P500);
hd_gluing_suite!(p664_17, sqisign::gf::P664);
