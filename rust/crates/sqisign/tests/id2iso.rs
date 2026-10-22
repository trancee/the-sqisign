//! Differential tests: Rust ideal-to-isogeny translation against the vendor C
//! reference, on paired PRNGs.

#[macro_use]
mod support;

macro_rules! id2iso_suite {
    ($module:ident, $params:ty) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            ec_helpers!($module, $params);
            use super::support::SAMPLES;
            use c::id2iso as C;
            use sqisign::gf::FpParams;
            use sqisign::id2iso::{self as R, Id2isoParams};
            use sqisign::mp::Ibz;
            use sqisign::quat::{Mat2, Vec2};
            use sqisign::verify::SqisignParams;

            type P = $params;
            type I = Ibz<P>;
            mp_helpers!();
            quat_helpers!();
            ideal_helpers!();

            const T: usize = <P as FpParams>::T as usize;
            const N: usize = SAMPLES / 32;

            fn endo() -> &'static R::CurveWithEndomorphisms<P> {
                &P::CURVE_WITH_ENDOMORPHISMS
            }

            /// A random non-negative value below `2^bits`.
            fn below(bits: usize, rng: &mut Rng) -> I {
                let w: Vec<u64> = (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                I::from_bits(&w, bits)
            }

            /// A random signed matrix of entries of up to `bits + 8` bits.
            fn matrix(bits: usize, rng: &mut Rng) -> Mat2<P> {
                [[0; 2]; 2].map(|row| {
                    row.map(|_| {
                        let x = below(bits + 8, rng);
                        if rng.next_u32() % 2 == 0 {
                            x
                        } else {
                            -x
                        }
                    })
                })
            }

            /// A random element of `O0 = <1, i, (i + j) / 2, (1 + k) / 2>`
            /// with coordinates of up to `bits` bits, odd content when `odd`.
            fn o0_elem(bits: usize, rng: &mut Rng) -> AlgElem<P> {
                let mut x = [0; 4].map(|_| below(bits, rng) - below(bits, rng));
                let half = rng.next_u32() % 2 == 0;
                if half {
                    // (a + b i + c j + d k) / 2 with a = d, b = c mod 2.
                    x[3] = x[3] + (x[0] - x[3]).mod_2exp(1);
                    x[2] = x[2] + (x[1] - x[2]).mod_2exp(1);
                }
                AlgElem {
                    denom: I::from_i64(if half { 2 } else { 1 }, 32),
                    coord: x,
                }
            }

            /// `f` values from tiny to the full torsion.
            fn exponents() -> [usize; 6] {
                [1, 2, 17, P::CHALLENGE_BITS, T - 1, T]
            }

            #[test]
            fn curve_with_endomorphisms_matches_reference() {
                let ce = C::curve_with_endomorphisms();
                let e = endo();
                assert_curve(&e.curve, &ce.curve, "E0");
                assert_basis(&e.basis_even, &ce.basis_even, "basis");
                let r = [
                    &e.action_i,
                    &e.action_j,
                    &e.action_k,
                    &e.action_gen2,
                    &e.action_gen3,
                    &e.action_gen4,
                ];
                for (m, cm) in r.into_iter().zip(&ce.actions) {
                    same_mat(m, cm);
                }
            }

            #[test]
            fn matrix_applications_match_reference() {
                let mut rng = Rng::new(1);
                let e = endo();
                for f in exponents() {
                    for k in 0..N {
                        let last = k % 2 == 0;
                        let mut m = matrix(f, &mut rng);
                        let mut b = e.basis_even;
                        let (cb, cm) = C::matrix_application_even_basis(
                            &c_basis(&b),
                            &c_curve(&e.curve),
                            &mat_c(&m),
                            f,
                            last,
                        );
                        R::matrix_application_even_basis(&mut b, &e.curve, &mut m, f, last);
                        assert_basis(&b, &cb, "basis");
                        same_mat(&m, &cm);
                    }
                }
            }

            #[test]
            fn endomorphism_applications_match_reference() {
                let mut rng = Rng::new(2);
                let e = endo();
                for f in exponents() {
                    for k in 0..N {
                        let last = k % 2 == 0;
                        let theta = o0_elem(f + 8, &mut rng);
                        let mut b = e.basis_even;
                        let cb = C::endomorphism_application_even_basis(
                            &c_basis(&b),
                            &c_curve(&e.curve),
                            &elem_c(&theta),
                            f,
                            last,
                        );
                        R::endomorphism_application_even_basis(&mut b, &e.curve, &theta, f, last);
                        assert_basis(&b, &cb, "basis");
                    }
                }
            }

            #[test]
            fn kernel_dlogs_match_reference() {
                let mut rng = Rng::new(3);
                let mut splits = [0; 2];
                for f in exponents().into_iter().filter(|&f| f > 1) {
                    for _ in 0..N {
                        let mut v: Vec2<P> = [below(f, &mut rng), below(f, &mut rng)];
                        // A kernel of full order 2^f.
                        let k = (rng.next_u32() % 2) as usize;
                        v[k] = v[k] + I::ONE - v[k].mod_2exp(1);
                        let (ideal, split) = R::kernel_dlogs_to_ideal_even(&v, f);
                        let (ci, cs) = C::kernel_dlogs_to_ideal_even(&vec_c(&v), f);
                        same_ideal(&ideal, &ci);
                        same_elem(&split, &cs);
                        splits[usize::from(split.coord[1].is_zero())] += 1;
                    }
                }
                assert!(splits.iter().all(|&s| s > 0), "{splits:?}");
            }

            /// `M . B2` for a random invertible `M` and the full basis `B2`,
            /// then doubled down to `E0[2^f]`.
            fn sub_basis(f: usize, rng: &mut Rng) -> Bs {
                let e = endo();
                let mut m = matrix(T, rng);
                m[0][0] = m[0][0] + I::ONE - m[0][0].mod_2exp(1);
                m[0][1] = m[0][1] - m[0][1].mod_2exp(1);
                m[1][1] = m[1][1] + I::ONE - m[1][1].mod_2exp(1);
                let mut b = e.basis_even;
                R::matrix_application_even_basis(&mut b, &e.curve, &mut m, T, true);
                e.curve.clone().dbl_iter_basis(&b, T - f)
            }

            #[test]
            fn changes_of_basis_match_reference() {
                let mut rng = Rng::new(4);
                let e = endo();
                for f in exponents() {
                    for _ in 0..N {
                        let b1 = sub_basis(f, &mut rng);
                        let mut curve = e.curve;
                        let m = R::change_of_basis_matrix_tate(&b1, &e.basis_even, &mut curve, f);
                        let (cm, cc) = C::change_of_basis_matrix_tate(
                            &c_basis(&b1),
                            &c_basis(&e.basis_even),
                            &c_curve(&e.curve),
                            f,
                        );
                        same_mat(&m, &cm);
                        assert_curve(&curve, &cc, "curve");

                        let mut curve = e.curve;
                        let m = R::change_of_basis_matrix_tate_invert(
                            &e.basis_even,
                            &b1,
                            &mut curve,
                            f,
                        );
                        let (cm, cc) = C::change_of_basis_matrix_tate_invert(
                            &c_basis(&e.basis_even),
                            &c_basis(&b1),
                            &c_curve(&e.curve),
                            f,
                        );
                        same_mat(&m, &cm);
                        assert_curve(&curve, &cc, "curve");
                    }
                }
            }

            /// Ideals of the signing sizes whose qlapoty succeeds on the
            /// stream of seed `s`, and the seed.
            fn solvable() -> Vec<(u64, Ideal<P>, c::ideal::Ideal)> {
                let bits = norm_bits();
                bits[3..]
                    .iter()
                    .enumerate()
                    .flat_map(|(k, &b)| ideal_pairs(b, 2, 90 + k as u8))
                    .flatten()
                    .enumerate()
                    .filter_map(|(s, (a, ca))| {
                        let mut d = seeded(s as u64).default_domain().clone();
                        a.qlapoty(&mut d).map(|_| (s as u64, a, ca))
                    })
                    .collect()
            }

            #[test]
            fn ideal_to_isogeny_matches_reference() {
                let ideals = solvable();
                assert!(!ideals.is_empty());
                for (s, a, ca) in ideals {
                    let mut p = seeded(s);
                    let got = R::ideal_to_isogeny_qlapoty(&a, p.default_domain());
                    let (b, d, e, bas, ok) = C::ideal_to_isogeny_qlapoty(&ca);
                    assert_eq!(got.is_some(), ok != 0);
                    let got = got.unwrap();
                    same_elem(&got.beta1, &b);
                    same(&got.d1, &d);
                    assert_curve(&got.codomain, &e, "codomain");
                    assert_basis(&got.basis, &bas, "basis");
                    in_step(&mut p);

                    let mut p = seeded(s);
                    let got = R::arbitrary_isogeny_evaluation(&a, p.default_domain());
                    let (bas, e, ok) = C::arbitrary_isogeny_evaluation(&ca);
                    assert_eq!(ok, 1);
                    let (rb, re) = got.unwrap();
                    assert_curve(&re, &e, "codomain");
                    assert_basis(&rb, &bas, "basis");
                    in_step(&mut p);
                }
            }

            #[test]
            #[should_panic(expected = "Qlapoty failed")]
            fn unsolvable_ideal_panics_like_the_c_abort() {
                let [(a, _), _] = ideal_pairs(3, 1, 7).remove(0);
                let mut p = seeded(0);
                R::ideal_to_isogeny_qlapoty(&a, p.default_domain());
            }
        }
    };
}

id2iso_suite!(p324_3, sqisign::gf::P324);
id2iso_suite!(p500_27, sqisign::gf::P500);
id2iso_suite!(p664_17, sqisign::gf::P664);
