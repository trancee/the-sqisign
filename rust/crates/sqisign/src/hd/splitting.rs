//! The final change of coordinates from a split theta structure to an
//! elliptic product (`splitting.c`).

use super::{CoupleCurve, CouplePoint, ThetaPoint};
use crate::ec::{Curve, Point};
use crate::gf::FpParams;

/// Which codomain curves a chain computes, and whether it adds the
/// verification checks (C `int8_t mode`: 0, 1, 2, -1).
#[derive(Clone, Copy, PartialEq, Eq)]
pub(super) enum Mode {
    /// Both curves (0).
    Both,
    /// `E1` only (1).
    E1,
    /// `E2` only (2).
    E2,
    /// `E1` only, with extra validity checks (-1).
    Verify,
}

/// `(x + z, y + t, y - t, x - z)` (C `splitting_change_coord_to_product_theta_struct`).
fn change_coord_to_product<P: FpParams>(p: &ThetaPoint<P>) -> ThetaPoint<P> {
    ThetaPoint::new(p.x + p.z, p.y + p.t, p.y - p.t, p.x - p.z)
}

/// The couple point `((x : z), (x : y))` of a product theta point, or `None`
/// if `P` is not a product (C `splitting_tensor_product_to_couple_point`).
pub fn tensor_product_to_couple_point<P: FpParams>(p: &ThetaPoint<P>) -> Option<CouplePoint<P>> {
    if p.is_product() == 0 {
        return None;
    }
    Some(CouplePoint {
        p1: Point::new(p.x, p.z),
        p2: Point::new(p.x, p.y),
    })
}

/// Whether `(x : z)` is a valid elliptic theta null point: `x, z != 0` and
/// `x^4 != z^4` (C `splitting_check_valid_theta_null_point`).
pub fn check_valid_theta_null_point<P: FpParams>(th: &Point<P>) -> bool {
    let xx = th.x.square().square();
    let zz = th.z.square().square();
    th.x.is_zero() == 0 && th.z.is_zero() == 0 && xx.ct_eq(&zz) == 0
}

/// Splits the dual null point and the pushed points into an elliptic
/// product in Montgomery form (C `splitting_to_elliptic_product`).
///
/// Curves not requested by `mode` are left as `E0`, and the matching point
/// components keep their raw theta-derived coordinates. `p12` is written only
/// on success.
pub(super) fn to_elliptic_product<P: FpParams>(
    p12: &mut [CouplePoint<P>],
    dual_null_point: &ThetaPoint<P>,
    pts: &[ThetaPoint<P>],
    mode: Mode,
) -> Option<CoupleCurve<P>> {
    let dual = change_coord_to_product(dual_null_point);
    let zero = dual.x.is_zero() | dual.y.is_zero() | dual.z.is_zero() | dual.t.is_zero();
    if zero != 0 {
        return None;
    }
    let th = tensor_product_to_couple_point(&dual)?;
    let mut out = pts
        .iter()
        .map(|p| tensor_product_to_couple_point(&change_coord_to_product(p)))
        .collect::<Option<Vec<_>>>()?;
    if mode == Mode::Verify && !check_valid_theta_null_point(&th.p2) {
        return None;
    }
    let mut e34 = CoupleCurve {
        e1: Curve::default(),
        e2: Curve::default(),
    };
    if mode != Mode::E2 {
        let (e, m) = Curve::from_theta(&th.p1)?;
        e34.e1 = e;
        out.iter_mut()
            .for_each(|q| q.p1 = q.p1.apply_isomorphism(&m));
    }
    if matches!(mode, Mode::Both | Mode::E2) {
        let (e, m) = Curve::from_theta(&th.p2)?;
        e34.e2 = e;
        out.iter_mut()
            .for_each(|q| q.p2 = q.p2.apply_isomorphism(&m));
    }
    p12.copy_from_slice(&out);
    Some(e34)
}

#[cfg(test)]
mod tests {
    use super::{to_elliptic_product, CouplePoint, Mode, Point, ThetaPoint};
    use crate::gf::{Fp2, P324};

    fn small(v: i32) -> Fp2<P324> {
        let x = Fp2::ONE.mul_small(v.unsigned_abs());
        if v < 0 {
            -x
        } else {
            x
        }
    }

    /// The theta point `(a : b : c : d)`.
    fn th(a: i32, b: i32, c: i32, d: i32) -> ThetaPoint<P324> {
        ThetaPoint::new(small(a), small(b), small(c), small(d))
    }

    /// `(3 : 3 : -1 : -1)` maps to the product `(1 : 1 : 2 : 2)`, whose second
    /// factor `(1 : 1)` is no valid theta null point.
    #[test]
    fn invalid_second_factor_is_rejected_when_computed() {
        let dual = th(3, 3, -1, -1);
        assert!(to_elliptic_product(&mut [], &dual, &[], Mode::Verify).is_none());
        assert!(to_elliptic_product(&mut [], &dual, &[], Mode::E1).is_some());
        assert!(to_elliptic_product(&mut [], &dual, &[], Mode::E2).is_none());
        assert!(to_elliptic_product(&mut [], &dual, &[], Mode::Both).is_none());
    }

    /// `(3 : 3 : -1 : 1)` maps to the product `(1 : 2 : 1 : 2)`, whose first
    /// factor `(1 : 1)` is invalid.
    #[test]
    fn invalid_first_factor_is_rejected_when_computed() {
        let dual = th(3, 3, -1, 1);
        assert!(to_elliptic_product(&mut [], &dual, &[], Mode::E1).is_none());
        assert!(to_elliptic_product(&mut [], &dual, &[], Mode::Verify).is_none());
        let o = Point::IDENTITY;
        let mut p12 = [CouplePoint { p1: o, p2: o }];
        assert!(to_elliptic_product(&mut p12, &dual, &[dual], Mode::E2).is_some());
    }

    /// A pushed point that is not a product fails the split and leaves the
    /// points untouched.
    #[test]
    fn non_product_points_are_rejected() {
        let dual = th(3, 3, -1, -1);
        let o = Point::IDENTITY;
        let mut p12 = [CouplePoint { p1: o, p2: o }];
        assert!(to_elliptic_product(&mut p12, &dual, &[th(1, 2, 3, 5)], Mode::E1).is_none());
        // The null point itself: a zero coordinate, or not a product.
        assert!(to_elliptic_product(&mut [], &th(1, 0, 1, 0), &[], Mode::E1).is_none());
        assert!(to_elliptic_product(&mut [], &th(1, 2, 3, 5), &[], Mode::E1).is_none());
        assert!(to_elliptic_product(&mut p12, &dual, &[th(3, 3, -1, -1)], Mode::E1).is_some());
    }
}
