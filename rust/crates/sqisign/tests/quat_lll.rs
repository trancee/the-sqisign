//! Differential tests: Rust `quat` lattice reductions against the vendor C
//! reference.

#[macro_use]
mod support;

macro_rules! quat_lll_suite {
    ($module:ident, $params:ty, $response_bits:literal) => {
        #[allow(dead_code)]
        mod $module {
            use super::support::{Rng, SAMPLES};
            use c::lll as C;
            use sqisign::mp::Ibz;
            use sqisign::quat::{lll, Alg, Lattice};
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();
            quat_helpers!();

            /// A positive value of exactly `bits` bits.
            fn positive(bits: usize, rng: &mut Rng) -> I {
                let mut w: Vec<u64> = (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                let (word, bit) = ((bits - 1) / 64, (bits - 1) % 64);
                w[word] = w[word] & (u64::MAX >> (63 - bit)) | 1 << bit;
                w[word + 1..].iter_mut().for_each(|x| *x = 0);
                I::from_bits(&w, bits + 1)
            }

            /// A prime `= 1 mod 4` of `bits` bits, by C's primality test.
            fn prime_1_mod_4(bits: usize, rng: &mut Rng) -> I {
                let mut x = positive(bits, rng);
                x = x - I::from_i64(x.get() as i64 & 3, 4) + I::from_i64(1, 2);
                loop {
                    if c::mp::probab_prime(&to_c(&x), 1) != 0 {
                        return x;
                    }
                    x = x + I::from_i64(4, 4);
                }
            }

            /// A column-HNF basis `[[a, b], [0, g]]` and a `det_bits` with
            /// `a * g <= 2^det_bits`.
            fn hnf(rng: &mut Rng) -> ([[I; 2]; 2], usize) {
                let total =
                    [2, 8, 31, 62, 63, 64, 65, 100, 200, CAP / 3][rng.next_u64() as usize % 10];
                let ga = 1 + rng.next_u64() as usize % total;
                let a = positive(ga, rng);
                let g = if rng.next_u64() % 4 == 0 {
                    I::from_i64(1, 2)
                } else {
                    positive(1 + rng.next_u64() as usize % total, rng)
                };
                let b = match rng.next_u64() % 4 {
                    0 => I::ZERO,
                    _ => positive(1 + rng.next_u64() as usize % ga, rng).modulo(&a),
                };
                let det = (a * g).bitsize() + rng.next_u64() as usize % 3;
                ([[a, b], [I::ZERO, g]], det)
            }

            /// A lattice whose columns 0 and 2 hold signed entries below
            /// `2^bound`, and the C version.
            fn zi_lattice(bound: usize, rng: &mut Rng) -> (Lattice<$params>, c::lattice::Lattice) {
                let mut basis = [[I::ZERO; 4]; 4];
                for row in &mut basis {
                    for col in [0, 2] {
                        let x = match rng.next_u64() % 8 {
                            0 => I::ZERO,
                            _ => positive(1 + rng.next_u64() as usize % bound, rng),
                        };
                        row[col] = if rng.next_u64() & 1 == 0 { x } else { -x };
                    }
                }
                let l = Lattice {
                    denom: I::from_i64(2, 3),
                    basis,
                };
                let cl = c::lattice::Lattice {
                    denom: to_c(&l.denom),
                    basis: mat_c(&l.basis),
                };
                (l, cl)
            }

            /// The Rust value of a C value.
            fn from_c(x: &c::mp::Ibz) -> I {
                let b = x.bitlen as usize;
                let mut r = I::from_bits(&x.limbs, b.min(CAP - 1));
                r.set_bound(b);
                r
            }

            /// Products of two random `O0` ideals of coprime prime norms of
            /// `bits` bits (vendor `quat_test_input_resplike_lattice_generation`),
            /// in Rust and C.
            fn resplike(
                bits: i32,
                n: usize,
                seed: u8,
            ) -> Vec<(Lattice<$params>, c::lattice::Lattice)> {
                c::mp::prng_seed(&[seed; 48]);
                let mut d = c::mp::Domain::new(b"tst").unwrap();
                let mut out = Vec::new();
                while out.len() < n {
                    let (na, nb) = (
                        c::ideal::random_prime(false, bits),
                        c::ideal::random_prime(false, bits),
                    );
                    if c::mp::cmp(&na, &nb) == 0 {
                        continue;
                    }
                    let a = c::ideal::random_ideal_given_prime_norm(&na, &mut d);
                    let b = c::ideal::random_ideal_given_prime_norm(&nb, &mut d);
                    let cl = c::ideal::mul_o0(&a, &b);
                    let l = Lattice {
                        denom: from_c(&cl.denom),
                        basis: cl.basis.map(|row| row.map(|x| from_c(&x))),
                    };
                    out.push((l, cl));
                }
                out
            }

            /// Norm-bit sizes from tiny to the signing path's
            /// `QUAT_P_BITS / 2 + 15`.
            fn norm_bits() -> [i32; 5] {
                let resp = (<$params as sqisign::quat::QuatParams>::P_BITS / 2 + 15) as i32;
                [3, 17, 64, resp / 2, resp]
            }

            #[test]
            fn dual_reduce_ideal_matches_reference() {
                let alg = Alg::<$params>::pinfty();
                let calg = c::lattice::pinfty();
                for (k, bits) in norm_bits().into_iter().enumerate() {
                    for (l, cl) in resplike(bits, SAMPLES / 8, 40 + k as u8) {
                        let r = l.dual_reduce_ideal(&alg);
                        let (cr, cg, ca) = C::dual_reduce_ideal(&cl, &calg);
                        same(&r.reduced.denom, &cr.denom);
                        same_mat(&r.reduced.basis, &cr.basis);
                        same_vec(&r.gram_diag, &cg);
                        same_mat(&r.ainv, &ca);
                    }
                }
            }

            #[test]
            fn bound_parallelogram_matches_reference() {
                // `2^160` leaves some sides zero for 64-bit norms (not at the
                // 664-bit prime, where C finds no box for them).
                let mut radii = vec![I::ZERO, I::ONE, I::from_i64(1000, 12), I::ONE.mul_2exp(160)];
                let signing = I::ONE.mul_2exp($response_bits) - I::from_i64(2, 3);
                radii.push(signing);
                for (k, bits) in norm_bits().into_iter().enumerate() {
                    for (l, cl) in resplike(bits, SAMPLES / 8, 50 + k as u8) {
                        for radius in &radii {
                            let (b, u, ok) = l.bound_parallelogram(radius);
                            let (cb, cu, cok) = C::bound_parallelogram(&cl, &to_c(radius));
                            assert_eq!(ok, cok != 0);
                            same_vec(&b, &cb);
                            same_mat(&u, &cu);
                        }
                    }
                }
            }

            #[test]
            fn reduce_o0_ideal_matches_reference() {
                let mut rng = Rng::new(34);
                let alg = Alg::<$params>::pinfty();
                let calg = c::lattice::pinfty();
                let sqrt_p = <$params as sqisign::quat::QuatParams>::SQRT_P_BITS;
                for bound in [
                    2,
                    30,
                    64,
                    sqrt_p,
                    sqrt_p + 2,
                    2 * sqrt_p,
                    3 * sqrt_p,
                    CAP / 4,
                ] {
                    for _ in 0..SAMPLES / 4 {
                        let (l, cl) = zi_lattice(bound, &mut rng);
                        let r = l.reduce_o0_ideal(&alg, bound);
                        let cr = C::reduce_o0_ideal(&cl, &calg, bound as i32);
                        same(&r.denom, &cr.denom);
                        same_mat(&r.basis, &cr.basis);
                    }
                }
            }

            #[test]
            fn short_basis_matches_reference() {
                let mut rng = Rng::new(31);
                for _ in 0..4 * SAMPLES {
                    let (m, det) = hnf(&mut rng);
                    let r = lll::dim2_short_basis(&m, det);
                    same_mat(&r, &C::dim2_short_basis(&mat_c(&m), det as i32));
                }
            }

            #[test]
            fn sums_of_squares_match_reference() {
                let mut rng = Rng::new(32);
                for bits in [3, 5, 8, 20, 33, 63, 64, 65, 128, 200, CAP / 3] {
                    for _ in 0..4 {
                        let p = prime_1_mod_4(bits, &mut rng);
                        let r = p.sqrt_m1_mod_verified().unwrap();
                        let (x, y) = lll::dim2_sum_of_squares(&p, &r);
                        let (cx, cy) = C::dim2_sumofsquares(&to_c(&p), &to_c(&r));
                        same(&x, &cx);
                        same(&y, &cy);
                        let (xy, ok) = (p.cornacchia_prime(), C::cornacchia_prime(&to_c(&p)));
                        assert_eq!(ok.2, 1);
                        let (x, y) = xy.unwrap();
                        same(&x, &ok.0);
                        same(&y, &ok.1);
                        assert!(I::sum_two_squares(&x, &y) == p && x <= y);
                    }
                }
            }

            #[test]
            fn cornacchia_edge_cases_match_reference() {
                let mut rng = Rng::new(33);
                let mut inputs: Vec<I> = (1..=40).map(|k| I::from_i64(k, 8)).collect();
                inputs.extend(
                    (0..SAMPLES).map(|_| positive(1 + rng.next_u64() as usize % 200, &mut rng)),
                );
                // A qlapoty `z` whose reduction already ends with `x < y`.
                inputs.push(I::from_bits(
                    &[
                        7975895079447544505,
                        5620290583429419967,
                        11518715144974204029,
                        12654877657546473977,
                        4327165982505072592,
                        754,
                        0,
                        0,
                        0,
                        0,
                        0,
                    ],
                    693,
                ));
                for p in inputs {
                    let got = p.cornacchia_prime();
                    let (cx, cy, ok) = C::cornacchia_prime(&to_c(&p));
                    let c_ok = ok != 0;
                    assert_eq!(got.is_some(), c_ok, "{p:?}");
                    if let Some((x, y)) = got {
                        same(&x, &cx);
                        same(&y, &cy);
                    }
                }
            }
        }
    };
}

quat_lll_suite!(p324_3, sqisign::gf::P324, 196);
quat_lll_suite!(p500_27, sqisign::gf::P500, 302);
quat_lll_suite!(p664_17, sqisign::gf::P664, 400);
