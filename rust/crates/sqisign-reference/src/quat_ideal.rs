//! Bindings for the C ideals of `O0` (`ideal.c`, `ibz_generate_random_prime`),
//! expanded inside each level module next to `quat`.

macro_rules! ideal_api {
    ($lvl:literal) => {
        /// C reference ideals.
        pub mod ideal {
            use super::lattice::Lattice;
            use super::mp::{Domain, Ibz};
            use super::quat::{AlgElem, Mat4};
            use core::ffi::c_int;
            use core::ptr::null_mut;

            /// C `quat_ideal_t`: an ideal of `O0` in inert HNF.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Ideal {
                /// The inert HNF `x`.
                pub x: Ibz,
                /// The inert HNF `y`.
                pub y: Ibz,
                /// The norm.
                pub norm: Ibz,
            }

            mod ffi {
                use super::{c_int, AlgElem, Domain, Ibz, Ideal, Lattice, Mat4};
                extern "C" {
                    #[link_name = concat!("sqisign_", $lvl, "_ref_QUAT_prime_cofactor")]
                    pub static QUAT_prime_cofactor: Ibz;
                    #[link_name = concat!("sqisign_", $lvl, "_ref_SEC_DEGREE")]
                    pub static SEC_DEGREE: Ibz;
                    #[link_name = concat!("sqisign_", $lvl, "_ref_COM_DEGREE")]
                    pub static COM_DEGREE: Ibz;
                }
                ext!($lvl; quat_to_lattice(l: *mut Lattice, i: *const Ideal));
                ext!($lvl; quat_ideal_odd_inert_gen(g: *mut AlgElem, i: *const Ideal));
                ext!($lvl; quat_ideal_intersect_O0(r: *mut Ideal, a: *const Ideal, b: *const Ideal,
                                                   s: *const AlgElem));
                ext!($lvl; quat_ideal_product_gram_matrix(g: *mut Mat4, l: *const Lattice));
                ext!($lvl; quat_ideal_create_O0_inert_odd(i: *mut Ideal, g: *const AlgElem,
                                                          n: *const Ibz) -> c_int);
                ext!($lvl; quat_ideal_create_O0_odd(i: *mut Ideal, s: *mut AlgElem,
                                                    g: *const AlgElem, n: *const Ibz));
                ext!($lvl; quat_ideal_create_O0_pow_two(i: *mut Ideal, g: *const AlgElem,
                                                        n: *const Ibz) -> c_int);
                ext!($lvl; quat_ideal_create_O0_inert(i: *mut Ideal, g: *const AlgElem,
                                                      n: *const Ibz) -> c_int);
                ext!($lvl; quat_represent_integer(g: *mut AlgElem, n: *const Ibz, d: *mut Domain)
                     -> c_int);
                ext!($lvl; quat_random_ideal_O0_given_arbitrary_odd_norm(i: *mut Ideal,
                     s: *mut AlgElem, n: *const Ibz, sh: *const AlgElem, d: *mut Domain) -> c_int);
                ext!($lvl; quat_ideal_reduce_basis(l: *mut Lattice, i: *const Ideal));
                ext!($lvl; quat_ideal_shortest_equivalent(g: *mut AlgElem, e: *mut AlgElem,
                                                          r: *mut Ideal, i: *const Ideal));
                ext!($lvl; quat_ideal_small_equivalent_coprime(g: *mut AlgElem, e: *mut Ideal,
                     i: *const Ideal, c: *const Ibz, d: *mut Domain) -> c_int);
                ext!($lvl; quat_lattice_sample_from_ball(r: *mut AlgElem, n: *mut Ibz,
                     l: *const Lattice, rad: *const Ibz, d: *mut Domain) -> c_int);
                ext!($lvl; quat_response_element(skc: *mut Ideal, resp: *mut AlgElem, n: *mut Ibz,
                     skq: *mut AlgElem, sk: *const Ideal, ch: *const Ideal, s: *const AlgElem,
                     com: *const Ideal, d: *mut Domain, e: c_int));
                ext!($lvl; ibz_generate_random_prime(p: *mut Ibz, is3mod4: c_int, bits: c_int)
                     -> c_int);
                ext!($lvl; quat_random_ideal_O0_given_prime_norm(i: *mut Ideal, n: *const Ibz,
                                                                 d: *mut Domain) -> c_int);
                ext!($lvl; quat_ideal_mul_O0(l: *mut Lattice, a: *const Ideal, b: *const Ideal));
            }

            /// C `ibz_generate_random_prime` on the default domain; its nonzero
            /// status is `ibz_probab_prime`'s.
            pub fn random_prime(is3mod4: bool, bits: c_int) -> Ibz {
                let mut p = Ibz::default();
                // SAFETY: a fresh output.
                let ok = unsafe { ffi::ibz_generate_random_prime(&mut p, is3mod4.into(), bits) };
                assert_ne!(ok, 0);
                p
            }
            /// C `quat_random_ideal_O0_given_prime_norm`.
            pub fn random_ideal_given_prime_norm(norm: &Ibz, d: &mut Domain) -> Ideal {
                let mut i = Ideal::default();
                // SAFETY: valid values, a seeded context and a fresh output.
                let ok = unsafe { ffi::quat_random_ideal_O0_given_prime_norm(&mut i, norm, d) };
                assert_eq!(ok, 1);
                i
            }
            /// C `quat_ideal_mul_O0`.
            pub fn mul_o0(a: &Ideal, b: &Ideal) -> Lattice {
                out!(quat_ideal_mul_O0(a, b))
            }
            /// C `QUAT_prime_cofactor`.
            pub fn prime_cofactor() -> Ibz {
                // SAFETY: an immutable C global.
                unsafe { ffi::QUAT_prime_cofactor }
            }
            /// C `SEC_DEGREE`, the prime norm of secret key ideals.
            pub fn sec_degree() -> Ibz {
                // SAFETY: an immutable C global.
                unsafe { ffi::SEC_DEGREE }
            }
            /// C `COM_DEGREE`, the prime norm of commitment ideals.
            pub fn com_degree() -> Ibz {
                // SAFETY: an immutable C global.
                unsafe { ffi::COM_DEGREE }
            }
            /// C `quat_to_lattice`.
            pub fn to_lattice(i: &Ideal) -> Lattice {
                out!(quat_to_lattice(i))
            }
            /// C `quat_ideal_odd_inert_gen`.
            pub fn odd_inert_gen(i: &Ideal) -> AlgElem {
                out!(quat_ideal_odd_inert_gen(i))
            }
            /// C `quat_ideal_intersect_O0`.
            pub fn intersect_o0(a: &Ideal, b: &Ideal, split: Option<&AlgElem>) -> Ideal {
                let s = split.map_or(core::ptr::null(), |s| s as *const AlgElem);
                out!(quat_ideal_intersect_O0(a, b, s))
            }
            /// C `quat_ideal_product_gram_matrix`.
            pub fn product_gram_matrix(l: &Lattice) -> Mat4 {
                out!(quat_ideal_product_gram_matrix(l))
            }
            /// C `quat_ideal_create_O0_inert_odd`, with its status.
            pub fn create_o0_inert_odd(g: &AlgElem, n: &Ibz) -> (Ideal, c_int) {
                let mut i = Ideal::default();
                // SAFETY: valid values and a fresh output.
                let ok = unsafe { ffi::quat_ideal_create_O0_inert_odd(&mut i, g, n) };
                (i, ok)
            }
            /// C `quat_ideal_create_O0_odd`: the ideal and the split part.
            pub fn create_o0_odd(g: &AlgElem, n: &Ibz) -> (Ideal, AlgElem) {
                let (mut i, mut s) = (Ideal::default(), AlgElem::default());
                // SAFETY: valid values and fresh outputs.
                unsafe { ffi::quat_ideal_create_O0_odd(&mut i, &mut s, g, n) };
                (i, s)
            }
            /// C `quat_ideal_create_O0_pow_two`, with its status.
            pub fn create_o0_pow_two(g: &AlgElem, n: &Ibz) -> (Ideal, c_int) {
                let mut i = Ideal::default();
                // SAFETY: valid values and a fresh output.
                let ok = unsafe { ffi::quat_ideal_create_O0_pow_two(&mut i, g, n) };
                (i, ok)
            }
            /// C `quat_ideal_create_O0_inert`, with whether it is inert.
            pub fn create_o0_inert(g: &AlgElem, n: &Ibz) -> (Ideal, c_int) {
                let mut i = Ideal::default();
                // SAFETY: valid values and a fresh output.
                let ok = unsafe { ffi::quat_ideal_create_O0_inert(&mut i, g, n) };
                (i, ok)
            }
            /// C `quat_represent_integer`, with its status.
            pub fn represent_integer(n: &Ibz, d: &mut Domain) -> (AlgElem, c_int) {
                let mut g = AlgElem::default();
                // SAFETY: valid values, a seeded context and a fresh output.
                let ok = unsafe { ffi::quat_represent_integer(&mut g, n, d) };
                (g, ok)
            }
            /// C `quat_random_ideal_O0_given_arbitrary_odd_norm`, with its
            /// status.
            pub fn random_given_odd_norm(
                n: &Ibz,
                shift: &AlgElem,
                d: &mut Domain,
            ) -> (Ideal, AlgElem, c_int) {
                let (mut i, mut s) = (Ideal::default(), AlgElem::default());
                // SAFETY: valid values, a seeded context and fresh outputs.
                let ok = unsafe {
                    ffi::quat_random_ideal_O0_given_arbitrary_odd_norm(&mut i, &mut s, n, shift, d)
                };
                (i, s, ok)
            }
            /// C `quat_ideal_reduce_basis`.
            pub fn reduce_basis(i: &Ideal) -> Lattice {
                out!(quat_ideal_reduce_basis(i))
            }
            /// C `quat_ideal_shortest_equivalent` without `gen`: the shortest
            /// element and the reduced ideal.
            pub fn shortest_equivalent(i: &Ideal) -> (AlgElem, Ideal) {
                let (mut e, mut r) = (AlgElem::default(), Ideal::default());
                // SAFETY: valid values and fresh outputs; `gen` may be null.
                unsafe { ffi::quat_ideal_shortest_equivalent(null_mut(), &mut e, &mut r, i) };
                (e, r)
            }
            /// C `quat_ideal_small_equivalent_coprime`: the element, the
            /// ideal and the status.
            pub fn small_equivalent_coprime(
                i: &Ideal,
                coprime_to: &Ibz,
                d: &mut Domain,
            ) -> (AlgElem, Ideal, c_int) {
                let (mut g, mut e) = (AlgElem::default(), i.clone());
                let e_ptr: *mut Ideal = &mut e;
                // SAFETY: valid values, a seeded context and a fresh `gen`;
                // `equiv` aliases `ideal` as at every C call site, which C
                // supports by reading `ideal` before writing `equiv`.
                let ok = unsafe {
                    ffi::quat_ideal_small_equivalent_coprime(&mut g, e_ptr, e_ptr, coprime_to, d)
                };
                (g, e, ok)
            }
            /// C `quat_lattice_sample_from_ball`: the element, its norm
            /// quotient and the status.
            pub fn sample_from_ball(l: &Lattice, radius: &Ibz, d: &mut Domain)
                -> (AlgElem, Ibz, c_int) {
                let (mut r, mut n) = (AlgElem::default(), Ibz::default());
                // SAFETY: valid values, a seeded context and fresh outputs.
                let ok = unsafe { ffi::quat_lattice_sample_from_ball(&mut r, &mut n, l, radius, d) };
                (r, n, ok)
            }
            /// C `quat_response_element`: the ideal, the response, its norm
            /// quotient and the equivalence element.
            pub fn response_element(
                sk: &Ideal,
                chall: &Ideal,
                split: &AlgElem,
                commit: &Ideal,
                d: &mut Domain,
                e: c_int,
            ) -> (Ideal, AlgElem, Ibz, AlgElem) {
                let (mut i, mut r, mut n, mut q) =
                    (Ideal::default(), AlgElem::default(), Ibz::default(), AlgElem::default());
                // SAFETY: valid values, a seeded context and fresh outputs.
                unsafe {
                    ffi::quat_response_element(
                        &mut i, &mut r, &mut n, &mut q, sk, chall, split, commit, d, e,
                    )
                };
                (i, r, n, q)
            }
        }
    };
}
