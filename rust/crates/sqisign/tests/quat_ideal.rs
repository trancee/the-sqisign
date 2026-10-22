//! Differential tests: Rust `quat` ideal arithmetic and construction against
//! the vendor C reference.

#[macro_use]
mod support;

macro_rules! quat_ideal_suite {
    ($module:ident, $params:ty) => {
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

            /// A signed value below `2^bits`, zero one time in eight.
            fn signed(bits: usize, rng: &mut Rng) -> I {
                if rng.next_u64() % 8 == 0 {
                    return I::ZERO;
                }
                let w: Vec<u64> = (0..c::mp::IBZ_NLIMBS).map(|_| rng.next_u64()).collect();
                let x = I::from_bits(&w, 1 + rng.next_u64() as usize % bits);
                if rng.next_u64() & 1 == 0 {
                    x
                } else {
                    -x
                }
            }

            /// A random element of `O0` with integral coordinates below
            /// `2^bits`, or half-integral ones one time in four.
            fn gen(bits: usize, rng: &mut Rng) -> AlgElem<P> {
                let mut g = AlgElem::from_i32(1, [0; 4]);
                g.coord = core::array::from_fn(|_| signed(bits, rng));
                if rng.next_u64() % 4 == 0 {
                    // (a + c j) / 2 and (b + d ij) / 2 lie in O0 for a = c, b = d mod 2.
                    g.coord[2] = g.coord[1] + I::TWO * signed(bits - 1, rng);
                    g.coord[3] = g.coord[0] + I::TWO * signed(bits - 1, rng);
                    g.denom = I::TWO;
                }
                g
            }

            /// A primitive Gaussian integer `u + v i` with norm coprime to
            /// `n`.
            fn split(n: &I, rng: &mut Rng) -> AlgElem<P> {
                loop {
                    let mut s = AlgElem::from_i32(1, [0; 4]);
                    s.coord[0] = signed(40, rng);
                    s.coord[1] = signed(40, rng);
                    let norm = I::sum_two_squares(&s.coord[0], &s.coord[1]);
                    if s.coord[0].gcd(&s.coord[1]).is_one() && norm.gcd(n).is_one() {
                        return s;
                    }
                }
            }

            #[test]
            fn prime_cofactor_matches_reference() {
                same(&P::prime_cofactor(), &C::prime_cofactor());
            }

            #[test]
            fn ideal_products_match_reference() {
                let mut rng = Rng::new(1);
                for (k, bits) in norm_bits().into_iter().enumerate() {
                    for [(a, ca), (b, cb)] in ideal_pairs(bits, SAMPLES / 8, 50 + k as u8) {
                        same_lattice(&a.to_lattice(), &C::to_lattice(&ca));
                        same_elem(&a.odd_inert_gen(), &C::odd_inert_gen(&ca));
                        let l = a.mul_o0(&b);
                        let cl = C::mul_o0(&ca, &cb);
                        same_lattice(&l, &cl);
                        same_mat(&l.product_gram_matrix(), &C::product_gram_matrix(&cl));
                        same_ideal(&a.intersect_o0(&b, None), &C::intersect_o0(&ca, &cb, None));
                        let s = split(&b.norm, &mut rng);
                        let cs = elem_c(&s);
                        same_ideal(
                            &a.intersect_o0(&b, Some(&s)),
                            &C::intersect_o0(&ca, &cb, Some(&cs)),
                        );
                        same_lattice(&a.reduce_basis(), &C::reduce_basis(&ca));
                        let (i, ci) = (a.odd_inert_gen(), C::odd_inert_gen(&ca));
                        same_opt_ideal(
                            &Ideal::create_o0_inert_odd(&i, &a.norm),
                            &C::create_o0_inert_odd(&ci, &ca.norm).0,
                        );
                    }
                }
            }

            #[test]
            fn created_ideals_match_reference() {
                let alg = Alg::<P>::pinfty();
                let mut rng = Rng::new(2);
                for bits in [2, 5, 20, 64, P::P_BITS / 4] {
                    for _ in 0..SAMPLES {
                        let g = gen(bits, &mut rng);
                        let (n, d) = alg.norm(&g);
                        let n = n.div_rem(&d).0;
                        if n.is_zero() {
                            continue;
                        }
                        let cg = elem_c(&g);
                        let odd = n.div_2exp(n.two_adic());
                        let (ci, cs) = C::create_o0_odd(&cg, &to_c(&odd));
                        let (sn, _) = alg.norm(&elem(&cs));
                        if !odd.div_rem(&sn).1.is_zero() {
                            // C asserts here; see `quat_response`'s panic test.
                            continue;
                        }
                        let (i, s) = Ideal::create_o0_odd(&g, &odd);
                        same_opt_ideal(&i, &ci);
                        same_elem(&s, &cs);
                        if i.is_none() {
                            // C `quat_ideal_create_O0_inert` would reduce modulo zero.
                            continue;
                        }
                        let (i, inert) = Ideal::create_o0_inert(&g, &n);
                        let (ci, cinert) = C::create_o0_inert(&cg, &to_c(&n));
                        same_ideal(&i, &ci);
                        assert_eq!(inert, cinert != 0);
                        let e = I::ONE.mul_2exp(rng.next_u64() as usize % 12);
                        let (ci, ok) = C::create_o0_pow_two(&cg, &to_c(&e));
                        match Ideal::create_o0_pow_two(&g, &e) {
                            Some(i) => same_ideal(&i, &ci),
                            None => assert_eq!(ok, 0),
                        }
                    }
                }
            }
        }
    };
}

quat_ideal_suite!(p324_3, sqisign::gf::P324);
quat_ideal_suite!(p500_27, sqisign::gf::P500);
quat_ideal_suite!(p664_17, sqisign::gf::P664);
