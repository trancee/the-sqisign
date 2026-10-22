//! Differential tests: Rust `quat` integer helpers, 2x2 matrices and Gaussian
//! integers against the vendor C reference.

#[macro_use]
mod support;

macro_rules! quat_dim2_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::{Rng, SAMPLES};
            use c::quat as C;
            use sqisign::mp::Ibz;
            use sqisign::quat::dim2 as R;
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            mp_helpers!();
            quat_helpers!();

            /// Values small enough that norms and products of Gaussian
            /// integers do not wrap.
            fn moderate(seed: u64) -> Vec<I> {
                pool(seed, CAP / 4)
            }

            #[test]
            fn rounded_div_and_sum_two_squares_match_reference() {
                let p = pool(1, CAP);
                let mut rng = Rng::new(2);
                let small: Vec<I> = (-9..=9).map(|k| I::from_i64(k, 5)).collect();
                let mut cases: Vec<(I, I)> = small
                    .iter()
                    .flat_map(|a| small.iter().map(move |b| (*a, *b)))
                    .collect();
                cases.extend((0..SAMPLES * 4).map(|_| (pick(&p, &mut rng), pick(&p, &mut rng))));
                for (a, b) in cases {
                    let (x, y) = (to_c(&a), to_c(&b));
                    same(&I::sum_two_squares(&a, &b), &C::sum_two_squares(&x, &y));
                    if !b.is_zero() {
                        same(&a.rounded_div(&b), &C::rounded_div(&x, &y));
                    }
                }
            }

            #[test]
            fn vectors_match_reference() {
                let p = pool(3, CAP);
                let mut rng = Rng::new(4);
                for (a0, a1) in [(0, 0), (1, -1), (i32::MAX, -i32::MAX), (-7, 123_456)] {
                    same_vec(&R::vec_from::<$params>(a0, a1), &C::vec2_set(a0, a1));
                }
                let zero = [I::ZERO; 2];
                assert!(R::vec_is_zero(&zero) && C::vec2_is_zero(&vec_c(&zero)) == 1);
                for i in 0..SAMPLES {
                    let (mut a, b) = (pick_vec::<2>(&p, &mut rng), pick_vec::<2>(&p, &mut rng));
                    a[i % 2] = I::ZERO;
                    let (x, y) = (vec_c(&a), vec_c(&b));
                    same_vec(&R::vec_add(&a, &b), &C::vec2_add(&x, &y));
                    same_vec(&R::vec_sub(&a, &b), &C::vec2_sub(&x, &y));
                    assert_eq!(R::vec_is_zero(&a), C::vec2_is_zero(&x) == 1);
                }
            }

            #[test]
            fn matrix_products_and_inverses_match_reference() {
                let p = pool(5, CAP);
                let mut rng = Rng::new(6);
                for i in 0..SAMPLES {
                    let (a, mut b) = (pick_mat::<2>(&p, &mut rng), pick_mat::<2>(&p, &mut rng));
                    let v = pick_vec::<2>(&p, &mut rng);
                    if i % 4 == 0 {
                        // Singular: proportional rows.
                        b[1] = b[0];
                    }
                    let (x, y) = (mat_c(&a), mat_c(&b));
                    same_vec(&R::mat_eval(&a, &v), &C::mat2_eval(&x, &vec_c(&v)));
                    same_mat(&R::mat_mul(&a, &b), &C::mat2_mul(&x, &y));
                    let (inv, det) = R::mat_inv_with_det(&b);
                    let (cinv, cdet, ok) = C::mat2_inv_with_det(&y);
                    same(&det, &cdet);
                    assert_eq!(inv.is_some(), ok == 1);
                    same_mat(&inv.unwrap_or([[I::ZERO; 2]; 2]), &cinv);
                }
            }

            #[test]
            fn modular_inverses_match_reference() {
                let p = pool(7, CAP / 2);
                let mut rng = Rng::new(8);
                let moduli: Vec<I> = p.iter().filter(|m| **m > I::ZERO).copied().collect();
                for _ in 0..SAMPLES * 2 {
                    let mut m = pick(&moduli, &mut rng);
                    if rng.next_u64() % 4 == 0 {
                        m = I::from_i64(1 << (rng.next_u64() % 12), 32);
                    }
                    let a = pick_mat::<2>(&p, &mut rng);
                    let (inv, ok) = R::mat_inv_mod(&a, &m);
                    let (cinv, cok) = C::mat2_inv_mod(&mat_c(&a), &to_c(&m));
                    assert_eq!(ok, cok == 1);
                    same_mat(&inv, &cinv);
                }
            }

            #[test]
            fn normalize_matches_reference() {
                let mut rng = Rng::new(9);
                for e in [1, 2, 5, 63, 64, 65, CAP / 2, CAP - 3] {
                    let half = I::ONE.mul_2exp(e - 1);
                    let mut cases = vec![[[I::ZERO; 2]; 2], [[half; 2]; 2]];
                    for _ in 0..SAMPLES / 4 {
                        let m: [[I; 2]; 2] = core::array::from_fn(|_| {
                            core::array::from_fn(|_| match rng.next_u64() % 4 {
                                0 => I::ZERO,
                                1 => half,
                                _ => {
                                    let w: Vec<u64> =
                                        (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                                    I::from_bits(&w, e)
                                }
                            })
                        });
                        cases.push(m);
                    }
                    for m in cases {
                        let mut r = m;
                        R::mat_normalize(&mut r, e);
                        same_mat(&r, &C::mat2_normalize(&mat_c(&m), e as i32));
                    }
                }
            }

            #[test]
            fn gaussian_arithmetic_matches_reference() {
                let p = moderate(10);
                let mut rng = Rng::new(11);
                let zero = [I::ZERO; 2];
                for i in 0..SAMPLES * 2 {
                    let a = pick_vec::<2>(&p, &mut rng);
                    let b = [pick_nonzero(&p, &mut rng), pick(&p, &mut rng)];
                    let (x, y) = (vec_c(&a), vec_c(&b));
                    same_vec(&R::gaussian_mul(&a, &b), &C::gaussian_mul(&x, &y));
                    let (q, r) = R::gaussian_div(&a, &b);
                    let (cq, cr) = C::gaussian_div(&x, &y);
                    same_vec(&q, &cq);
                    same_vec(&r, &cr);
                    // A common factor makes the gcd non-trivial.
                    let g = [
                        pick(&p, &mut rng).abs().div_2exp(CAP / 8),
                        I::from_i64(3, 32),
                    ];
                    let (a, b) = match i % 4 {
                        0 => (zero, b),
                        1 => (a, zero),
                        _ => (R::gaussian_mul(&a, &g), R::gaussian_mul(&b, &g)),
                    };
                    same_vec(
                        &R::gaussian_gcd(&a, &b),
                        &C::gaussian_gcd(&vec_c(&a), &vec_c(&b)),
                    );
                }
            }
        }
    };
}

quat_dim2_suite!(p324_3, sqisign::gf::P324);
quat_dim2_suite!(p500_27, sqisign::gf::P500);
quat_dim2_suite!(p664_17, sqisign::gf::P664);
