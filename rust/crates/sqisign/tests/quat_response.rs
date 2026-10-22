//! Differential tests: Rust randomized `quat` ideal sampling and the
//! signing response against the vendor C reference, on paired PRNGs.

#[macro_use]
mod support;

macro_rules! quat_response_suite {
    ($module:ident, $params:ty, $resp_bits:expr) => {
        #[allow(dead_code, unused_imports)]
        mod $module {
            use super::support::{Rng, SAMPLES};
            use c::ideal as C;
            use sqisign::mp::Ibz;
            use sqisign_reference::$module as c;

            type P = $params;
            type I = Ibz<P>;
            mp_helpers!();
            quat_helpers!();
            ideal_helpers!();

            const N: usize = SAMPLES / 16;

            /// A random value of `1..=bits` bits from the test RNG.
            fn rand_bits(bits: usize, rng: &mut Rng) -> I {
                let w: Vec<u64> = (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                I::from_bits(&w, 1 + rng.next_u64() as usize % bits)
            }

            /// A random odd norm: `bits + 1` bits for even `s` (the signing
            /// size, where elements exist), `2..=bits + 1` bits otherwise.
            fn odd_norm(s: u64, bits: usize, rng: &mut Rng) -> I {
                let r = rand_bits(bits - 1, rng);
                let r = if s % 2 == 0 {
                    r + I::ONE.mul_2exp(bits - 1)
                } else {
                    r
                };
                r.mul_2exp(1) + I::from_i64(3, 32)
            }

            /// A random element with coordinates below `2^bits`.
            fn rand_elem(bits: usize, rng: &mut Rng) -> AlgElem<P> {
                let mut g = AlgElem::from_i32(1, [0; 4]);
                g.coord = core::array::from_fn(|_| rand_bits(bits, rng));
                g
            }

            /// A primitive Gaussian integer `u + v i` with norm coprime to
            /// `n`.
            fn split(n: &I, rng: &mut Rng) -> AlgElem<P> {
                loop {
                    let mut s = AlgElem::from_i32(1, [0; 4]);
                    s.coord[0] = rand_bits(40, rng);
                    s.coord[1] = -rand_bits(40, rng);
                    let norm = I::sum_two_squares(&s.coord[0], &s.coord[1]);
                    if s.coord[0].gcd(&s.coord[1]).is_one() && norm.gcd(n).is_one() {
                        return s;
                    }
                }
            }

            #[test]
            fn prime_norm_ideals_match_reference() {
                for (k, bits) in norm_bits().into_iter().enumerate() {
                    for (s, [(a, ca), _]) in
                        ideal_pairs(bits, N, 60 + k as u8).into_iter().enumerate()
                    {
                        let mut p = seeded(s as u64);
                        let mut d = p.domain(Separator::Keygen);
                        let got = Ideal::random_given_prime_norm(
                            &a.norm,
                            &mut Draw::new(&mut d, p.default_domain()),
                        );
                        same_ideal(
                            &got,
                            &C::random_ideal_given_prime_norm(
                                &ca.norm,
                                &mut c_domain(Separator::Keygen),
                            ),
                        );
                        in_step(&mut p);
                    }
                }
            }

            #[test]
            fn represented_integers_match_reference() {
                let mut rng = Rng::new(3);
                let cof = P::prime_cofactor();
                let mut found = 0;
                for s in 0..2 * N as u64 {
                    // Below p for the first sample: no element exists.
                    let n = if s == 0 {
                        I::from_i64(5, 32)
                    } else {
                        cof * odd_norm(s, $resp_bits, &mut rng)
                    };
                    let mut p = seeded(s);
                    let mut d = p.domain(Separator::Commitment);
                    let got =
                        AlgElem::represent_integer(&n, &mut Draw::new(&mut d, p.default_domain()));
                    let (cg, ok) =
                        C::represent_integer(&to_c(&n), &mut c_domain(Separator::Commitment));
                    assert_eq!(got.is_some(), ok != 0);
                    if let Some(g) = got {
                        same_elem(&g, &cg);
                        found += 1;
                    }
                    in_step(&mut p);
                }
                assert!(found > 0);
            }

            #[test]
            fn odd_norm_ideals_match_reference() {
                let mut rng = Rng::new(4);
                let mut found = 0;
                for s in 0..2 * N as u64 {
                    let n = odd_norm(s, $resp_bits, &mut rng);
                    let shift = rand_elem(P::P_BITS / 2, &mut rng);
                    let mut p = seeded(s);
                    let mut d = p.domain(Separator::Response);
                    let got = Ideal::random_given_odd_norm(
                        &n,
                        &shift,
                        &mut Draw::new(&mut d, p.default_domain()),
                    );
                    let (ci, cs, ok) = C::random_given_odd_norm(
                        &to_c(&n),
                        &elem_c(&shift),
                        &mut c_domain(Separator::Response),
                    );
                    assert_eq!(got.is_some(), ok != 0);
                    if let Some((i, split)) = got {
                        same_opt_ideal(&i, &ci);
                        same_elem(&split, &cs);
                        found += 1;
                    }
                    in_step(&mut p);
                }
                assert!(found > 0);
            }

            #[test]
            fn shortest_equivalents_match_reference() {
                for (k, bits) in norm_bits().into_iter().enumerate() {
                    for (s, [(a, ca), _]) in
                        ideal_pairs(bits, N, 70 + k as u8).into_iter().enumerate()
                    {
                        let mut p = seeded(s as u64);
                        let (e, r) = a.shortest_equivalent(p.default_domain());
                        let (ce, cr) = C::shortest_equivalent(&ca);
                        same_elem(&e, &ce);
                        same_ideal(&r, &cr);
                        in_step(&mut p);
                    }
                }
            }

            /// Norm sizes of the signing path's calls: response, `p` and
            /// `SEC_DEGREE` (C asserts on far smaller ones; see
            /// `small_split_norm_panics_like_the_c_assert`).
            fn equiv_bits() -> [i32; 3] {
                let pb = P::P_BITS as i32;
                [norm_bits()[4], pb, pb * 9 / 5]
            }

            #[test]
            fn small_equivalents_match_reference() {
                let mut rng = Rng::new(5);
                for (k, bits) in equiv_bits().into_iter().enumerate() {
                    for (s, [(a, ca), (b, _)]) in
                        ideal_pairs(bits, N, 75 + k as u8).into_iter().enumerate()
                    {
                        let others = [I::ZERO, I::TWO, b.norm * rand_bits(20, &mut rng)];
                        for (t, to) in others.iter().enumerate() {
                            let mut p = seeded(100 * s as u64 + t as u64);
                            let mut d = p.domain(Separator::Keygen);
                            let (g, e) = a.small_equivalent_coprime(
                                to,
                                &mut Draw::new(&mut d, p.default_domain()),
                            );
                            let (cg, ce, ok) = C::small_equivalent_coprime(
                                &ca,
                                &to_c(to),
                                &mut c_domain(Separator::Keygen),
                            );
                            assert_ne!(ok, 0);
                            same_elem(&g, &cg);
                            same_ideal(&e, &ce);
                            in_step(&mut p);
                        }
                    }
                }
            }

            /// Whether the bounding parallelogram of `l` at `radius` has a
            /// zero side and a point `sample_from_ball` accepts.
            fn has_partial_box_point(l: &Lattice<P>, radius: &I) -> bool {
                use sqisign::quat::dim4;
                let rad = radius.mul_2exp(1);
                let (bx, u, ok) = l.bound_parallelogram(&rad);
                if !ok || !bx.iter().any(I::is_zero) {
                    return false;
                }
                let g = l.product_gram_matrix();
                let side = |b: &I| -(b.get() as i64)..=b.get() as i64;
                side(&bx[0]).any(|x0| {
                    side(&bx[1]).any(|x1| {
                        side(&bx[2]).any(|x2| {
                            side(&bx[3]).any(|x3| {
                                let x = [x0, x1, x2, x3].map(|c| I::from_i64(c, 64));
                                let q = dim4::qf_eval(&g, &dim4::mat_eval_t(&x, &u));
                                q.get() % 4 != 0 && q <= rad
                            })
                        })
                    })
                })
            }

            #[test]
            fn ball_samples_match_reference() {
                let e = $resp_bits - 1;
                let mut radius = I::ONE.mul_2exp(e) - I::ONE;
                radius.set_bound(e + 1);
                let mut mixed = 0;
                for (s, [(a, ca), (b, cb)]) in
                    ideal_pairs(norm_bits()[4], N, 80).into_iter().enumerate()
                {
                    let l = a.mul_o0(&b);
                    let cl = C::mul_o0(&ca, &cb);
                    // The largest power-of-two radius a few bits above the
                    // first box beyond the origin whose box has a zero side
                    // and a point the rejection sampling accepts.
                    let first = (P::P_BITS / 2 - 32..)
                        .find(|&k| l.bound_parallelogram(&I::ONE.mul_2exp(k + 1)).2)
                        .unwrap();
                    let part = (first..first + 8)
                        .rev()
                        .map(|k| I::ONE.mul_2exp(k))
                        .find(|r| has_partial_box_point(&l, r));
                    mixed += usize::from(part.is_some());
                    for r in [radius, I::ONE].into_iter().chain(part) {
                        let p = seeded(s as u64);
                        let mut d = p.domain(Separator::Response);
                        let got = l.sample_from_ball(&r, &mut d);
                        let (ce, cn, ok) =
                            C::sample_from_ball(&cl, &to_c(&r), &mut c_domain(Separator::Response));
                        assert_eq!(got.is_some(), ok != 0);
                        if let Some((el, n)) = got {
                            same_elem(&el, &ce);
                            same(&n, &cn);
                        }
                    }
                }
                assert!(mixed > 0);
            }

            #[test]
            fn responses_match_reference() {
                let mut rng = Rng::new(6);
                let e = $resp_bits - 1;
                for (s, [(sk, csk), (com, ccom)]) in
                    ideal_pairs(norm_bits()[4], N, 90).into_iter().enumerate()
                {
                    let gen = rand_elem(P::P_BITS / 2, &mut rng);
                    let (chall, _) = Ideal::create_o0_inert(&gen, &I::ONE.mul_2exp($resp_bits / 2));
                    let (cchall, _) =
                        C::create_o0_inert(&elem_c(&gen), &to_c(&I::ONE.mul_2exp($resp_bits / 2)));
                    let sp = split(&sk.norm, &mut rng);
                    let mut p = seeded(s as u64);
                    let mut d = p.domain(Separator::Response);
                    let got = sk.response_element(&chall, &sp, &com, &mut d, p.default_domain(), e);
                    let (ci, cq, cn, cs) = C::response_element(
                        &csk,
                        &cchall,
                        &elem_c(&sp),
                        &ccom,
                        &mut c_domain(Separator::Response),
                        e as i32,
                    );
                    same_ideal(&got.sk_chall_ideal, &ci);
                    same_elem(&got.resp_quat, &cq);
                    same(&got.norm, &cn);
                    same_elem(&got.sk_chall_quat, &cs);
                    in_step(&mut p);
                }
            }
        }
    };
}

quat_response_suite!(p324_3, sqisign::gf::P324, 196);

/// A 90-bit norm ideal for which C's bound-clamped Gaussian gcd yields a
/// split part of norm above the prime found, so the inert norm is zero and C
/// divides by it.
#[test]
#[should_panic(expected = "C divides by a zero inert norm")]
fn zero_inert_norm_panics_like_the_c_division() {
    use sqisign::mp::Ibz;
    use sqisign::prng::{Prng, Separator, SEED_BYTES};
    use sqisign::quat::{Draw, Ideal};
    type I = Ibz<sqisign::gf::P324>;
    let v = |lo: u64, hi: u64| {
        let mut x = I::from_bits(&[lo, hi], 93);
        x.set_bound(93);
        x
    };
    let a = Ideal {
        x: v(16182485669978929650, 9546417),
        y: v(10490986500480259147, 14383430),
        norm: v(15560469119401928035, 52484868),
    };
    let entropy: [u8; SEED_BYTES] = support::Rng::new(200).bytes(SEED_BYTES).try_into().unwrap();
    let mut p = Prng::new(sqisign_reference::p324_3::mp::prng_seed(&entropy));
    let mut d = p.domain(Separator::Keygen);
    a.small_equivalent_coprime(&I::ZERO, &mut Draw::new(&mut d, p.default_domain()));
}
quat_response_suite!(p500_27, sqisign::gf::P500, 302);
quat_response_suite!(p664_17, sqisign::gf::P664, 400);
