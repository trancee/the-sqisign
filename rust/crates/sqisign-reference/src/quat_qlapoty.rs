//! Bindings for the C qlapoty norm equation (`qlapoty.c`), expanded inside
//! each level module next to `ideal`. Every draw is from the default domain.

macro_rules! qlapoty_api {
    ($lvl:literal) => {
        /// C reference qlapoty.
        pub mod qlapoty {
            use super::ideal::Ideal;
            use super::mp::Ibz;
            use super::quat::{AlgElem, Mat2, Vec2};
            use core::ffi::c_int;

            mod ffi {
                use super::{c_int, AlgElem, Ibz, Ideal, Mat2, Vec2};
                ext!($lvl; quat_qlapoty_dim2_reduce_last_step(r: *mut Mat2, i: *const Mat2));
                ext!($lvl; quat_qlapoty_gen_to_dim2_lattice(l: *mut Mat2, t: *mut Vec2,
                     g: *const AlgElem, n: *const Ibz));
                ext!($lvl; quat_qlapoty_get_short_basis(b: *mut Mat2, t: *mut Vec2, st: *const Vec2,
                     a: *const AlgElem, n: *const Ibz));
                ext!($lvl; quat_qlapoty_initialize_bounds(ab: *mut Vec2, o: *mut Vec2,
                     g: *mut AlgElem, f: *mut u32, n: *const Ibz, li: *const Mat2,
                     twoe: *const Ibz) -> c_int);
                ext!($lvl; quat_qlapoty_loop_one(a: *mut AlgElem, s: *mut Vec2, g: *const AlgElem,
                     n: *const Ibz, ab: *const Vec2, o: *const Vec2, f: u32, big: c_int));
                ext!($lvl; quat_qlapoty_loop_two(l: *mut Ibz, s: *mut Ibz, t: *mut Ibz,
                     sc: *mut Ibz, tc: *mut Ibz, a: *const AlgElem, r: *const Ibz,
                     st: *const Vec2, n: *const Ibz, twoe: *const Ibz) -> c_int);
                ext!($lvl; quat_qlapoty_normeq(m1: *mut AlgElem, m2: *mut AlgElem,
                     th: *mut AlgElem, sm: *mut AlgElem, i: *const Ideal) -> c_int);
                ext!($lvl; quat_qlapoty(b: *mut AlgElem, d: *mut Ibz, th: *mut AlgElem,
                     i: *const Ideal) -> c_int);
            }

            /// C `quat_qlapoty_dim2_reduce_last_step`.
            pub fn dim2_reduce_last_step(m: &Mat2) -> Mat2 {
                out!(quat_qlapoty_dim2_reduce_last_step(m))
            }
            /// C `quat_qlapoty_gen_to_dim2_lattice`: the basis and target.
            pub fn gen_to_dim2_lattice(gen: &AlgElem, n: &Ibz) -> (Mat2, Vec2) {
                let (mut l, mut t) = (Mat2::default(), Vec2::default());
                // SAFETY: valid values; outputs are fresh.
                unsafe { ffi::quat_qlapoty_gen_to_dim2_lattice(&mut l, &mut t, gen, n) };
                (l, t)
            }
            /// C `quat_qlapoty_get_short_basis`: the basis and target.
            pub fn get_short_basis(st: &Vec2, alpha0: &AlgElem, n: &Ibz) -> (Mat2, Vec2) {
                let (mut b, mut t) = (Mat2::default(), Vec2::default());
                // SAFETY: valid values; outputs are fresh.
                unsafe { ffi::quat_qlapoty_get_short_basis(&mut b, &mut t, st, alpha0, n) };
                (b, t)
            }
            /// C `quat_qlapoty_initialize_bounds`: `ab`, `omega`, the updated
            /// `gen`, `f_and_big_interval` and the status.
            pub fn initialize_bounds(gen: &AlgElem, n: &Ibz, li: &Mat2, twoe: &Ibz)
                -> (Vec2, Vec2, AlgElem, [u32; 2], c_int) {
                let (mut ab, mut o, mut g, mut f) =
                    (Vec2::default(), Vec2::default(), gen.clone(), [0u32; 2]);
                // SAFETY: valid values; `f` holds two; outputs are fresh or copies.
                let ok = unsafe {
                    ffi::quat_qlapoty_initialize_bounds(&mut ab, &mut o, &mut g, f.as_mut_ptr(),
                                                        n, li, twoe)
                };
                (ab, o, g, f, ok)
            }
            /// C `quat_qlapoty_loop_one`: `alpha0` and the short solution.
            pub fn loop_one(gen: &AlgElem, n: &Ibz, ab: &Vec2, omega: &Vec2, f: u32, big: bool)
                -> (AlgElem, Vec2) {
                let (mut a, mut s) = (AlgElem::default(), Vec2::default());
                // SAFETY: valid values, a seeded default domain; outputs are fresh.
                unsafe {
                    ffi::quat_qlapoty_loop_one(&mut a, &mut s, gen, n, ab, omega, f, big.into())
                };
                (a, s)
            }
            /// C `quat_qlapoty_loop_two`: `[lambda, s, t, sc, tc]` and the status.
            pub fn loop_two(alpha0: &AlgElem, r: &Ibz, st0: &Vec2, n: &Ibz, twoe: &Ibz)
                -> ([Ibz; 5], c_int) {
                let mut o: [Ibz; 5] = Default::default();
                let [l, s, t, sc, tc] = &mut o;
                // SAFETY: valid values, a seeded default domain; outputs are fresh.
                let ok = unsafe {
                    ffi::quat_qlapoty_loop_two(l, s, t, sc, tc, alpha0, r, st0, n, twoe)
                };
                (o, ok)
            }
            /// C `quat_qlapoty_normeq`: `[mu1, mu2, theta, smallest]` and the status.
            pub fn normeq(i: &Ideal) -> ([AlgElem; 4], c_int) {
                let mut o: [AlgElem; 4] = Default::default();
                let [m1, m2, th, sm] = &mut o;
                // SAFETY: valid values, a seeded default domain; outputs are fresh.
                let ok = unsafe { ffi::quat_qlapoty_normeq(m1, m2, th, sm, i) };
                (o, ok)
            }
            /// C `quat_qlapoty`: `beta1`, `d1`, `theta` and the status.
            pub fn qlapoty(i: &Ideal) -> (AlgElem, Ibz, AlgElem, c_int) {
                let (mut b, mut d, mut th) = (AlgElem::default(), Ibz::default(), AlgElem::default());
                // SAFETY: valid values, a seeded default domain; outputs are fresh.
                let ok = unsafe { ffi::quat_qlapoty(&mut b, &mut d, &mut th, i) };
                (b, d, th, ok)
            }
        }
    };
}
