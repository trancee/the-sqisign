//! Safe wrappers over the C reference `ec` API (`ec.c`) of one parameter set.

macro_rules! curve_in_place {
    ($($name:ident => $f:ident),*) => {$(
        #[doc = concat!("C `", stringify!($f), "` on a copy.")]
        pub fn $name(e: &Curve) -> Curve {
            let mut o = *e;
            // SAFETY: the pointer references a valid value.
            unsafe { ffi::$f(&mut o) };
            o
        }
    )*};
}

macro_rules! ec_api {
    ($lvl:literal, $order_words:literal) => {
        /// C reference elliptic-curve arithmetic (`ec.c`).
        pub mod ec {
            use super::Fp2;
            use core::ffi::c_int;

            /// Words of an order scalar (`NWORDS_ORDER`).
            pub const ORDER_WORDS: usize = $order_words;

            /// C `ec_point_t`: projective `(X : Z)`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Point {
                /// `X`.
                pub x: Fp2,
                /// `Z`.
                pub z: Fp2,
            }

            /// C `ec_basis_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Basis {
                /// `P`.
                pub p: Point,
                /// `Q`.
                pub q: Point,
                /// `P - Q`.
                pub pmq: Point,
            }

            /// C `ec_curve_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Curve {
                /// `A`.
                pub a: Fp2,
                /// `C`.
                pub c: Fp2,
                /// `(A + 2C : 4C)`.
                pub a24: Point,
                /// `is_A24_computed_and_normalized`.
                pub a24_normalized: bool,
            }

            /// C `ec_bary_coordinates_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Bary {
                /// `u`.
                pub u: Fp2,
                /// `v`.
                pub v: Fp2,
                /// `w`.
                pub w: Fp2,
            }

            ec_ladder_api!($lvl);
            ec_isog_api!($lvl);

            mod ffi {
                use super::{c_int, Bary, Curve, Fp2, Point};
                ext!($lvl; ec_point_init(p: *mut Point));
                ext!($lvl; ec_curve_init(e: *mut Curve));
                ext!($lvl; ec_curve_verify_A(a: *const Fp2) -> c_int);
                ext!($lvl; ec_curve_init_from_A(e: *mut Curve, a: *const Fp2) -> c_int);
                ext!($lvl; ec_normalize_curve(e: *mut Curve));
                ext!($lvl; ec_curve_normalize_A24(e: *mut Curve));
                ext!($lvl; ec_normalize_curve_and_A24(e: *mut Curve));
                ext!($lvl; ec_normalize_point(p: *mut Point));
                ext!($lvl; ec_is_equal(p: *const Point, q: *const Point) -> u32);
                ext!($lvl; ec_is_zero(p: *const Point) -> u32);
                ext!($lvl; ec_has_zero_coordinate(p: *const Point) -> u32);
                ext!($lvl; ec_is_two_torsion(p: *const Point, e: *const Curve) -> u32);
                ext!($lvl; ec_is_four_torsion(p: *const Point, e: *const Curve) -> u32);
                ext!($lvl; ec_xADD(r: *mut Point, p: *const Point, q: *const Point, pq: *const Point));
                ext!($lvl; ec_xDBL(q: *mut Point, p: *const Point, ac: *const Point));
                ext!($lvl; ec_xDBL_E0(q: *mut Point, p: *const Point));
                ext!($lvl; ec_xDBL_A24(q: *mut Point, p: *const Point, a24: *const Point, n: bool));
                ext!($lvl; ec_xDBLADD(r: *mut Point, s: *mut Point, p: *const Point,
                                      q: *const Point, pq: *const Point, a24: *const Point));
                ext!($lvl; ec_points_to_bary_coordinates(r: *mut Bary, p: *mut Point,
                                                         q: *mut Point, pmq: *mut Point));
            }

            /// C `ec_point_init`: the identity `(1 : 0)`.
            pub fn point_init() -> Point {
                let mut p = Point::default();
                // SAFETY: the pointer references a valid value.
                unsafe { ffi::ec_point_init(&mut p) };
                p
            }

            /// C `ec_curve_init`.
            pub fn curve_init() -> Curve {
                let mut e = Curve::default();
                // SAFETY: the pointer references a valid value.
                unsafe { ffi::ec_curve_init(&mut e) };
                e
            }

            /// C `ec_curve_verify_A`.
            pub fn curve_verify_a(a: &Fp2) -> i32 {
                // SAFETY: the pointer references a valid value.
                unsafe { ffi::ec_curve_verify_A(a) }
            }

            /// C `ec_curve_init_from_A`.
            pub fn curve_init_from_a(a: &Fp2) -> (Curve, i32) {
                let mut e = Curve::default();
                // SAFETY: both pointers reference valid values.
                let ok = unsafe { ffi::ec_curve_init_from_A(&mut e, a) };
                (e, ok)
            }

            curve_in_place!(normalize_curve => ec_normalize_curve,
                            curve_normalize_a24 => ec_curve_normalize_A24,
                            normalize_curve_and_a24 => ec_normalize_curve_and_A24);

            /// C `ec_normalize_point` on a copy.
            pub fn normalize_point(p: &Point) -> Point {
                let mut o = *p;
                // SAFETY: the pointer references a valid value.
                unsafe { ffi::ec_normalize_point(&mut o) };
                o
            }

            /// C `ec_is_equal`.
            pub fn is_equal(p: &Point, q: &Point) -> u32 {
                // SAFETY: both pointers reference valid values.
                unsafe { ffi::ec_is_equal(p, q) }
            }

            /// C `ec_is_zero`.
            pub fn is_zero(p: &Point) -> u32 {
                // SAFETY: the pointer references a valid value.
                unsafe { ffi::ec_is_zero(p) }
            }

            /// C `ec_has_zero_coordinate`.
            pub fn has_zero_coordinate(p: &Point) -> u32 {
                // SAFETY: the pointer references a valid value.
                unsafe { ffi::ec_has_zero_coordinate(p) }
            }

            /// C `ec_is_two_torsion`.
            pub fn is_two_torsion(p: &Point, e: &Curve) -> u32 {
                // SAFETY: both pointers reference valid values.
                unsafe { ffi::ec_is_two_torsion(p, e) }
            }

            /// C `ec_is_four_torsion`.
            pub fn is_four_torsion(p: &Point, e: &Curve) -> u32 {
                // SAFETY: both pointers reference valid values.
                unsafe { ffi::ec_is_four_torsion(p, e) }
            }

            /// C `ec_xADD`.
            pub fn xadd(p: &Point, q: &Point, pq: &Point) -> Point {
                let mut r = Point::default();
                // SAFETY: all pointers reference valid values.
                unsafe { ffi::ec_xADD(&mut r, p, q, pq) };
                r
            }

            /// C `ec_xDBL` with `(A : C)` given as a point.
            pub fn xdbl(p: &Point, ac: &Point) -> Point {
                let mut r = Point::default();
                // SAFETY: all pointers reference valid values.
                unsafe { ffi::ec_xDBL(&mut r, p, ac) };
                r
            }

            /// C `ec_xDBL_E0`.
            pub fn xdbl_e0(p: &Point) -> Point {
                let mut r = Point::default();
                // SAFETY: both pointers reference valid values.
                unsafe { ffi::ec_xDBL_E0(&mut r, p) };
                r
            }

            /// C `ec_xDBL_A24`.
            pub fn xdbl_a24(p: &Point, a24: &Point, normalized: bool) -> Point {
                let mut r = Point::default();
                // SAFETY: all pointers reference valid values.
                unsafe { ffi::ec_xDBL_A24(&mut r, p, a24, normalized) };
                r
            }

            /// C `ec_xDBLADD`: `(2P, P + Q)`.
            pub fn xdbladd(p: &Point, q: &Point, pq: &Point, a24: &Point) -> (Point, Point) {
                let (mut r, mut s) = (Point::default(), Point::default());
                // SAFETY: all pointers reference valid, distinct values.
                unsafe { ffi::ec_xDBLADD(&mut r, &mut s, p, q, pq, a24) };
                (r, s)
            }

            /// C `ec_points_to_bary_coordinates`.
            pub fn bary(p: &Point, q: &Point, pmq: &Point) -> Bary {
                let (mut p, mut q, mut pmq) = (*p, *q, *pmq);
                let mut r = Bary::default();
                // SAFETY: all pointers reference valid, distinct values.
                unsafe { ffi::ec_points_to_bary_coordinates(&mut r, &mut p, &mut q, &mut pmq) };
                r
            }
        }
    };
}
