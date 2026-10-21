//! Differential tests: Rust `ec` torsion bases and pairings against the C reference.

#[macro_use]
mod support;

macro_rules! ec_basis_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            use super::support::SAMPLES;
            use sqisign::ec::IsogEven;
            use sqisign::gf::FpParams;

            const T: usize = <$params as FpParams>::T as usize;

            /// `E0`, two supersingular codomains of `E0` and two random curves.
            fn curves(rng: &mut Rng) -> Vec<Cv> {
                let mut e = e0();
                let b = e.basis_2f_from_hint(T, 0);
                let mut v = vec![e];
                for _ in 0..2 {
                    let k = e
                        .ladder3pt(&scalar(rng), &b.p, &b.q, &b.pmq)
                        .expect("basis");
                    let phi = IsogEven {
                        curve: e,
                        kernel: k,
                        length: T,
                    };
                    v.push(phi.codomain().expect("valid kernel"));
                }
                v.extend((0..2).map(|_| curve(rng).0));
                v
            }

            /// Compares `basis_2f_from_hint` with C; returns the Rust basis.
            fn check_from_hint(e: &Cv, f: usize, hint: u8) -> Bs {
                let mut r = *e;
                let b = r.basis_2f_from_hint(f, hint);
                let (cb, ok, c) = ce::basis_2f_from_hint(&c_curve(e), f, hint);
                assert_eq!(ok, 1, "from_hint never fails without debug checks");
                assert_basis(&b, &cb, "from_hint");
                assert_curve(&r, &c, "from_hint curve");
                b
            }

            #[test]
            fn hinted_bases_match_reference() {
                let mut rng = Rng::new(50);
                for e in curves(&mut rng) {
                    for (f, ee) in [(T, 0), (T, 1), (T, T / 2), (T, T - 1), (T - 2, 3), (10, 0)] {
                        let mut r = e;
                        let (b, hint) = r.basis_2f_to_hint(f, ee);
                        let (cb, chint, c) = ce::basis_2f_to_hint(&c_curve(&e), f, ee);
                        assert_eq!(hint, chint, "to_hint hint");
                        assert_basis(&b, &cb, "to_hint");
                        assert_curve(&r, &c, "to_hint curve");
                        if ee == 0 {
                            assert_basis(&check_from_hint(&e, f, hint), &cb, "hint round trip");
                        }
                        // The fallback searches and arbitrary hints.
                        for h in [hint & 1, hint ^ 2, hint | 0xfe] {
                            check_from_hint(&e, f, h);
                        }
                    }
                }
            }

            #[test]
            #[should_panic(expected = "e must be below f")]
            fn hint_order_above_basis_order_panics() {
                let (mut e, _) = curve(&mut Rng::new(51));
                let _ = e.basis_2f_to_hint(4, 4);
            }

            /// A full basis, `P + Q`, and the curve, for each test curve.
            fn full_bases(rng: &mut Rng) -> Vec<(Cv, Bs, Pt)> {
                let mut v = Vec::new();
                for mut e in curves(rng).into_iter().take(3) {
                    let (b, _) = e.basis_2f_to_hint(T, 0);
                    let ppq = b.p.xadd(&b.q, &b.pmq);
                    v.push((e, b, ppq));
                }
                v
            }

            #[test]
            fn reduced_tate_matches_reference() {
                let mut rng = Rng::new(52);
                for (e, b, ppq) in full_bases(&mut rng) {
                    for k in [T, T - 1, T / 2, 7, 1] {
                        let mut d = e;
                        let (p, q) = (d.dbl_iter(&b.p, T - k), d.dbl_iter(&b.q, T - k));
                        let pq = d.dbl_iter(&ppq, T - k);
                        let mut r = e;
                        let w = r.reduced_tate(k, &p, &q, &pq);
                        let (cw, c) = ce::reduced_tate(
                            k as u32,
                            &c_point(&p),
                            &c_point(&q),
                            &c_point(&pq),
                            &c_curve(&e),
                        );
                        assert_eq!(enc(&w), cw.encode(), "reduced_tate");
                        assert_curve(&r, &c, "reduced_tate curve");
                        if k == T {
                            // The pairing of a basis of E[2^T] has order exactly 2^T.
                            let mut h = w;
                            for _ in 1..k {
                                h = h.square();
                            }
                            assert_eq!(h.is_one(), 0, "pairing order");
                            assert_ne!(h.square().is_one(), 0, "pairing order");
                        }
                    }
                    let mut r = e;
                    let (p, _) = point(&mut rng);
                    let w = r.reduced_tate(5, &p, &b.q, &ppq);
                    let (cw, _) = ce::reduced_tate(
                        5,
                        &c_point(&p),
                        &c_point(&b.q),
                        &c_point(&ppq),
                        &c_curve(&e),
                    );
                    assert_eq!(enc(&w), cw.encode(), "reduced_tate random");
                }
            }

            #[test]
            #[should_panic(expected = "unsupported pairing order")]
            fn zero_pairing_order_panics() {
                let mut e = e0();
                let p = Pt::IDENTITY;
                let _ = e.reduced_tate(0, &p, &p, &p);
            }

            fn low_bits(x: &[u64], e: usize) -> Vec<u64> {
                let mut v = x.to_vec();
                for (i, w) in v.iter_mut().enumerate() {
                    let lo = 64 * i;
                    if e <= lo {
                        *w = 0;
                    } else if e - lo < 64 {
                        *w &= (1u64 << (e - lo)) - 1;
                    }
                }
                v
            }

            fn negate(x: &[u64]) -> Vec<u64> {
                let mut borrow = 0u64;
                x.iter()
                    .map(|w| {
                        let (r, b1) = 0u64.overflowing_sub(*w);
                        let (r, b2) = r.overflowing_sub(borrow);
                        borrow = u64::from(b1 | b2);
                        r
                    })
                    .collect()
            }

            #[test]
            fn dlogs_match_reference() {
                let mut rng = Rng::new(53);
                for (e, b, _) in full_bases(&mut rng) {
                    let k: [u64; 4] = core::array::from_fn(|_| u64::from(rng.next_u32()));
                    // a, d odd and b, c even: an invertible change of basis, and
                    // a > c, b > d keep R - S non-negative.
                    let (a, c) = (k[0] | 1 << 31 | 1, (k[2] >> 1) & !1);
                    let (bb, d) = ((k[1] | 1 << 31) & !1, (k[3] >> 1) | 1);
                    let s = small_scalar;
                    let r = e.biscalar_mul(&s(a), &s(bb), 64, &b).expect("R");
                    let sp = e.biscalar_mul(&s(c), &s(d), 64, &b).expect("S");
                    let rms = e
                        .biscalar_mul(&s(a - c), &s(bb - d), 64, &b)
                        .expect("R - S");
                    let rs = Bs {
                        p: r,
                        q: sp,
                        pmq: rms,
                    };
                    for ee in [T, T - 3, 40, 2, 1] {
                        let mut dd = e;
                        let rs = dd.dbl_iter_basis(&rs, T - ee);
                        let mut cur = e;
                        let got = cur.dlog_2_tate(&b, &rs, ee);
                        let ([r1, r2, s1, s2], c2) =
                            ce::dlog_2_tate(&c_basis(&b), &c_basis(&rs), &c_curve(&e), ee);
                        assert_eq!(
                            (&got.r1, &got.r2, &got.s1, &got.s2),
                            (&r1, &r2, &s1, &s2),
                            "dlog e={ee}"
                        );
                        assert_curve(&cur, &c2, "dlog curve");
                        // x-only points fix the logarithms up to one common sign; C
                        // (and so the port) gives no solution for e = 1.
                        if ee > 1 {
                            let got = [got.r1, got.r2, got.s1, got.s2];
                            let want = [a, bb, c, d].map(|x| low_bits(&s(x), ee));
                            let neg = want.clone().map(|x| low_bits(&negate(&x), ee));
                            assert!(got == want || got == neg, "dlog values e={ee}");
                        }
                    }
                    // Unrelated points, and a degenerate first basis whose
                    // reference pairing generates too small a group.
                    let (junk, _) = basis(&mut rng);
                    let mut dd = e;
                    let small = dd.dbl_iter_basis(&b, 2);
                    let mut failed = 0;
                    for (pq, rs) in [(b, junk), (small, b), (small, junk)] {
                        for ee in [T, 2, 1] {
                            let mut cur = e;
                            let got = cur.dlog_2_tate(&pq, &rs, ee);
                            let ([r1, r2, s1, s2], _) =
                                ce::dlog_2_tate(&c_basis(&pq), &c_basis(&rs), &c_curve(&e), ee);
                            assert_eq!(
                                (&got.r1, &got.r2, &got.s1, &got.s2),
                                (&r1, &r2, &s1, &s2),
                                "junk e={ee}"
                            );
                            failed +=
                                usize::from([r1, r2, s1, s2].iter().flatten().all(|w| *w == 0));
                        }
                    }
                    assert!(failed > 0, "no failing discrete logarithm");
                }
            }

            #[test]
            #[should_panic(expected = "unsupported dlog order")]
            fn oversized_dlog_order_panics() {
                let mut e = e0();
                let b = e.basis_2f_from_hint(T, 0);
                let _ = e.dlog_2_tate(&b, &b, T + 1);
            }
        }
    };
}

ec_basis_suite!(p324_3, sqisign::gf::P324);
ec_basis_suite!(p500_27, sqisign::gf::P500);
ec_basis_suite!(p664_17, sqisign::gf::P664);
