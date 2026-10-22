//! Differential tests: Rust `quat` lattices and the standard order `O0`
//! against the vendor C reference.

#[macro_use]
mod support;

macro_rules! quat_lattice_suite {
    ($module:ident, $params:ty) => {
        mod $module {
            use super::support::{Rng, SAMPLES};
            use c::lattice as C;
            use sqisign::mp::Ibz;
            use sqisign::quat::{dim4, Alg, AlgElem, Lattice};
            use sqisign_reference::$module as c;

            type I = Ibz<$params>;
            type E = AlgElem<$params>;
            type L = Lattice<$params>;
            mp_helpers!();
            quat_helpers!();

            fn elem_c(e: &E) -> c::quat::AlgElem {
                c::quat::AlgElem {
                    denom: to_c(&e.denom),
                    coord: vec_c(&e.coord),
                }
            }

            fn same_elem(r: &E, x: &c::quat::AlgElem) {
                same(&r.denom, &x.denom);
                same_vec(&r.coord, &x.coord);
            }

            fn lat_c(l: &L) -> C::Lattice {
                C::Lattice {
                    denom: to_c(&l.denom),
                    basis: mat_c(&l.basis),
                }
            }

            fn same_lat(r: &L, x: &C::Lattice) {
                same(&r.denom, &x.denom);
                same_mat(&r.basis, &x.basis);
            }

            /// An upper-triangular lattice with a non-zero diagonal.
            fn pick_lattice(p: &[I], rng: &mut Rng) -> L {
                let mut basis = pick_mat::<4>(p, rng);
                for (i, row) in basis.iter_mut().enumerate() {
                    row[..i].iter_mut().for_each(|x| *x = I::ZERO);
                    row[i] = pick_nonzero(p, rng);
                }
                L {
                    denom: pick_nonzero(p, rng),
                    basis,
                }
            }

            /// `k * (basis * v) / (k * denom)`: an element of `lat`, sometimes
            /// with a common factor.
            fn member(lat: &L, p: &[I], rng: &mut Rng) -> E {
                let v = pick_vec::<4>(p, rng);
                let k = if rng.next_u64() & 1 == 0 {
                    I::ONE
                } else {
                    pick_nonzero(p, rng)
                };
                E {
                    denom: lat.denom * k,
                    coord: dim4::vec_scalar_mul(&k, &dim4::mat_eval(&lat.basis, &v)),
                }
            }

            #[test]
            fn constants_match_reference() {
                same(&Alg::<$params>::pinfty().p, &C::pinfty().p);
                same_lat(&L::o0(), &C::maxord_o0());
                same_lat(&L::o0(), &C::o0_set());
            }

            #[test]
            fn membership_matches_reference() {
                let p = pool(3, CAP / 8);
                let mut rng = Rng::new(4);
                for i in 0..SAMPLES {
                    let lat = pick_lattice(&p, &mut rng);
                    let mut x = member(&lat, &p, &mut rng);
                    if i % 3 == 0 {
                        x.coord[(i / 3) % 4] = x.coord[(i / 3) % 4] + I::ONE;
                    }
                    if i % 7 == 0 {
                        x.coord = [I::ZERO; 4];
                    }
                    let (coords, ok) = lat.contains(&x);
                    let (cc, cok) = C::contains(&lat_c(&lat), &elem_c(&x));
                    same_vec(&coords, &cc);
                    assert_eq!(ok, cok != 0);
                    let (prim, content) = x.make_primitive(&lat);
                    let (cprim, ccontent) = C::make_primitive(&elem_c(&x), &lat_c(&lat));
                    same_vec(&prim, &cprim);
                    same(&content, &ccontent);
                }
            }

            #[test]
            fn standard_order_matches_reference() {
                let p = pool(5, CAP / 4);
                let mut rng = Rng::new(6);
                let o0 = L::o0();
                for i in 0..SAMPLES {
                    let x = member(&o0, &p, &mut rng);
                    let cx = elem_c(&x);
                    same_vec(&x.to_o0_basis(), &C::change_to_o0_basis(&cx));
                    let m = if i % 4 == 0 {
                        I::from_i64(1, 2)
                    } else {
                        pick_nonzero(&p, &mut rng).abs()
                    };
                    same_elem(&x.mod_o0(&m), &C::mod_o0(&cx, &to_c(&m)));
                }
            }
        }
    };
}

quat_lattice_suite!(p324_3, sqisign::gf::P324);
quat_lattice_suite!(p500_27, sqisign::gf::P500);
quat_lattice_suite!(p664_17, sqisign::gf::P664);
