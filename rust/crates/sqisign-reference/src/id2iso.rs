//! Bindings for the C ideal-to-isogeny translation (`id2iso.c`,
//! `dim2id2iso.c`) and `CURVE_WITH_ENDOMORPHISMS`, expanded inside each level
//! module next to `ec`, `hd` and `ideal`. Every draw is from the default
//! domain.

macro_rules! id2iso_api {
    ($lvl:literal) => {
        /// C reference ideal-to-isogeny translation.
        pub mod id2iso {
            use super::ec::{Basis, Curve};
            use super::ideal::Ideal;
            use super::mp::Ibz;
            use super::quat::{AlgElem, Mat2, Vec2};
            use core::ffi::c_int;

            /// C `curve_with_endomorphism_ring_t`.
            #[repr(C)]
            pub struct CurveWithEndomorphisms {
                /// `curve`.
                pub curve: Curve,
                /// `basis_even`.
                pub basis_even: Basis,
                /// `action_i`, `action_j`, `action_k`, `action_gen2`,
                /// `action_gen3`, `action_gen4`.
                pub actions: [Mat2; 6],
            }

            mod ffi {
                use super::{c_int, AlgElem, Basis, Curve, CurveWithEndomorphisms, Ibz, Ideal, Mat2,
                            Vec2};
                extern "C" {
                    #[link_name = concat!("sqisign_", $lvl, "_ref_CURVE_WITH_ENDOMORPHISMS")]
                    pub static CURVE_WITH_ENDOMORPHISMS: CurveWithEndomorphisms;
                }
                ext!($lvl; id2iso_matrix_application_even_basis(b: *mut Basis, e: *const Curve,
                     m: *mut Mat2, f: c_int, last: bool) -> c_int);
                ext!($lvl; id2iso_endomorphism_application_even_basis(b: *mut Basis,
                     e: *const Curve, t: *const AlgElem, f: c_int, last: bool));
                ext!($lvl; id2iso_kernel_dlogs_to_ideal_even(i: *mut Ideal, s: *mut AlgElem,
                     v: *const Vec2, f: c_int));
                ext!($lvl; id2iso_change_of_basis_matrix_tate(m: *mut Mat2, b1: *const Basis,
                     b2: *const Basis, e: *mut Curve, f: c_int));
                ext!($lvl; id2iso_change_of_basis_matrix_tate_invert(m: *mut Mat2,
                     b1: *const Basis, b2: *const Basis, e: *mut Curve, f: c_int));
                ext!($lvl; dim2id2iso_ideal_to_isogeny_qlapoty(b: *mut AlgElem, d: *mut Ibz,
                     e: *mut Curve, bas: *mut Basis, i: *const Ideal) -> c_int);
                ext!($lvl; dim2id2iso_arbitrary_isogeny_evaluation(bas: *mut Basis, e: *mut Curve,
                     i: *const Ideal) -> c_int);
            }

            /// C `CURVE_WITH_ENDOMORPHISMS`.
            pub fn curve_with_endomorphisms() -> &'static CurveWithEndomorphisms {
                // SAFETY: an initialised immutable C constant.
                unsafe { &ffi::CURVE_WITH_ENDOMORPHISMS }
            }
            /// C `id2iso_matrix_application_even_basis`: the basis and the
            /// reduced matrix.
            pub fn matrix_application_even_basis(bas: &Basis, e: &Curve, mat: &Mat2, f: usize,
                                                 last: bool) -> (Basis, Mat2) {
                let (mut b, mut m) = (*bas, *mat);
                // SAFETY: valid values; outputs are copies.
                unsafe {
                    ffi::id2iso_matrix_application_even_basis(&mut b, e, &mut m, f as c_int, last)
                };
                (b, m)
            }
            /// C `id2iso_endomorphism_application_even_basis`.
            pub fn endomorphism_application_even_basis(bas: &Basis, e: &Curve, theta: &AlgElem,
                                                       f: usize, last: bool) -> Basis {
                let mut b = *bas;
                // SAFETY: valid values; the output is a copy.
                unsafe {
                    ffi::id2iso_endomorphism_application_even_basis(&mut b, e, theta, f as c_int,
                                                                    last)
                };
                b
            }
            /// C `id2iso_kernel_dlogs_to_ideal_even`: the ideal and split.
            pub fn kernel_dlogs_to_ideal_even(v: &Vec2, f: usize) -> (Ideal, AlgElem) {
                let (mut i, mut s) = (Ideal::default(), AlgElem::default());
                // SAFETY: valid values; outputs are fresh.
                unsafe { ffi::id2iso_kernel_dlogs_to_ideal_even(&mut i, &mut s, v, f as c_int) };
                (i, s)
            }
            /// C `id2iso_change_of_basis_matrix_tate`: the matrix and the
            /// updated curve.
            pub fn change_of_basis_matrix_tate(b1: &Basis, b2: &Basis, e: &Curve, f: usize)
                -> (Mat2, Curve) {
                let (mut m, mut c) = (Mat2::default(), *e);
                // SAFETY: valid values; outputs are fresh or copies.
                unsafe { ffi::id2iso_change_of_basis_matrix_tate(&mut m, b1, b2, &mut c, f as c_int) };
                (m, c)
            }
            /// C `id2iso_change_of_basis_matrix_tate_invert`: the matrix and
            /// the updated curve.
            pub fn change_of_basis_matrix_tate_invert(b1: &Basis, b2: &Basis, e: &Curve, f: usize)
                -> (Mat2, Curve) {
                let (mut m, mut c) = (Mat2::default(), *e);
                // SAFETY: valid values; outputs are fresh or copies.
                unsafe {
                    ffi::id2iso_change_of_basis_matrix_tate_invert(&mut m, b1, b2, &mut c,
                                                                   f as c_int)
                };
                (m, c)
            }
            /// C `dim2id2iso_ideal_to_isogeny_qlapoty`: `beta1`, `d1`, the
            /// codomain, the basis and the status.
            pub fn ideal_to_isogeny_qlapoty(i: &Ideal) -> (AlgElem, Ibz, Curve, Basis, c_int) {
                let (mut b, mut d, mut e, mut bas) =
                    (AlgElem::default(), Ibz::default(), Curve::default(), Basis::default());
                // SAFETY: valid values, a seeded default domain; outputs are fresh.
                let ok = unsafe {
                    ffi::dim2id2iso_ideal_to_isogeny_qlapoty(&mut b, &mut d, &mut e, &mut bas, i)
                };
                (b, d, e, bas, ok)
            }
            /// C `dim2id2iso_arbitrary_isogeny_evaluation`: the basis, the
            /// codomain and the status.
            pub fn arbitrary_isogeny_evaluation(i: &Ideal) -> (Basis, Curve, c_int) {
                let (mut bas, mut e) = (Basis::default(), Curve::default());
                // SAFETY: valid values, a seeded default domain; outputs are fresh.
                let ok = unsafe { ffi::dim2id2iso_arbitrary_isogeny_evaluation(&mut bas, &mut e, i) };
                (bas, e, ok)
            }
        }
    };
}
