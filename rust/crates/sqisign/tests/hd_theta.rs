//! Differential tests: Rust `hd` theta arithmetic, gluing and splitting
//! internals against the C reference.

#[macro_use]
mod support;

macro_rules! hd_theta_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            hd_helpers!($module, $params);
            use super::support::SAMPLES;
            use sqisign::hd::{
                check_valid_theta_null_point, gluing_ladder, tensor_product_to_couple_point,
            };

            fn structure(rng: &mut Rng, precomputation: bool) -> ThetaStructure<$params> {
                ThetaStructure {
                    inv_dual_null_point: theta(rng),
                    dbl_data: theta(rng),
                    inv_sqr_null_point: theta(rng),
                    precomputation,
                }
            }

            /// `(x, y, z, x y / z)`: a product theta point.
            fn product(rng: &mut Rng) -> Th {
                let (x, y, z) = (rand_f2(rng), rand_f2(rng), rand_f2(rng));
                let t = x * y * z.inv();
                Th::new(x * z, y * z, z * z, t * z)
            }

            #[test]
            fn theta_doubling_matches_reference() {
                let mut rng = Rng::new(80);
                for k in 0..SAMPLES {
                    let a = structure(&mut rng, k % 2 == 0);
                    let p = theta(&mut rng);
                    let mut r = a.clone();
                    let rp = r.dbl(&p);
                    let (cp, ca) = ch::theta_dbl(&c_structure(&a), &c_theta(&p));
                    assert_theta(&rp, &cp, "dbl");
                    assert_structure(&r, &ca, "dbl structure");
                    for n in [0, 1, 5] {
                        let mut r = a;
                        let rp = r.dbl_iter(&p, n);
                        let (cp, ca) = ch::theta_dbl_iter(&c_structure(&a), &c_theta(&p), n);
                        assert_theta(&rp, &cp, "dbl_iter");
                        assert_structure(&r, &ca, "dbl_iter structure");
                    }
                    assert_theta(
                        &a.dual_null_point(),
                        &ch::dual_null_point(&c_structure(&a)),
                        "dual null",
                    );
                    let mut r = a;
                    r.precompute();
                    assert_structure(&r, &ch::precompute(&c_structure(&a)), "precompute");
                }
            }

            #[test]
            fn product_detection_and_splitting_match_reference() {
                let mut rng = Rng::new(81);
                let zero = Th::new(F2::ZERO, F2::ZERO, F2::ZERO, F2::ZERO);
                let mut cases = vec![zero];
                for _ in 0..SAMPLES {
                    cases.push(product(&mut rng));
                    cases.push(theta(&mut rng));
                }
                for p in cases {
                    let flag = p.is_product();
                    assert_eq!(flag, ch::is_product(&c_theta(&p)), "is_product");
                    let (cp, ok) = ch::tensor_product(&c_theta(&p));
                    let r = tensor_product_to_couple_point(&p);
                    assert_eq!(r.is_some(), ok, "tensor flag");
                    assert_eq!(r.is_some(), flag != 0, "tensor flag vs mask");
                    if let Some(r) = r {
                        assert_couple(&r, &cp, "tensor product");
                    }
                }
                let i = F2::ONE.mul_by_i(1);
                let mut nulls = vec![
                    Pt::new(F2::ZERO, F2::ONE),
                    Pt::new(F2::ONE, F2::ZERO),
                    Pt::new(F2::ONE, F2::ONE),
                    Pt::new(F2::ONE, -F2::ONE),
                    Pt::new(F2::ONE, i),
                ];
                nulls.extend((0..8).map(|_| point(&mut rng).0));
                for th in nulls {
                    assert_eq!(
                        check_valid_theta_null_point(&th),
                        ch::check_valid_null(&c_point(&th)),
                        "null"
                    );
                }
            }

            #[test]
            fn couple_points_match_reference() {
                let mut rng = Rng::new(82);
                for _ in 0..SAMPLES / 4 {
                    let e12 = Cc {
                        e1: curve(&mut rng).0,
                        e2: curve(&mut rng).0,
                    };
                    let p = Cp {
                        p1: point(&mut rng).0,
                        p2: point(&mut rng).0,
                    };
                    assert_couple(
                        &p.dbl(&e12),
                        &ch::dbl_couple(&c_couple(&p), &c_cc(&e12)),
                        "dbl",
                    );
                    for n in [0, 1, 7] {
                        let c = ch::dbl_couple_iter(&c_couple(&p), n, &c_cc(&e12));
                        assert_couple(&p.dbl_iter(n, &e12), &c, "dbl_iter");
                    }
                    let (b1, b2) = (basis(&mut rng).0, basis(&mut rng).0);
                    let k = Kr::from_bases(&b1, &b2);
                    let ck = ch::bases_to_kernel(&c_basis(&b1), &c_basis(&b2));
                    assert_couple(&k.t1, &ck.t1, "kernel T1");
                    assert_couple(&k.t2, &ck.t2, "kernel T2");
                }
            }

            #[test]
            fn gluing_ladder_matches_reference() {
                let mut rng = Rng::new(83);
                for _ in 0..SAMPLES / 4 {
                    let mut e = curve(&mut rng).0;
                    e.normalize_a24();
                    let (p, q) = (point(&mut rng).0, point(&mut rng).0);
                    let pmq = Pt::new(rand_f2(&mut rng), F2::ONE);
                    for pow2 in [0, 1, 9] {
                        let (r1, r0) = gluing_ladder(&p, &q, &pmq, pow2, &e);
                        let (c1, c0) = ch::gluing_ladder(
                            &c_point(&p),
                            &c_point(&q),
                            &c_point(&pmq),
                            pow2,
                            &c_curve(&e),
                        );
                        assert_point(&r1, &c1, "ladder R1");
                        assert_point(&r0, &c0, "ladder R0");
                    }
                }
            }
        }
    };
}

hd_theta_suite!(p324_3, sqisign::gf::P324);
hd_theta_suite!(p500_27, sqisign::gf::P500);
hd_theta_suite!(p664_17, sqisign::gf::P664);
