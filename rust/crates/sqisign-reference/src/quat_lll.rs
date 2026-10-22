//! Bindings for the C lattice reductions (`lll/*.c`, `ibz_cornacchia_prime`),
//! expanded inside each level module next to `quat`.

macro_rules! lll_api {
    ($lvl:literal) => {
        /// C reference lattice reductions.
        pub mod lll {
            use super::mp::Ibz;
            use super::lattice::Lattice;
            use super::quat::{Alg, Mat2, Mat4, Vec4};
            use core::ffi::c_int;

            mod ffi {
                use super::{c_int, Alg, Ibz, Lattice, Mat2, Mat4, Vec4};
                ext!($lvl; quat_lll_reduce_O0_ideal(r: *mut Lattice, h: *const Lattice, a: *const Alg,
                                                    bound: c_int));
                ext!($lvl; quat_lll_dim2_short_basis(r: *mut Mat2, b: *const Mat2, det_bits: c_int));
                ext!($lvl; quat_lll_dim2_sumofsquares(x: *mut Ibz, y: *mut Ibz, p: *const Ibz,
                                                      r: *const Ibz));
                ext!($lvl; quat_lll_dual_reduce_ideal(r: *mut Lattice, g: *mut Ibz, u: *mut Mat4,
                                                      h: *const Lattice, a: *const Alg));
                ext!($lvl; quat_lattice_bound_parallelogram(b: *mut Vec4, u: *mut Mat4,
                                                            l: *const Lattice, r: *const Ibz)
                     -> c_int);
                ext!($lvl; ibz_cornacchia_prime(x: *mut Ibz, y: *mut Ibz, p: *const Ibz) -> c_int);
            }

            /// C `quat_lll_dim2_short_basis`.
            pub fn dim2_short_basis(b: &Mat2, det_bits: c_int) -> Mat2 {
                out!(quat_lll_dim2_short_basis(b, det_bits))
            }
            /// C `quat_lll_dim2_sumofsquares`.
            pub fn dim2_sumofsquares(p: &Ibz, r: &Ibz) -> (Ibz, Ibz) {
                let (mut x, mut y) = (Ibz::default(), Ibz::default());
                // SAFETY: valid values; outputs are fresh.
                unsafe { ffi::quat_lll_dim2_sumofsquares(&mut x, &mut y, p, r) };
                (x, y)
            }
            /// C `quat_lll_reduce_O0_ideal`.
            pub fn reduce_o0_ideal(h: &Lattice, a: &Alg, bound: c_int) -> Lattice {
                out!(quat_lll_reduce_O0_ideal(h, a, bound))
            }
            /// C `quat_lll_dual_reduce_ideal`: the reduced lattice, the
            /// dual Gram diagonal and the transform.
            pub fn dual_reduce_ideal(h: &Lattice, a: &Alg) -> (Lattice, Vec4, Mat4) {
                let (mut r, mut g, mut u) =
                    (Lattice::default(), Vec4::default(), Mat4::default());
                // SAFETY: valid values; outputs are fresh, `g` holds four.
                unsafe { ffi::quat_lll_dual_reduce_ideal(&mut r, g.as_mut_ptr(), &mut u, h, a) };
                (r, g, u)
            }
            /// C `quat_lattice_bound_parallelogram`.
            pub fn bound_parallelogram(l: &Lattice, radius: &Ibz) -> (Vec4, Mat4, c_int) {
                let (mut b, mut u) = (Vec4::default(), Mat4::default());
                // SAFETY: valid values; outputs are fresh.
                let ok = unsafe { ffi::quat_lattice_bound_parallelogram(&mut b, &mut u, l, radius) };
                (b, u, ok)
            }
            /// C `ibz_cornacchia_prime`, with the outputs it leaves.
            pub fn cornacchia_prime(p: &Ibz) -> (Ibz, Ibz, c_int) {
                let (mut x, mut y) = (Ibz::default(), Ibz::default());
                // SAFETY: valid values; outputs are fresh.
                let ok = unsafe { ffi::ibz_cornacchia_prime(&mut x, &mut y, p) };
                (x, y, ok)
            }
        }
    };
}
