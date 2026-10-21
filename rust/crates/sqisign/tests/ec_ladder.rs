//! Differential tests: Rust `ec` doubling chains and ladders against the C reference.

#[macro_use]
mod support;

macro_rules! ec_ladder_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);

            /// A random curve, its `A24`-normalised twin and the `E0` curve.
            fn curves(rng: &mut Rng) -> Vec<(Cv, ce::Curve)> {
                let (r, c) = curve(rng);
                let (mut rn, cn) = (r, ce::curve_normalize_a24(&c));
                rn.normalize_a24();
                let zero = vec![0u8; BYTES];
                let (mut r0, c0) = curve_from(&zero, &enc(&F2::ONE));
                let c0n = ce::normalize_curve_and_a24(&c0);
                r0.normalize_with_a24();
                vec![
                    (r, c),
                    (rn, cn),
                    curve_from(&zero, &enc(&F2::ONE)),
                    (r0, c0n),
                ]
            }

            const KBITS: [usize; 8] = [0, 1, 2, 7, 50, 51, 64, 129];

            fn all_kbits() -> Vec<usize> {
                let mut v = KBITS.to_vec();
                v.push(64 * WORDS - 1);
                v.push(64 * WORDS);
                v
            }

            #[test]
            fn doubling_chains_match_reference() {
                let mut rng = Rng::new(20);
                for _ in 0..8 {
                    let (b, cb) = basis(&mut rng);
                    for (e, c) in curves(&mut rng) {
                        assert_point(&e.dbl(&b.p), &ce::dbl(&cb.p, &c), "dbl");
                        for n in [0, 1, 7, 50, 51] {
                            let mut e2 = e;
                            let r = e2.dbl_iter(&b.p, n);
                            let (cr, c2) = ce::dbl_iter(&cb.p, n as i32, &c);
                            assert_point(&r, &cr, "dbl_iter");
                            assert_curve(&e2, &c2, "dbl_iter curve");
                        }
                        let mut e2 = e;
                        let rb = e2.dbl_iter_basis(&b, 3);
                        let (crb, _) = ce::dbl_iter_basis(&cb, 3, &c);
                        assert_point(&rb.p, &crb.p, "basis P");
                        assert_point(&rb.q, &crb.q, "basis Q");
                        assert_point(&rb.pmq, &crb.pmq, "basis PmQ");
                    }
                }
            }

            #[test]
            fn scalar_multiplication_matches_reference() {
                let mut rng = Rng::new(21);
                for kbits in all_kbits() {
                    let (p, cp) = point(&mut rng);
                    let k = scalar(&mut rng);
                    for (e, c) in curves(&mut rng) {
                        let mut e2 = e;
                        let r = e2.mul(&k, kbits, &p);
                        let (cr, c2) = ce::mul(&k, kbits, &cp, &c);
                        assert_point(&r, &cr, "mul");
                        assert_curve(&e2, &c2, "mul curve");
                    }
                }
            }

            #[test]
            fn three_point_ladder_matches_reference() {
                let mut rng = Rng::new(22);
                for _ in 0..8 {
                    let (b, cb) = basis(&mut rng);
                    let m = scalar(&mut rng);
                    for (e, c) in curves(&mut rng) {
                        let r = e.ladder3pt(&m, &b.p, &b.q, &b.pmq);
                        let (cr, ok) = ce::ladder3pt(&m, &cb.p, &cb.q, &cb.pmq, &c);
                        assert_eq!(r.is_some(), ok == 1, "ladder3pt status");
                        if let Some(r) = r {
                            assert_point(&r, &cr, "ladder3pt");
                        }
                        // P - Q with a zero coordinate is rejected.
                        let bad = Pt::new(F2::ZERO, F2::ONE);
                        assert!(e.ladder3pt(&m, &b.p, &b.q, &bad).is_none());
                    }
                }
            }

            /// Bases hitting every degenerate branch of the biladders.
            fn bases(rng: &mut Rng) -> Vec<(Bs, ce::Basis)> {
                let (zero, one) = (vec![0u8; BYTES], enc(&F2::ONE));
                let (x, _) = fe(&rand_fe(rng));
                let mut v: Vec<_> = (0..4).map(|_| basis(rng)).collect();
                let mut with = |p: (Vec<u8>, Vec<u8>), q: (Vec<u8>, Vec<u8>), rng: &mut Rng| {
                    let (rp, cp) = point_from(&p.0, &p.1);
                    let (rq, cq) = point_from(&q.0, &q.1);
                    let (rd, cd) = point(rng);
                    v.push((
                        Bs {
                            p: rp,
                            q: rq,
                            pmq: rd,
                        },
                        ce::Basis {
                            p: cp,
                            q: cq,
                            pmq: cd,
                        },
                    ));
                };
                // P + Q = (0 : *): Px Qx = Pz Qz.
                with((enc(&x), one.clone()), (enc(&x.inv()), one.clone()), rng);
                // (1 : 1) doubles to (0 : *).
                with((one.clone(), one.clone()), (rand_fe(rng), one.clone()), rng);
                with((rand_fe(rng), one.clone()), (one.clone(), one.clone()), rng);
                // Zero coordinates in the input.
                with(
                    (zero.clone(), one.clone()),
                    (rand_fe(rng), one.clone()),
                    rng,
                );
                with(
                    (rand_fe(rng), one.clone()),
                    (zero.clone(), one.clone()),
                    rng,
                );
                let (mut b, mut cb) = basis(rng);
                (b.pmq.x, cb.pmq.x) = fe(&zero);
                v.push((b, cb));
                (b.pmq.z, cb.pmq.z) = fe(&zero);
                v.push((b, cb));
                v
            }

            fn scalars(rng: &mut Rng) -> Vec<(Vec<u64>, Vec<u64>)> {
                let s = small_scalar;
                let mut v = vec![
                    (s(0), s(0)),
                    (s(1), s(1)),
                    (s(1), s(5)),
                    (s(1), s(4)),
                    (s(2), s(9)),
                    (s(0), s(2)),
                    (s(0), s(3)),
                    (s(5), s(1)),
                    (s(2), s(4)),
                    (s(3), s(0)),
                ];
                v.extend((0..4).map(|_| (scalar(rng), scalar(rng))));
                v
            }

            #[test]
            fn biscalar_multiplication_matches_reference() {
                let mut rng = Rng::new(23);
                for kbits in all_kbits().into_iter().filter(|&k| k > 0) {
                    let bs = bases(&mut rng);
                    let ks = scalars(&mut rng);
                    for (e, c) in curves(&mut rng) {
                        for (b, cb) in &bs {
                            for (k, l) in &ks {
                                let r = e.biscalar_mul(k, l, kbits, b);
                                let (cr, ok) = ce::biscalar_mul(k, l, kbits, cb, &c);
                                assert_eq!(r.is_some(), ok == 1, "biscalar status {kbits}");
                                if let Some(r) = r {
                                    assert_point(&r, &cr, "biscalar_mul");
                                }
                                let mut e2 = e;
                                let r = e2.biscalar_mul_verif(k, l, kbits, b);
                                let (cr, ok, c2) = ce::biscalar_mul_verif(k, l, kbits, cb, &c);
                                assert_eq!(r.is_some(), ok == 1, "verif status {kbits}");
                                if let Some(r) = r {
                                    assert_point(&r, &cr, "biscalar_mul_verif");
                                }
                                assert_curve(&e2, &c2, "verif curve");
                            }
                        }
                    }
                }
            }

            #[test]
            fn biscalar_one_bit_uses_two_torsion() {
                let mut rng = Rng::new(24);
                let (x1, _) = fe(&rand_fe(&mut rng));
                let ra = -(x1 + x1.inv());
                let (one, zero) = (enc(&F2::ONE), vec![0u8; BYTES]);
                let (p, cp) = point_from(&enc(&x1), &one);
                let (q, cq) = point_from(&enc(&x1.inv()), &one);
                let (d, cd) = point_from(&zero, &one);
                let (e, c) = curve_from(&enc(&ra), &one);
                let b = Bs { p, q, pmq: d };
                let cb = ce::Basis {
                    p: cp,
                    q: cq,
                    pmq: cd,
                };
                for (k, l) in [(0, 0), (0, 1), (1, 0), (1, 1)] {
                    let (k, l) = (small_scalar(k), small_scalar(l));
                    let r = e.biscalar_mul(&k, &l, 1, &b).expect("2-torsion basis");
                    let (cr, ok) = ce::biscalar_mul(&k, &l, 1, &cb, &c);
                    assert_eq!(ok, 1);
                    assert_point(&r, &cr, "one-bit biscalar");
                }
            }
        }
    };
}

ec_ladder_suite!(p324_3, sqisign::gf::P324);
ec_ladder_suite!(p500_27, sqisign::gf::P500);
ec_ladder_suite!(p664_17, sqisign::gf::P664);
