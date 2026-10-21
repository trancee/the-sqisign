//! Safe wrappers over the C reference `hd` (theta structures, gluing,
//! splitting and isogeny chains).

macro_rules! hd_api {
    ($lvl:literal) => {
        /// C reference dimension-2 isogenies (`hd`).
        pub mod hd {
            use super::ec::{Bary, Basis, Curve, Point};
            use super::Fp2;
            use core::ffi::c_int;

            /// C `theta_point_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct ThetaPoint {
                /// `x`.
                pub x: Fp2,
                /// `y`.
                pub y: Fp2,
                /// `z`.
                pub z: Fp2,
                /// `t`.
                pub t: Fp2,
            }

            /// C `theta_couple_point_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct CouplePoint {
                /// `P1`.
                pub p1: Point,
                /// `P2`.
                pub p2: Point,
            }

            /// C `theta_couple_curve_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct CoupleCurve {
                /// `E1`.
                pub e1: Curve,
                /// `E2`.
                pub e2: Curve,
            }

            /// C `theta_kernel_couple_points_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Kernel {
                /// `T1`.
                pub t1: CouplePoint,
                /// `T2`.
                pub t2: CouplePoint,
            }

            /// C `theta_structure_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct ThetaStructure {
                /// `inv_dual_null_point`.
                pub inv_dual_null_point: ThetaPoint,
                /// `dbl_data`.
                pub dbl_data: ThetaPoint,
                /// `inv_sqr_null_point`.
                pub inv_sqr_null_point: ThetaPoint,
                /// `precomputation`.
                pub precomputation: bool,
            }

            /// C `gluing_change_coord_matrix_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct GluingMatrix {
                /// `m`.
                pub m: [[Fp2; 4]; 4],
            }

            /// C `theta_gluing_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Gluing {
                /// `inv_imageK1_8`.
                pub inv_image_k1_8: ThetaPoint,
                /// `inv_imageK2_8`.
                pub inv_image_k2_8: ThetaPoint,
                /// `M`.
                pub m: GluingMatrix,
                /// `inv_dual_theta_null`.
                pub inv_dual_theta_null: ThetaPoint,
                /// `codomain`.
                pub codomain: ThetaPoint,
            }

            mod ffi {
                use super::*;
                ext!($lvl; theta_DBL_couple_point(o: *mut CouplePoint, i: *const CouplePoint,
                                                  e: *const CoupleCurve));
                ext!($lvl; theta_DBL_couple_point_iter(o: *mut CouplePoint, n: u16,
                                                       i: *const CouplePoint,
                                                       e: *const CoupleCurve));
                ext!($lvl; theta_copy_bases_to_kernel(k: *mut Kernel, b1: *const Basis,
                                                      b2: *const Basis));
                ext!($lvl; theta_DBL(o: *mut ThetaPoint, a: *mut ThetaStructure,
                                     i: *const ThetaPoint));
                ext!($lvl; theta_DBL_iter(o: *mut ThetaPoint, a: *mut ThetaStructure,
                                          i: *const ThetaPoint, e: u16));
                ext!($lvl; theta_extract_dual_null_point(o: *mut ThetaPoint,
                                                         a: *mut ThetaStructure));
                ext!($lvl; theta_precomputation(a: *mut ThetaStructure));
                ext!($lvl; theta_is_product_theta_point(p: *const ThetaPoint) -> u32);
                ext!($lvl; gluing_ladder(re: *mut Point, ro: *mut Point, p: *const Point,
                                         q: *const Point, pmq: *const Point, pow2: c_int,
                                         e: *const Curve));
                ext!($lvl; gluing_couple_point_to_theta(o: *mut ThetaPoint,
                                                        m: *const GluingMatrix,
                                                        t: *const CouplePoint));
                ext!($lvl; gluing_basis_compute(m: *mut GluingMatrix, k1: *const CouplePoint,
                                                k2: *const CouplePoint));
                ext!($lvl; gluing_compute(o: *mut Gluing, e: *const CoupleCurve,
                                          k1: *const CouplePoint, k2: *const CouplePoint,
                                          dual: bool, verify: bool) -> c_int);
                ext!($lvl; gluing_eval_point_bary(o: *mut ThetaPoint, c1: *const Bary,
                                                  c2: *const Bary, phi: *const Gluing,
                                                  aux: *const ThetaPoint, dual: bool));
                ext!($lvl; gluing_eval_point_special_case(o: *mut ThetaPoint,
                                                          p: *const CouplePoint,
                                                          phi: *const Gluing, dual: bool)
                                                          -> c_int);
                ext!($lvl; splitting_tensor_product_to_couple_point(o: *mut CouplePoint,
                                                                    p: *const ThetaPoint)
                                                                    -> bool);
                ext!($lvl; splitting_check_valid_theta_null_point(th: *const Point) -> bool);
                ext!($lvl; theta_chain_compute_and_eval(n: u16, e: *mut CoupleCurve,
                    k: *const Kernel, o: *mut CoupleCurve, p: *mut CouplePoint, np: u8) -> c_int);
                ext!($lvl; theta_chain_compute_and_eval_E1(n: u16, e: *mut CoupleCurve,
                    k: *const Kernel, o: *mut CoupleCurve, p: *mut CouplePoint, np: u8) -> c_int);
                ext!($lvl; theta_chain_compute_and_eval_E2(n: u16, e: *mut CoupleCurve,
                    k: *const Kernel, o: *mut CoupleCurve, p: *mut CouplePoint, np: u8) -> c_int);
                ext!($lvl; theta_chain_compute_and_eval_verify(n: u16, e: *mut CoupleCurve,
                    k: *const Kernel, o: *mut CoupleCurve, p: *mut CouplePoint, np: u8) -> c_int);
            }

            hd_wrappers!();
        }
    };
}
