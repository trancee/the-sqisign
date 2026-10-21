//! Montgomery models from theta null points (vendor `normalize.c`).

use super::{Basis, Curve, Point};
use crate::gf::{Fp2, FpParams};

/// An x-only change of coordinates `(X : Z) -> (aX + bZ : cX + dZ)`
/// (C `ec_change_coord_matrix_t`).
pub struct ChangeCoordMatrix<P: FpParams> {
    /// Row 1, column 1.
    pub a: Fp2<P>,
    /// Row 1, column 2.
    pub b: Fp2<P>,
    /// Row 2, column 1.
    pub c: Fp2<P>,
    /// Row 2, column 2.
    pub d: Fp2<P>,
}

impl<P: FpParams> Point<P> {
    /// The image of the point under `m` (C `ec_apply_isomorphism`).
    pub fn apply_isomorphism(&self, m: &ChangeCoordMatrix<P>) -> Self {
        Self::new(self.x * m.a + self.z * m.b, self.x * m.c + self.z * m.d)
    }

    /// `(2 (x^4 + z^4) : x^4 - z^4)` (C `ec_compute_montgomery_coefficient`).
    fn montgomery_coefficient(&self) -> Self {
        let xx = self.x.square().square();
        let zz = self.z.square().square();
        let x = xx + zz;
        Self::new(x + x, xx - zz)
    }
}

impl<P: FpParams> Basis<P> {
    /// The image of all three points under `m`.
    pub fn apply_isomorphism(&self, m: &ChangeCoordMatrix<P>) -> Self {
        Self {
            p: self.p.apply_isomorphism(m),
            q: self.q.apply_isomorphism(m),
            pmq: self.pmq.apply_isomorphism(m),
        }
    }
}

/// `0xFFFF_FFFF` when `a == b`, else 0, in constant time.
fn eq_mask(a: u32, b: u32) -> u32 {
    let d = a ^ b;
    (((d | d.wrapping_neg()) >> 31) ^ 1).wrapping_neg()
}

/// The largest element of `list` (by `Fp2::less_than`) and its first index
/// (C `ec_find_max_coefficient_in_list`), in constant time.
fn max_coefficient<P: FpParams>(list: &[Fp2<P>]) -> (Fp2<P>, u32) {
    let (mut max, mut index) = (list[0], 0u32);
    for (i, x) in (1u32..).zip(&list[1..]) {
        let ctl = max.less_than(x);
        max = Fp2::select(&max, x, ctl);
        index ^= (index ^ i) & ctl;
    }
    (max, index)
}

impl<P: FpParams> Curve<P> {
    /// The normalised Montgomery curve of the theta null point `th`, and the
    /// isomorphism from theta to Montgomery coordinates; `None` if `th` is
    /// invalid (C `ec_theta_to_montgomery`).
    pub fn from_theta(th: &Point<P>) -> Option<(Self, ChangeCoordMatrix<P>)> {
        let (ma, mb) = (-th.x, -th.z);
        let (ia, ib) = (th.x.mul_by_i(0), th.z.mul_by_i(0));
        let mont1 = th.montgomery_coefficient();
        let mont2 = Point::new(th.x + th.z, th.x - th.z).montgomery_coefficient();
        let mont3 = Point::new(ia + th.z, th.x + ib).montgomery_coefficient();

        let mut coefs = [mont1.z, mont2.z, mont3.z];
        Fp2::batched_inv(&mut coefs);
        // A zero coefficient zeroes the whole batched inverse.
        if coefs[0].is_zero() != 0 {
            return None;
        }
        let a = [mont1.x * coefs[0], mont2.x * coefs[1], mont3.x * coefs[2]];
        let list = [a[0], a[1], a[2], -a[0], -a[1], -a[2]];
        let (max, i) = max_coefficient(&list);
        let mut e = Self {
            a: max,
            ..Self::default()
        };
        e.normalize_with_a24();

        // i: 0 [b, a; b, -a], 1 [-a, b; b, -a], 2 [ia, ib; -b, a],
        //    3 [b, a; -b, a], 4 [-a, b; -b, a], 5 [ia, ib; b, -a].
        let is = |k: u32| eq_mask(i, k);
        let (m14, m25) = (is(1) | is(4), is(2) | is(5));
        let m = ChangeCoordMatrix {
            a: Fp2::select(&Fp2::select(&th.z, &ma, m14), &ia, m25),
            b: Fp2::select(&Fp2::select(&th.x, &th.z, m14), &ib, m25),
            c: Fp2::select(&th.z, &mb, is(2) | is(3) | is(4)),
            d: Fp2::select(&th.x, &ma, is(0) | is(1) | is(5)),
        };
        Some((e, m))
    }

    /// The normalised curve of the 4-torsion point `four_torsion`, mapping
    /// `basis` along when given; `None` if invalid (C `ec_normalize_montgomery`).
    pub fn normalize_montgomery(
        four_torsion: &Point<P>,
        basis: Option<&mut Basis<P>>,
    ) -> Option<Self> {
        let (e, m) = Self::from_theta(four_torsion)?;
        if let Some(b) = basis {
            *b = b.apply_isomorphism(&m);
        }
        Some(e)
    }
}

#[cfg(test)]
mod tests {
    use super::eq_mask;

    #[test]
    fn eq_mask_is_all_or_nothing() {
        assert_eq!(eq_mask(3, 3), u32::MAX);
        assert_eq!(eq_mask(0, 0), u32::MAX);
        assert_eq!(eq_mask(3, 4), 0);
        assert_eq!(eq_mask(0, 1 << 31), 0);
    }
}
