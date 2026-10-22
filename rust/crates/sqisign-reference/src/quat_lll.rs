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
                use super::{c_int, Alg, Gram2, Ibz, Lattice, Mat2, Mat4, Vec4};
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
                ext!($lvl; test_partial_xgcd_msb(a: u64, b: u64, s: u64, m: *mut [[i32; 2]; 2],
                                                 w: u64) -> c_int);
                ext!($lvl; test_sizered_msb(a: u64, b: u64, c: i64, d: i64) -> i32);
                ext!($lvl; test_partial_xgcd_lehmer(a: *mut Ibz, b: *mut Ibz, c: *mut Ibz,
                                                    d: *mut Ibz, outer: c_int, det: c_int));
                ext!($lvl; test_sizered_lehmer(a: *mut Ibz, b: *mut Ibz, c: *mut Ibz,
                                               d: *mut Ibz, outer: c_int, det: c_int));
                ext!($lvl; quat_lll_gram_lehmer(g: *mut Gram2, u: *mut [[Ibz; 2]; 2],
                                                outer: c_int));
                ext!($lvl; test_LG_gaussian_inner(v: *const [[i64; 2]; 4],
                                                  m: *mut [[[i32; 2]; 2]; 2], w: u64, f1: i32,
                                                  f2: i32) -> c_int);
                ext!($lvl; test_fast_log2_ratio(a: u64, b: u64, clz_b: c_int) -> c_int);
                ext!($lvl; test_partial_gram_msb(a: u64, b: i64, c: u64, m: *mut [[i32; 2]; 2],
                                                 w: u64) -> c_int);
            }

            /// C `static partial_xgcd_msb` (`lehmer_xgcd.c`): the transform.
            pub fn partial_xgcd_msb(a: u64, b: u64, sqrta: u64, swap: bool) -> [[i32; 2]; 2] {
                let mut m = [[0; 2]; 2];
                // SAFETY: `m` is a valid 2x2 output.
                unsafe { ffi::test_partial_xgcd_msb(a, b, sqrta, &mut m, swap.into()) };
                m
            }
            /// C `static sizered_msb` (`lehmer_xgcd.c`).
            pub fn sizered_msb(a: u64, b: u64, c: i64, d: i64) -> i32 {
                // SAFETY: pure function of its arguments.
                unsafe { ffi::test_sizered_msb(a, b, c, d) }
            }
            /// C `quat_lll_gram2_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Default)]
            pub struct Gram2 {
                /// The first diagonal entry.
                pub a: Ibz,
                /// The off-diagonal entry.
                pub b: Ibz,
                /// The second diagonal entry.
                pub c: Ibz,
                /// The scale exponent.
                pub e: i32,
            }

            /// C `static partial_xgcd_lehmer` (`lehmer_xgcd.c`) on
            /// `[A, B, C, D]`.
            pub fn partial_xgcd_lehmer(q: &[Ibz; 4], outer: c_int, det: c_int) -> [Ibz; 4] {
                let [mut a, mut b, mut c, mut d] = *q;
                // SAFETY: four valid, distinct values.
                unsafe { ffi::test_partial_xgcd_lehmer(&mut a, &mut b, &mut c, &mut d, outer, det) };
                [a, b, c, d]
            }
            /// C `static sizered_lehmer` (`lehmer_xgcd.c`) on `[A, B, C, D]`.
            pub fn sizered_lehmer(q: &[Ibz; 4], outer: c_int, det: c_int) -> [Ibz; 4] {
                let [mut a, mut b, mut c, mut d] = *q;
                // SAFETY: four valid, distinct values.
                unsafe { ffi::test_sizered_lehmer(&mut a, &mut b, &mut c, &mut d, outer, det) };
                [a, b, c, d]
            }
            /// C `quat_lll_gram_lehmer`: the reduced Gram and the transform.
            pub fn gram_lehmer(g: &Gram2, outer: c_int) -> (Gram2, [[Ibz; 2]; 2]) {
                let (mut g, mut u) = (*g, [[Ibz::default(); 2]; 2]);
                // SAFETY: valid Gram; `u` is a fresh 2x2 output.
                unsafe { ffi::quat_lll_gram_lehmer(&mut g, &mut u, outer) };
                (g, u)
            }
            /// C `static LG_gaussian_inner` (`gaussian_xgcd.c`) on the
            /// `[re, im]` windows `a, b, c, d`: the transform.
            pub fn gaussian_inner(v: [[i64; 2]; 4], swap: bool, f1: i32, f2: i32)
                -> [[[i32; 2]; 2]; 2] {
                let mut m = [[[0; 2]; 2]; 2];
                // SAFETY: `v` holds four values, `m` is a valid 2x2 output.
                unsafe { ffi::test_LG_gaussian_inner(&v, &mut m, swap.into(), f1, f2) };
                m
            }
            /// C `static fast_log2_ratio` (`lll_lg2.c`).
            pub fn fast_log2_ratio(a: u64, b: u64, clz_b: u32) -> i32 {
                // SAFETY: pure function of its arguments.
                unsafe { ffi::test_fast_log2_ratio(a, b, clz_b as c_int) }
            }
            /// C `static partial_gram_msb` (`lll_lg2.c`): the transform.
            pub fn partial_gram_msb(a: u64, b: i64, c: u64, swap: bool) -> [[i32; 2]; 2] {
                let mut m = [[0; 2]; 2];
                // SAFETY: `m` is a valid 2x2 output.
                unsafe { ffi::test_partial_gram_msb(a, b, c, &mut m, swap.into()) };
                m
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
