//! Safe wrappers over the C reference `ec` isogenies, normalisation, bases
//! and pairings (`isog.c`, `normalize.c`, `basis.c`, `biextension.c`).

macro_rules! ec_isog_api {
    ($lvl:literal) => {
        /// C `ec_kps4_t`.
        #[repr(C)]
        #[derive(Clone, Copy, Debug, Default)]
        pub struct Kps4 {
            /// `K`.
            pub k: [Fp2; 3],
        }

        /// C `ec_isog_even_t`.
        #[repr(C)]
        #[derive(Clone, Copy, Debug, Default)]
        pub struct IsogEven {
            /// The domain curve.
            pub curve: Curve,
            /// A kernel generator.
            pub kernel: Point,
            /// The length as a 2-isogeny walk.
            pub length: core::ffi::c_uint,
        }

        /// C `ec_change_coord_matrix_t`.
        #[repr(C)]
        #[derive(Clone, Copy, Debug, Default)]
        pub struct ChangeCoordMatrix {
            /// `a`.
            pub a: Fp2,
            /// `b`.
            pub b: Fp2,
            /// `c`.
            pub c: Fp2,
            /// `d`.
            pub d: Fp2,
        }

        mod isog_ffi {
            use super::{c_int, Basis, ChangeCoordMatrix, Curve, Fp2, IsogEven, Kps4, Point};
            ext!($lvl; iso_xisog_4(kps: *mut Kps4, b: *mut Point, p: Point));
            ext!($lvl; iso_xeval_4(r: *mut Point, q: *const Point, len: c_int, kps: *const Kps4));
            ext!($lvl; iso_isogeny_2chain(image: *mut Curve, phi: *mut IsogEven) -> u32);
            ext!($lvl; ec_apply_isomorphism(p: *mut Point, m: *const ChangeCoordMatrix));
            ext!($lvl; ec_normalize_montgomery(e: *mut Curve, t: *const Point, b: *mut Basis)
                                               -> bool);
            ext!($lvl; ec_theta_to_montgomery(e: *mut Curve, m: *mut ChangeCoordMatrix,
                                              th: *const Point) -> bool);
            ext!($lvl; ec_curve_to_basis_2f_to_hint(b: *mut Basis, e: *mut Curve, f: c_int,
                                                    ee: c_int) -> u8);
            ext!($lvl; ec_curve_to_basis_2f_from_hint(b: *mut Basis, e: *mut Curve, f: c_int,
                                                      hint: u8) -> c_int);
            ext!($lvl; pairing_reduced_tate(r: *mut Fp2, e: u32, p: *const Point,
                                            q: *const Point, pq: *const Point, c: *mut Curve));
            ext!($lvl; pairing_dlog_2_tate(r1: *mut u64, r2: *mut u64, s1: *mut u64,
                                           s2: *mut u64, pq: *const Basis, rs: *const Basis,
                                           c: *mut Curve, e: c_int));
            extern "C" {
                #[link_name = concat!("sqisign_", $lvl, "_ref_BASIS_E0_PX")]
                pub static BASIS_E0_PX: Fp2;
                #[link_name = concat!("sqisign_", $lvl, "_ref_BASIS_E0_QX")]
                pub static BASIS_E0_QX: Fp2;
                #[link_name = concat!("sqisign_", $lvl, "_ref_p_cofactor_for_2f")]
                pub static P_COFACTOR_FOR_2F: [u64; 1];
            }
        }

        fn int(v: usize) -> c_int {
            c_int::try_from(v).expect("fits c_int")
        }

        /// C `BASIS_E0_PX`, `BASIS_E0_QX` and `p_cofactor_for_2f`.
        pub fn e0_constants() -> (Fp2, Fp2, u64) {
            // SAFETY: immutable, initialised C globals.
            unsafe { (isog_ffi::BASIS_E0_PX, isog_ffi::BASIS_E0_QX, isog_ffi::P_COFACTOR_FOR_2F[0]) }
        }

        /// C `iso_xisog_4`: `(kps, A24)`.
        pub fn xisog_4(p: &Point) -> (Kps4, Point) {
            let (mut k, mut b) = (Kps4::default(), Point::default());
            // SAFETY: all pointers reference valid values.
            unsafe { isog_ffi::iso_xisog_4(&mut k, &mut b, *p) };
            (k, b)
        }

        /// C `iso_xeval_4` on every point of `qs`.
        pub fn xeval_4(qs: &[Point], kps: &Kps4) -> Vec<Point> {
            let mut r = vec![Point::default(); qs.len()];
            // SAFETY: `r` and `qs` hold `qs.len()` points; `kps` is valid.
            unsafe { isog_ffi::iso_xeval_4(r.as_mut_ptr(), qs.as_ptr(), int(qs.len()), kps) };
            r
        }

        /// C `iso_isogeny_2chain`: the codomain and the success flag.
        pub fn isogeny_2chain(phi: &IsogEven) -> (Curve, u32) {
            let (mut c, mut phi) = (Curve::default(), *phi);
            // SAFETY: both pointers reference valid values.
            let ok = unsafe { isog_ffi::iso_isogeny_2chain(&mut c, &mut phi) };
            (c, ok)
        }

        /// C `ec_apply_isomorphism` on a copy.
        pub fn apply_isomorphism(p: &Point, m: &ChangeCoordMatrix) -> Point {
            let mut o = *p;
            // SAFETY: both pointers reference valid values.
            unsafe { isog_ffi::ec_apply_isomorphism(&mut o, m) };
            o
        }

        /// C `ec_normalize_montgomery`: the curve, the mapped basis (if any) and
        /// the success flag.
        pub fn normalize_montgomery(e: &Curve, t: &Point, b: Option<&Basis>)
            -> (Curve, Option<Basis>, bool) {
            let (mut e, mut b) = (*e, b.copied());
            let bp = b.as_mut().map_or(core::ptr::null_mut(), |b| b as *mut Basis);
            // SAFETY: `bp` is null or references a valid basis; others are valid.
            let ok = unsafe { isog_ffi::ec_normalize_montgomery(&mut e, t, bp) };
            (e, b, ok)
        }

        /// C `ec_theta_to_montgomery`.
        pub fn theta_to_montgomery(th: &Point) -> (Curve, ChangeCoordMatrix, bool) {
            let (mut e, mut m) = (Curve::default(), ChangeCoordMatrix::default());
            // SAFETY: all pointers reference valid values.
            let ok = unsafe { isog_ffi::ec_theta_to_montgomery(&mut e, &mut m, th) };
            (e, m, ok)
        }

        /// C `ec_curve_to_basis_2f_to_hint`: basis, hint and the updated curve.
        pub fn basis_2f_to_hint(e: &Curve, f: usize, ee: usize) -> (Basis, u8, Curve) {
            let (mut b, mut e) = (Basis::default(), *e);
            // SAFETY: both pointers reference valid values.
            let h = unsafe { isog_ffi::ec_curve_to_basis_2f_to_hint(&mut b, &mut e, int(f), int(ee)) };
            (b, h, e)
        }

        /// C `ec_curve_to_basis_2f_from_hint`: basis, flag and the updated curve.
        pub fn basis_2f_from_hint(e: &Curve, f: usize, hint: u8) -> (Basis, i32, Curve) {
            let (mut b, mut e) = (Basis::default(), *e);
            // SAFETY: both pointers reference valid values.
            let ok = unsafe { isog_ffi::ec_curve_to_basis_2f_from_hint(&mut b, &mut e, int(f), hint) };
            (b, ok, e)
        }

        /// C `pairing_reduced_tate`: the pairing and the updated curve.
        pub fn reduced_tate(e: u32, p: &Point, q: &Point, pq: &Point, c: &Curve) -> (Fp2, Curve) {
            let (mut r, mut c) = (Fp2::default(), *c);
            // SAFETY: all pointers reference valid values.
            unsafe { isog_ffi::pairing_reduced_tate(&mut r, e, p, q, pq, &mut c) };
            (r, c)
        }

        /// C `pairing_dlog_2_tate`: `[r1, r2, s1, s2]` and the updated curve.
        pub fn dlog_2_tate(pq: &Basis, rs: &Basis, c: &Curve, e: usize) -> ([Vec<u64>; 4], Curve) {
            let mut o: [Vec<u64>; 4] = core::array::from_fn(|_| vec![u64::MAX; ORDER_WORDS]);
            let mut c = *c;
            let [r1, r2, s1, s2] = &mut o;
            // SAFETY: each output holds `NWORDS_ORDER` words; others are valid.
            unsafe {
                isog_ffi::pairing_dlog_2_tate(r1.as_mut_ptr(), r2.as_mut_ptr(), s1.as_mut_ptr(),
                                              s2.as_mut_ptr(), pq, rs, &mut c, int(e))
            };
            (o, c)
        }
    };
}
