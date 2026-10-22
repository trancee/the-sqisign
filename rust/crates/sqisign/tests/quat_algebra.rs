//! Differential tests: Rust `quat` 4-dimensional vectors, matrices,
//! quadratic forms and algebra elements against the vendor C reference.

#[macro_use]
mod support;

macro_rules! quat_algebra_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::{Rng, SAMPLES};
            use c::quat as C;
            use sqisign::mp::Ibz;
            use sqisign::quat::{dim4 as R, Alg, AlgElem};
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            type E = AlgElem<$params>;
            mp_helpers!();
            quat_helpers!();

            fn elem_c(e: &E) -> C::AlgElem {
                C::AlgElem {
                    denom: to_c(&e.denom),
                    coord: vec_c(&e.coord),
                }
            }

            fn same_elem(r: &E, x: &C::AlgElem) {
                same(&r.denom, &x.denom);
                same_vec(&r.coord, &x.coord);
            }

            /// Elements with non-zero denominators.
            fn pick_elem(p: &[I], rng: &mut Rng) -> E {
                E {
                    denom: pick_nonzero(p, rng),
                    coord: pick_vec(p, rng),
                }
            }

            /// An algebra over a positive `p`, for the C functions do not need
            /// a prime.
            fn pick_alg(p: &[I], rng: &mut Rng) -> (Alg<$params>, C::Alg) {
                let q = pick_nonzero(p, rng).abs();
                (Alg { p: q }, C::Alg { p: to_c(&q) })
            }

            #[test]
            fn vectors_match_reference() {
                let p = pool(1, CAP);
                let mut rng = Rng::new(2);
                for c4 in [[0; 4], [1, -1, i32::MAX, -i32::MAX], [5, 0, -3, 77]] {
                    same_vec(&R::vec_from::<$params>(c4), &C::vec4_set(c4));
                }
                for i in 0..SAMPLES {
                    let (mut a, b) = (pick_vec::<4>(&p, &mut rng), pick_vec::<4>(&p, &mut rng));
                    if i % 2 == 0 {
                        a.iter_mut().take(3).for_each(|x| *x = I::ZERO);
                    }
                    if i % 8 == 0 {
                        a[3] = I::ZERO;
                    }
                    let s = pick(&p, &mut rng);
                    let (x, y, cs) = (vec_c(&a), vec_c(&b), to_c(&s));
                    same_vec(&R::vec_add(&a, &b), &C::vec4_add(&x, &y));
                    same_vec(&R::vec_sub(&a, &b), &C::vec4_sub(&x, &y));
                    assert_eq!(R::vec_is_zero(&a), C::vec4_is_zero(&x) == 1);
                    same(&R::vec_content(&b), &C::vec4_content(&y));
                    same_vec(&R::vec_scalar_mul(&s, &b), &C::vec4_scalar_mul(&cs, &y));
                    let s = pick_nonzero(&p, &mut rng);
                    // Multiples of `s`, and `s` itself plus one in a single
                    // coordinate.
                    let mut m = b.map(|x| x.div_2exp(CAP / 2) * s);
                    if i % 3 == 0 {
                        m[i % 4] = m[i % 4] + I::ONE;
                    }
                    for v in [b, m] {
                        let (q, ok) = R::vec_scalar_div(&s, &v);
                        let (cq, cok) = C::vec4_scalar_div(&to_c(&s), &vec_c(&v));
                        same_vec(&q, &cq);
                        assert_eq!(ok, cok == 1);
                    }
                }
            }

            #[test]
            fn matrices_and_quadratic_forms_match_reference() {
                let p = pool(3, CAP);
                let mut rng = Rng::new(4);
                same_mat(&R::mat_zero::<$params>(), &C::mat4_zero());
                same_mat(&R::mat_identity::<$params>(), &C::mat4_identity());
                for _ in 0..SAMPLES {
                    let m = pick_mat::<4>(&p, &mut rng);
                    let (v, s) = (pick_vec::<4>(&p, &mut rng), pick(&p, &mut rng));
                    let (cm, cv) = (mat_c(&m), vec_c(&v));
                    same_mat(
                        &R::mat_scalar_mul(&s, &m),
                        &C::mat4_scalar_mul(&to_c(&s), &cm),
                    );
                    same_vec(&R::mat_eval(&m, &v), &C::mat4_eval(&cm, &cv));
                    same_vec(&R::mat_eval_t(&v, &m), &C::mat4_eval_t(&cv, &cm));
                    same(&R::qf_eval(&m, &v), &C::qf_eval(&cm, &cv));
                }
            }

            #[test]
            fn products_and_sums_match_reference() {
                let p = pool(5, CAP);
                let mut rng = Rng::new(6);
                for _ in 0..SAMPLES {
                    let (alg, calg) = pick_alg(&p, &mut rng);
                    let (a, b) = (pick_elem(&p, &mut rng), pick_elem(&p, &mut rng));
                    let (x, y) = (elem_c(&a), elem_c(&b));
                    same_vec(
                        &alg.coord_mul(&a.coord, &b.coord),
                        &C::coord_mul(&x.coord, &y.coord, &calg),
                    );
                    same_elem(&alg.mul(&a, &b), &C::mul(&x, &y, &calg));
                    let (ra, rb) = E::equal_denom(&a, &b);
                    let (ca, cb) = C::equal_denom(&x, &y);
                    same_elem(&ra, &ca);
                    same_elem(&rb, &cb);
                    same_elem(&E::add(&a, &b), &C::add(&x, &y));
                    same_elem(&a.conj(), &C::conj(&x));
                    let s = pick(&p, &mut rng);
                    same_elem(&a.scalar_mul(&s), &C::elem_scalar_mul(&to_c(&s), &x));
                }
            }

            #[test]
            fn norms_and_normal_forms_match_reference() {
                let p = pool(7, CAP / 4);
                let mut rng = Rng::new(8);
                for c4 in [[0; 4], [1, -2, 3, -4]] {
                    for d in [1, -6, i32::MAX] {
                        let e = E::from_i32(d, c4);
                        let x = C::elem_set(d, c4);
                        same_elem(&e, &x);
                        assert_eq!(e.is_zero(), C::elem_is_zero(&x) == 1);
                    }
                }
                let default = E::default();
                same(&default.denom, &c::mp::set(1, 2));
                same_vec(&default.coord, &[c::mp::Ibz::default(); 4]);
                for i in 0..SAMPLES * 2 {
                    let (alg, calg) = pick_alg(&p, &mut rng);
                    let mut a = pick_elem(&p, &mut rng);
                    // A common factor to cancel, against either sign of the
                    // denominator.
                    let g = pick_nonzero(&p, &mut rng).div_2exp(CAP / 8);
                    if i % 2 == 0 && !g.is_zero() {
                        a = a.scalar_mul(&g);
                        a.denom = a.denom * g;
                    }
                    if i % 5 == 0 {
                        a.coord = [I::ZERO; 4];
                    }
                    let x = elem_c(&a);
                    let (num, den) = alg.norm(&a);
                    let (cnum, cden) = C::norm(&x, &calg);
                    same(&num, &cnum);
                    same(&den, &cden);
                    let mut n = a;
                    n.normalize();
                    same_elem(&n, &C::normalize(&x));
                }
            }
        }
    };
}

quat_algebra_suite!(p324_3, sqisign::gf::P324);
quat_algebra_suite!(p500_27, sqisign::gf::P500);
quat_algebra_suite!(p664_17, sqisign::gf::P664);
