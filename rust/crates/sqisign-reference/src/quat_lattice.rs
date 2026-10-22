//! Bindings for the C lattices and the standard order (`lattice.c`,
//! `stdorder.c`, `quat_alg_make_primitive`), expanded inside each level
//! module next to `quat`.

/// Declares the level-prefixed C constant `$name`.
macro_rules! ext_static {
    ($lvl:literal; $name:ident : $ty:ty) => {
        extern "C" {
            #[link_name = concat!("sqisign_", $lvl, "_ref_", stringify!($name))]
            pub static $name: $ty;
        }
    };
}

macro_rules! lattice_api {
    ($lvl:literal) => {
        /// C reference lattices and standard order.
        pub mod lattice {
            use super::mp::Ibz;
            use super::quat::{Alg, AlgElem, Mat4, Vec4};
            use core::ffi::c_int;

            /// C `quat_lattice_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Lattice {
                /// The denominator.
                pub denom: Ibz,
                /// The basis vectors as columns.
                pub basis: Mat4,
            }

            mod ffi {
                use super::{c_int, Alg, AlgElem, Ibz, Lattice, Vec4};
                ext_static!($lvl; QUATALG_PINFTY: Alg);
                ext_static!($lvl; MAXORD_O0: Lattice);
                ext!($lvl; quat_lattice_contains(c: *mut Vec4, l: *const Lattice,
                                                 x: *const AlgElem) -> c_int);
                ext!($lvl; quat_alg_make_primitive(x: *mut Vec4, c: *mut Ibz, a: *const AlgElem,
                                                   o: *const Lattice));
                ext!($lvl; quat_lattice_O0_set(o: *mut Lattice));
                ext!($lvl; quat_change_to_O0_basis(v: *mut Vec4, x: *const AlgElem));
                ext!($lvl; quat_mod_O0(r: *mut AlgElem, x: *const AlgElem, m: *const Ibz));
            }

            /// C `QUATALG_PINFTY`.
            pub fn pinfty() -> Alg {
                // SAFETY: an initialised C constant.
                unsafe { ffi::QUATALG_PINFTY }
            }
            /// C `MAXORD_O0`.
            pub fn maxord_o0() -> Lattice {
                // SAFETY: an initialised C constant.
                unsafe { ffi::MAXORD_O0 }
            }
            /// C `quat_lattice_contains`.
            pub fn contains(l: &Lattice, x: &AlgElem) -> (Vec4, c_int) {
                let mut c = Vec4::default();
                // SAFETY: valid values; the output is fresh.
                let ok = unsafe { ffi::quat_lattice_contains(&mut c, l, x) };
                (c, ok)
            }
            /// C `quat_alg_make_primitive`.
            pub fn make_primitive(x: &AlgElem, o: &Lattice) -> (Vec4, Ibz) {
                let (mut v, mut c) = (Vec4::default(), Ibz::default());
                // SAFETY: valid values; outputs are fresh.
                unsafe { ffi::quat_alg_make_primitive(&mut v, &mut c, x, o) };
                (v, c)
            }
            /// C `quat_lattice_O0_set`.
            pub fn o0_set() -> Lattice {
                out!(quat_lattice_O0_set())
            }
            /// C `quat_change_to_O0_basis`.
            pub fn change_to_o0_basis(x: &AlgElem) -> Vec4 {
                out!(quat_change_to_O0_basis(x))
            }
            /// C `quat_mod_O0`.
            pub fn mod_o0(x: &AlgElem, m: &Ibz) -> AlgElem {
                out!(quat_mod_O0(x, m))
            }
        }
    };
}
