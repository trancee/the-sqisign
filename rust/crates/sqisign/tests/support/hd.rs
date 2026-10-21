//! Paired Rust / C reference values for the `hd` differential suites.

#![allow(unused_macros)]

/// Defines `hd` helpers for one parameter set; expects `ec_helpers!` first.
macro_rules! hd_helpers {
    ($module:ident, $params:ty) => {
        use sqisign::hd::{
            CoupleCurve, CouplePoint, Gluing, GluingMatrix, KernelCouplePoints, ThetaPoint,
            ThetaStructure,
        };
        use sqisign_reference::$module::hd as ch;

        type Th = ThetaPoint<$params>;
        type Cp = CouplePoint<$params>;
        type Cc = CoupleCurve<$params>;
        type Kr = KernelCouplePoints<$params>;

        use sqisign::gf::FpParams;

        const T: usize = <$params as FpParams>::T as usize;

        /// `E0 x E0`, the basis `P, Q` of `E0[2^(n+2)]` and the kernel
        /// `{(R, M R)}` for `M = [[a, b], [c, d]]` with `det M = det`; `-1` is
        /// maximal isotropic but does not split in general; `det = 7` keeps
        /// it isotropic only modulo 8, so the gluing succeeds and later
        /// steps see an inconsistent kernel.
        fn isotropic_kernel(rng: &mut Rng, n: usize, det: u64) -> (Cc, Kr, Bs) {
            let e = n + 2;
            let mask = (1u64 << e) - 1;
            let mut e0c = e0();
            let full = e0c.basis_2f_from_hint(T, 0);
            let b = e0c.dbl_iter_basis(&full, T - e);
            let (a, bb, c) = (rng.next_u64() | 1, rng.next_u64(), rng.next_u64());
            let a_inv = (0..64).fold(1u64, |x, _| {
                x.wrapping_mul(2u64.wrapping_sub(a.wrapping_mul(x)))
            });
            let d = bb.wrapping_mul(c).wrapping_add(det).wrapping_mul(a_inv);
            let mul = |k: u64, l: u64| {
                e0c.biscalar_mul(&small_scalar(k & mask), &small_scalar(l & mask), e, &b)
                    .expect("basis")
            };
            let b2 = Bs {
                p: mul(a, c),
                q: mul(bb, d),
                pmq: Pt::IDENTITY,
            };
            (Cc { e1: e0(), e2: e0() }, Kr::from_bases(&b, &b2), b)
        }

        fn rand_f2(rng: &mut Rng) -> F2 {
            fe(&rand_fe(rng)).0
        }

        fn theta(rng: &mut Rng) -> Th {
            Th::new(rand_f2(rng), rand_f2(rng), rand_f2(rng), rand_f2(rng))
        }

        fn c_theta(p: &Th) -> ch::ThetaPoint {
            ch::ThetaPoint {
                x: c_fe(&p.x),
                y: c_fe(&p.y),
                z: c_fe(&p.z),
                t: c_fe(&p.t),
            }
        }

        fn assert_theta(r: &Th, c: &ch::ThetaPoint, what: &str) {
            assert_eq!(enc(&r.x), c.x.encode(), "{what}: x");
            assert_eq!(enc(&r.y), c.y.encode(), "{what}: y");
            assert_eq!(enc(&r.z), c.z.encode(), "{what}: z");
            assert_eq!(enc(&r.t), c.t.encode(), "{what}: t");
        }

        fn c_couple(p: &Cp) -> ch::CouplePoint {
            ch::CouplePoint {
                p1: c_point(&p.p1),
                p2: c_point(&p.p2),
            }
        }

        fn assert_couple(r: &Cp, c: &ch::CouplePoint, what: &str) {
            assert_point(&r.p1, &c.p1, what);
            assert_point(&r.p2, &c.p2, what);
        }

        fn c_cc(e: &Cc) -> ch::CoupleCurve {
            ch::CoupleCurve {
                e1: c_curve(&e.e1),
                e2: c_curve(&e.e2),
            }
        }

        fn c_kernel(k: &Kr) -> ch::Kernel {
            ch::Kernel {
                t1: c_couple(&k.t1),
                t2: c_couple(&k.t2),
            }
        }

        fn c_structure(a: &ThetaStructure<$params>) -> ch::ThetaStructure {
            ch::ThetaStructure {
                inv_dual_null_point: c_theta(&a.inv_dual_null_point),
                dbl_data: c_theta(&a.dbl_data),
                inv_sqr_null_point: c_theta(&a.inv_sqr_null_point),
                precomputation: a.precomputation,
            }
        }

        fn assert_structure(r: &ThetaStructure<$params>, c: &ch::ThetaStructure, what: &str) {
            assert_theta(&r.inv_dual_null_point, &c.inv_dual_null_point, what);
            assert_theta(&r.dbl_data, &c.dbl_data, what);
            assert_theta(&r.inv_sqr_null_point, &c.inv_sqr_null_point, what);
            assert_eq!(r.precomputation, c.precomputation, "{what}: flag");
        }

        fn c_matrix(m: &GluingMatrix<$params>) -> ch::GluingMatrix {
            ch::GluingMatrix {
                m: m.m.map(|r| r.map(|v| c_fe(&v))),
            }
        }

        fn assert_matrix(r: &GluingMatrix<$params>, c: &ch::GluingMatrix, what: &str) {
            for (rr, cr) in r.m.iter().zip(&c.m) {
                for (rv, cv) in rr.iter().zip(cr) {
                    assert_eq!(enc(rv), cv.encode(), "{what}");
                }
            }
        }

        fn c_gluing(g: &Gluing<$params>) -> ch::Gluing {
            ch::Gluing {
                inv_image_k1_8: c_theta(&g.inv_image_k1_8),
                inv_image_k2_8: c_theta(&g.inv_image_k2_8),
                m: c_matrix(&g.m),
                inv_dual_theta_null: c_theta(&g.inv_dual_theta_null),
                codomain: c_theta(&g.codomain),
            }
        }

        fn assert_gluing(r: &Gluing<$params>, c: &ch::Gluing, what: &str) {
            assert_theta(&r.inv_image_k1_8, &c.inv_image_k1_8, what);
            assert_theta(&r.inv_image_k2_8, &c.inv_image_k2_8, what);
            assert_matrix(&r.m, &c.m, what);
            assert_theta(&r.inv_dual_theta_null, &c.inv_dual_theta_null, what);
            assert_theta(&r.codomain, &c.codomain, what);
        }

        fn random_gluing(rng: &mut Rng) -> Gluing<$params> {
            Gluing {
                inv_image_k1_8: theta(rng),
                inv_image_k2_8: theta(rng),
                m: GluingMatrix {
                    m: core::array::from_fn(|_| core::array::from_fn(|_| rand_f2(rng))),
                },
                inv_dual_theta_null: theta(rng),
                codomain: theta(rng),
            }
        }
    };
}

/// `check_chain`: runs a Rust chain in a C mode and compares with C.
macro_rules! hd_chain_checks {
    () => {
        /// Runs the Rust chain in `mode` (C numbering) and compares with C.
        fn check_chain(mode: i8, n: usize, e12: &Cc, ker: &Kr, pts: &[Cp]) -> Option<Cc> {
            let mut r12 = *e12;
            let mut rp = pts.to_vec();
            let out = match mode {
                0 => r12.chain_compute_and_eval(n, ker, &mut rp),
                1 => r12.chain_compute_and_eval_e1(n, ker, &mut rp).map(|e1| Cc {
                    e1,
                    e2: Cv::default(),
                }),
                2 => r12.chain_compute_and_eval_e2(n, ker, &mut rp).map(|e2| Cc {
                    e1: Cv::default(),
                    e2,
                }),
                _ => r12
                    .chain_compute_and_eval_verify(n, ker, &mut rp)
                    .map(|e1| Cc {
                        e1,
                        e2: Cv::default(),
                    }),
            };
            let cpts: Vec<_> = pts.iter().map(c_couple).collect();
            let (ok, c12, c34, cp) = ch::chain(mode, n, &c_cc(e12), &c_kernel(ker), &cpts);
            assert_eq!(out.is_some(), ok == 1, "chain flag, mode {mode}");
            assert_curve(&r12.e1, &c12.e1, "domain e1");
            assert_curve(&r12.e2, &c12.e2, "domain e2");
            if let Some(e34) = out {
                if mode != 2 {
                    assert_curve(&e34.e1, &c34.e1, "codomain e1");
                }
                if mode == 0 || mode == 2 {
                    assert_curve(&e34.e2, &c34.e2, "codomain e2");
                }
                for (r, c) in rp.iter().zip(&cp) {
                    assert_couple(r, c, "pushed point");
                }
            }
            out
        }
    };
}
