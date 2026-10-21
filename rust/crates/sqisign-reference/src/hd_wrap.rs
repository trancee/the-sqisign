//! Safe wrapper functions for [`hd_api`](crate::hd), expanded inside each
//! level's `hd` module.

macro_rules! hd_wrappers {
    () => {
        fn small(n: usize) -> u16 {
            u16::try_from(n).expect("fits u16")
        }

        /// C `theta_DBL_couple_point`.
        pub fn dbl_couple(p: &CouplePoint, e: &CoupleCurve) -> CouplePoint {
            let mut o = CouplePoint::default();
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::theta_DBL_couple_point(&mut o, p, e) };
            o
        }

        /// C `theta_DBL_couple_point_iter`.
        pub fn dbl_couple_iter(p: &CouplePoint, n: usize, e: &CoupleCurve) -> CouplePoint {
            let mut o = CouplePoint::default();
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::theta_DBL_couple_point_iter(&mut o, small(n), p, e) };
            o
        }

        /// C `theta_copy_bases_to_kernel`.
        pub fn bases_to_kernel(b1: &Basis, b2: &Basis) -> Kernel {
            let mut k = Kernel::default();
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::theta_copy_bases_to_kernel(&mut k, b1, b2) };
            k
        }

        /// C `theta_DBL`: the double and the updated structure.
        pub fn theta_dbl(a: &ThetaStructure, p: &ThetaPoint) -> (ThetaPoint, ThetaStructure) {
            let (mut o, mut a) = (ThetaPoint::default(), *a);
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::theta_DBL(&mut o, &mut a, p) };
            (o, a)
        }

        /// C `theta_DBL_iter`: the result and the updated structure.
        pub fn theta_dbl_iter(
            a: &ThetaStructure,
            p: &ThetaPoint,
            n: usize,
        ) -> (ThetaPoint, ThetaStructure) {
            let (mut o, mut a) = (ThetaPoint::default(), *a);
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::theta_DBL_iter(&mut o, &mut a, p, small(n)) };
            (o, a)
        }

        /// C `theta_extract_dual_null_point`.
        pub fn dual_null_point(a: &ThetaStructure) -> ThetaPoint {
            let (mut o, mut a) = (ThetaPoint::default(), *a);
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::theta_extract_dual_null_point(&mut o, &mut a) };
            o
        }

        /// C `theta_precomputation` on a copy.
        pub fn precompute(a: &ThetaStructure) -> ThetaStructure {
            let mut a = *a;
            // SAFETY: the pointer references a valid value.
            unsafe { ffi::theta_precomputation(&mut a) };
            a
        }

        /// C `theta_is_product_theta_point`.
        pub fn is_product(p: &ThetaPoint) -> u32 {
            // SAFETY: the pointer references a valid value.
            unsafe { ffi::theta_is_product_theta_point(p) }
        }

        /// C `gluing_ladder`: `(Reven, Rodd)`.
        pub fn gluing_ladder(
            p: &Point,
            q: &Point,
            pmq: &Point,
            pow2: usize,
            e: &Curve,
        ) -> (Point, Point) {
            let (mut re, mut ro) = (Point::default(), Point::default());
            let pow2 = c_int::try_from(pow2).expect("fits c_int");
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::gluing_ladder(&mut re, &mut ro, p, q, pmq, pow2, e) };
            (re, ro)
        }

        /// C `gluing_couple_point_to_theta`.
        pub fn couple_point_to_theta(m: &GluingMatrix, t: &CouplePoint) -> ThetaPoint {
            let mut o = ThetaPoint::default();
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::gluing_couple_point_to_theta(&mut o, m, t) };
            o
        }

        /// C `gluing_basis_compute`.
        pub fn basis_compute(k1: &CouplePoint, k2: &CouplePoint) -> GluingMatrix {
            let mut m = GluingMatrix::default();
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::gluing_basis_compute(&mut m, k1, k2) };
            m
        }

        /// C `gluing_compute`: the gluing and the success flag.
        pub fn gluing_compute(
            e: &CoupleCurve,
            k1: &CouplePoint,
            k2: &CouplePoint,
            dual: bool,
            verify: bool,
        ) -> (Gluing, c_int) {
            let mut o = Gluing::default();
            // SAFETY: all pointers reference valid values.
            let ok = unsafe { ffi::gluing_compute(&mut o, e, k1, k2, dual, verify) };
            (o, ok)
        }

        /// C `gluing_eval_point_bary`.
        pub fn eval_bary(
            c1: &Bary,
            c2: &Bary,
            phi: &Gluing,
            aux: &ThetaPoint,
            dual: bool,
        ) -> ThetaPoint {
            let mut o = ThetaPoint::default();
            // SAFETY: all pointers reference valid values.
            unsafe { ffi::gluing_eval_point_bary(&mut o, c1, c2, phi, aux, dual) };
            o
        }

        /// C `gluing_eval_point_special_case`: the image and the success flag.
        pub fn eval_special(p: &CouplePoint, phi: &Gluing, dual: bool) -> (ThetaPoint, c_int) {
            let mut o = ThetaPoint::default();
            // SAFETY: all pointers reference valid values.
            let ok = unsafe { ffi::gluing_eval_point_special_case(&mut o, p, phi, dual) };
            (o, ok)
        }

        /// C `splitting_tensor_product_to_couple_point`: the point and the flag.
        pub fn tensor_product(p: &ThetaPoint) -> (CouplePoint, bool) {
            let mut o = CouplePoint::default();
            // SAFETY: all pointers reference valid values.
            let ok = unsafe { ffi::splitting_tensor_product_to_couple_point(&mut o, p) };
            (o, ok)
        }

        /// C `splitting_check_valid_theta_null_point`.
        pub fn check_valid_null(th: &Point) -> bool {
            // SAFETY: the pointer references a valid value.
            unsafe { ffi::splitting_check_valid_theta_null_point(th) }
        }

        /// Output of a C chain: flag, updated domain, codomain, evaluated points.
        pub type ChainOut = (c_int, CoupleCurve, CoupleCurve, Vec<CouplePoint>);

        /// C `theta_chain_compute_and_eval{,_E1,_E2,_verify}` for `mode`
        /// 0, 1, 2 and -1.
        pub fn chain(
            mode: i8,
            n: usize,
            e12: &CoupleCurve,
            ker: &Kernel,
            pts: &[CouplePoint],
        ) -> ChainOut {
            let f = match mode {
                0 => ffi::theta_chain_compute_and_eval,
                1 => ffi::theta_chain_compute_and_eval_E1,
                2 => ffi::theta_chain_compute_and_eval_E2,
                _ => ffi::theta_chain_compute_and_eval_verify,
            };
            let (mut e12, mut e34, mut p) = (*e12, CoupleCurve::default(), pts.to_vec());
            let np = u8::try_from(p.len()).expect("fits u8");
            // SAFETY: `p` holds `np` points; other pointers reference valid values.
            let ok = unsafe { f(small(n), &mut e12, ker, &mut e34, p.as_mut_ptr(), np) };
            (ok, e12, e34, p)
        }
    };
}
