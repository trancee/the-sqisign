//! Bindings for the C quaternion module (`quaternion/ref`), expanded inside
//! each level module next to `mp`.

/// Calls `ffi::$f` with a fresh output first, returning it.
macro_rules! out {
    ($f:ident($($a:expr),*)) => {{
        let mut r = Default::default();
        // SAFETY: valid values; outputs are fresh.
        unsafe { ffi::$f(&mut r, $($a),*) };
        r
    }};
}

/// Calls the C predicate `ffi::$f`.
macro_rules! query {
    ($f:ident($($a:expr),*)) => {
        // SAFETY: valid values.
        unsafe { ffi::$f($($a),*) }
    };
}

macro_rules! quat_api {
    ($lvl:literal) => {
        /// C reference quaternion algebra.
        pub mod quat {
            use super::mp::Ibz;
            use core::ffi::c_int;

            /// C `ibz_vec_2_t`.
            pub type Vec2 = [Ibz; 2];
            /// C `ibz_vec_4_t`.
            pub type Vec4 = [Ibz; 4];
            /// C `ibz_mat_2x2_t`.
            pub type Mat2 = [[Ibz; 2]; 2];
            /// C `ibz_mat_4x4_t`.
            pub type Mat4 = [[Ibz; 4]; 4];

            /// C `quat_alg_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Alg {
                /// The prime.
                pub p: Ibz,
            }

            /// C `quat_alg_elem_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct AlgElem {
                /// The denominator.
                pub denom: Ibz,
                /// The numerators.
                pub coord: Vec4,
            }

            mod ffi {
                use super::{c_int, Alg, AlgElem, Ibz, Mat2, Mat4, Vec2, Vec4};
                ext!($lvl; ibz_rounded_div(q: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_sum_two_squares(s: *mut Ibz, a: *const Ibz, b: *const Ibz));
                ext!($lvl; ibz_vec_2_set(v: *mut Vec2, a0: c_int, a1: c_int));
                ext!($lvl; ibz_vec_2_add(o: *mut Vec2, a: *const Vec2, b: *const Vec2));
                ext!($lvl; ibz_vec_2_sub(o: *mut Vec2, a: *const Vec2, b: *const Vec2));
                ext!($lvl; ibz_vec_2_is_zero(a: *const Vec2) -> c_int);
                ext!($lvl; ibz_mat_2x2_eval(o: *mut Vec2, m: *const Mat2, v: *const Vec2));
                ext!($lvl; ibz_mat_2x2_mul(o: *mut Mat2, a: *const Mat2, b: *const Mat2));
                ext!($lvl; ibz_mat_2x2_inv_with_det_as_denom(inv: *mut Mat2, det: *mut Ibz,
                                                             m: *const Mat2) -> c_int);
                ext!($lvl; ibz_mat_2x2_inv_mod(inv: *mut Mat2, m: *const Mat2, n: *const Ibz) -> c_int);
                ext!($lvl; ibz_mat_2x2_normalize(m: *mut Mat2, e: c_int));
                ext!($lvl; ibz_vec_2_gaussian_mul(o: *mut Vec2, a: *const Vec2, b: *const Vec2));
                ext!($lvl; ibz_vec_2_gaussian_euclidean_division(q: *mut Vec2, r: *mut Vec2,
                                                                 a: *const Vec2, b: *const Vec2));
                ext!($lvl; ibz_vec_2_gaussian_gcd(o: *mut Vec2, a: *const Vec2, b: *const Vec2));
                ext!($lvl; ibz_vec_4_set(v: *mut Vec4, c0: i32, c1: i32, c2: i32, c3: i32));
                ext!($lvl; ibz_vec_4_add(o: *mut Vec4, a: *const Vec4, b: *const Vec4));
                ext!($lvl; ibz_vec_4_sub(o: *mut Vec4, a: *const Vec4, b: *const Vec4));
                ext!($lvl; ibz_vec_4_is_zero(a: *const Vec4) -> c_int);
                ext!($lvl; ibz_vec_4_content(o: *mut Ibz, v: *const Vec4));
                ext!($lvl; ibz_vec_4_scalar_mul(o: *mut Vec4, s: *const Ibz, v: *const Vec4));
                ext!($lvl; ibz_vec_4_scalar_div(o: *mut Vec4, s: *const Ibz, v: *const Vec4) -> c_int);
                ext!($lvl; ibz_mat_4x4_zero(m: *mut Mat4));
                ext!($lvl; ibz_mat_4x4_identity(m: *mut Mat4));
                ext!($lvl; ibz_mat_4x4_scalar_mul(o: *mut Mat4, s: *const Ibz, m: *const Mat4));
                ext!($lvl; ibz_mat_4x4_eval(o: *mut Vec4, m: *const Mat4, v: *const Vec4));
                ext!($lvl; ibz_mat_4x4_eval_t(o: *mut Vec4, v: *const Vec4, m: *const Mat4));
                ext!($lvl; quat_qf_eval(o: *mut Ibz, qf: *const Mat4, v: *const Vec4));
                ext!($lvl; quat_alg_coord_mul(o: *mut Vec4, a: *const Vec4, b: *const Vec4,
                                              alg: *const Alg));
                ext!($lvl; quat_alg_equal_denom(ra: *mut AlgElem, rb: *mut AlgElem,
                                                a: *const AlgElem, b: *const AlgElem));
                ext!($lvl; quat_alg_add(o: *mut AlgElem, a: *const AlgElem, b: *const AlgElem));
                ext!($lvl; quat_alg_mul(o: *mut AlgElem, a: *const AlgElem, b: *const AlgElem,
                                        alg: *const Alg));
                ext!($lvl; quat_alg_norm(num: *mut Ibz, den: *mut Ibz, a: *const AlgElem,
                                         alg: *const Alg));
                ext!($lvl; quat_alg_conj(o: *mut AlgElem, a: *const AlgElem));
                ext!($lvl; quat_alg_normalize(a: *mut AlgElem));
                ext!($lvl; quat_alg_elem_is_zero(a: *const AlgElem) -> c_int);
                ext!($lvl; quat_alg_elem_set(e: *mut AlgElem, d: i32, c0: i32, c1: i32, c2: i32,
                                             c3: i32));
                ext!($lvl; quat_alg_elem_scalar_mul(o: *mut AlgElem, s: *const Ibz,
                                                    a: *const AlgElem));
            }

            /// C `ibz_rounded_div`.
            pub fn rounded_div(a: &Ibz, b: &Ibz) -> Ibz {
                out!(ibz_rounded_div(a, b))
            }
            /// C `ibz_sum_two_squares`.
            pub fn sum_two_squares(a: &Ibz, b: &Ibz) -> Ibz {
                out!(ibz_sum_two_squares(a, b))
            }
            /// C `ibz_vec_2_set`.
            pub fn vec2_set(a0: i32, a1: i32) -> Vec2 {
                out!(ibz_vec_2_set(a0, a1))
            }
            /// C `ibz_vec_2_add`.
            pub fn vec2_add(a: &Vec2, b: &Vec2) -> Vec2 {
                out!(ibz_vec_2_add(a, b))
            }
            /// C `ibz_vec_2_sub`.
            pub fn vec2_sub(a: &Vec2, b: &Vec2) -> Vec2 {
                out!(ibz_vec_2_sub(a, b))
            }
            /// C `ibz_vec_2_is_zero`.
            pub fn vec2_is_zero(a: &Vec2) -> c_int {
                query!(ibz_vec_2_is_zero(a))
            }
            /// C `ibz_mat_2x2_eval`.
            pub fn mat2_eval(m: &Mat2, v: &Vec2) -> Vec2 {
                out!(ibz_mat_2x2_eval(m, v))
            }
            /// C `ibz_mat_2x2_mul`.
            pub fn mat2_mul(a: &Mat2, b: &Mat2) -> Mat2 {
                out!(ibz_mat_2x2_mul(a, b))
            }
            /// C `ibz_mat_2x2_inv_with_det_as_denom` into a zero matrix.
            pub fn mat2_inv_with_det(m: &Mat2) -> (Mat2, Ibz, c_int) {
                let (mut inv, mut det) = (Mat2::default(), Ibz::default());
                // SAFETY: valid values; outputs are fresh.
                let ok = unsafe { ffi::ibz_mat_2x2_inv_with_det_as_denom(&mut inv, &mut det, m) };
                (inv, det, ok)
            }
            /// C `ibz_mat_2x2_inv_mod`.
            pub fn mat2_inv_mod(m: &Mat2, n: &Ibz) -> (Mat2, c_int) {
                let mut inv = Mat2::default();
                // SAFETY: valid values; the output is fresh.
                let ok = unsafe { ffi::ibz_mat_2x2_inv_mod(&mut inv, m, n) };
                (inv, ok)
            }
            /// C `ibz_mat_2x2_normalize`.
            pub fn mat2_normalize(m: &Mat2, e: c_int) -> Mat2 {
                let mut r = *m;
                // SAFETY: a valid value.
                unsafe { ffi::ibz_mat_2x2_normalize(&mut r, e) };
                r
            }
            /// C `ibz_vec_2_gaussian_mul`.
            pub fn gaussian_mul(a: &Vec2, b: &Vec2) -> Vec2 {
                out!(ibz_vec_2_gaussian_mul(a, b))
            }
            /// C `ibz_vec_2_gaussian_euclidean_division`.
            pub fn gaussian_div(a: &Vec2, b: &Vec2) -> (Vec2, Vec2) {
                let (mut q, mut r) = (Vec2::default(), Vec2::default());
                // SAFETY: valid values; outputs are fresh.
                unsafe { ffi::ibz_vec_2_gaussian_euclidean_division(&mut q, &mut r, a, b) };
                (q, r)
            }
            /// C `ibz_vec_2_gaussian_gcd`.
            pub fn gaussian_gcd(a: &Vec2, b: &Vec2) -> Vec2 {
                out!(ibz_vec_2_gaussian_gcd(a, b))
            }
            /// C `ibz_vec_4_set`.
            pub fn vec4_set(c: [i32; 4]) -> Vec4 {
                out!(ibz_vec_4_set(c[0], c[1], c[2], c[3]))
            }
            /// C `ibz_vec_4_add`.
            pub fn vec4_add(a: &Vec4, b: &Vec4) -> Vec4 {
                out!(ibz_vec_4_add(a, b))
            }
            /// C `ibz_vec_4_sub`.
            pub fn vec4_sub(a: &Vec4, b: &Vec4) -> Vec4 {
                out!(ibz_vec_4_sub(a, b))
            }
            /// C `ibz_vec_4_is_zero`.
            pub fn vec4_is_zero(a: &Vec4) -> c_int {
                query!(ibz_vec_4_is_zero(a))
            }
            /// C `ibz_vec_4_content`.
            pub fn vec4_content(v: &Vec4) -> Ibz {
                out!(ibz_vec_4_content(v))
            }
            /// C `ibz_vec_4_scalar_mul`.
            pub fn vec4_scalar_mul(s: &Ibz, v: &Vec4) -> Vec4 {
                out!(ibz_vec_4_scalar_mul(s, v))
            }
            /// C `ibz_vec_4_scalar_div`.
            pub fn vec4_scalar_div(s: &Ibz, v: &Vec4) -> (Vec4, c_int) {
                let mut r = Vec4::default();
                // SAFETY: valid values; the output is fresh.
                let ok = unsafe { ffi::ibz_vec_4_scalar_div(&mut r, s, v) };
                (r, ok)
            }
            /// C `ibz_mat_4x4_zero`.
            pub fn mat4_zero() -> Mat4 {
                out!(ibz_mat_4x4_zero())
            }
            /// C `ibz_mat_4x4_identity`.
            pub fn mat4_identity() -> Mat4 {
                out!(ibz_mat_4x4_identity())
            }
            /// C `ibz_mat_4x4_scalar_mul`.
            pub fn mat4_scalar_mul(s: &Ibz, m: &Mat4) -> Mat4 {
                out!(ibz_mat_4x4_scalar_mul(s, m))
            }
            /// C `ibz_mat_4x4_eval`.
            pub fn mat4_eval(m: &Mat4, v: &Vec4) -> Vec4 {
                out!(ibz_mat_4x4_eval(m, v))
            }
            /// C `ibz_mat_4x4_eval_t`.
            pub fn mat4_eval_t(v: &Vec4, m: &Mat4) -> Vec4 {
                out!(ibz_mat_4x4_eval_t(v, m))
            }
            /// C `quat_qf_eval`.
            pub fn qf_eval(qf: &Mat4, v: &Vec4) -> Ibz {
                out!(quat_qf_eval(qf, v))
            }
            /// C `quat_alg_coord_mul`.
            pub fn coord_mul(a: &Vec4, b: &Vec4, alg: &Alg) -> Vec4 {
                out!(quat_alg_coord_mul(a, b, alg))
            }
            /// C `quat_alg_equal_denom`.
            pub fn equal_denom(a: &AlgElem, b: &AlgElem) -> (AlgElem, AlgElem) {
                let (mut ra, mut rb) = (AlgElem::default(), AlgElem::default());
                // SAFETY: valid values; outputs are fresh.
                unsafe { ffi::quat_alg_equal_denom(&mut ra, &mut rb, a, b) };
                (ra, rb)
            }
            /// C `quat_alg_add`.
            pub fn add(a: &AlgElem, b: &AlgElem) -> AlgElem {
                out!(quat_alg_add(a, b))
            }
            /// C `quat_alg_mul`.
            pub fn mul(a: &AlgElem, b: &AlgElem, alg: &Alg) -> AlgElem {
                out!(quat_alg_mul(a, b, alg))
            }
            /// C `quat_alg_norm`.
            pub fn norm(a: &AlgElem, alg: &Alg) -> (Ibz, Ibz) {
                let (mut num, mut den) = (Ibz::default(), Ibz::default());
                // SAFETY: valid values; outputs are fresh.
                unsafe { ffi::quat_alg_norm(&mut num, &mut den, a, alg) };
                (num, den)
            }
            /// C `quat_alg_conj`.
            pub fn conj(a: &AlgElem) -> AlgElem {
                out!(quat_alg_conj(a))
            }
            /// C `quat_alg_normalize`.
            pub fn normalize(a: &AlgElem) -> AlgElem {
                let mut r = *a;
                // SAFETY: a valid value.
                unsafe { ffi::quat_alg_normalize(&mut r) };
                r
            }
            /// C `quat_alg_elem_is_zero`.
            pub fn elem_is_zero(a: &AlgElem) -> c_int {
                query!(quat_alg_elem_is_zero(a))
            }
            /// C `quat_alg_elem_set`.
            pub fn elem_set(d: i32, c: [i32; 4]) -> AlgElem {
                out!(quat_alg_elem_set(d, c[0], c[1], c[2], c[3]))
            }
            /// C `quat_alg_elem_scalar_mul`.
            pub fn elem_scalar_mul(s: &Ibz, a: &AlgElem) -> AlgElem {
                out!(quat_alg_elem_scalar_mul(s, a))
            }
        }
    };
}
