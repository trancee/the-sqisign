//! Full-rank lattices of the algebra (vendor `lattice.c`).

use super::{dim4, AlgElem, Mat4, Vec4};
use crate::mp::{Ibz, MpParams};

/// The lattice spanned by the columns of `basis / denom` in the basis
/// `(1, i, j, ij)` (C `quat_lattice_t`).
#[derive(Clone, Copy, Debug)]
pub struct Lattice<P: MpParams> {
    /// The non-zero denominator.
    pub denom: Ibz<P>,
    /// The basis vectors as columns.
    pub basis: Mat4<P>,
}

impl<P: MpParams> Lattice<P> {
    /// The coordinates of `elem` in the basis of `self` and whether they are
    /// all integers, that is whether `self` contains `elem` (C
    /// `quat_lattice_contains`).
    ///
    /// `self` must have an upper-triangular basis with a non-zero diagonal.
    pub fn contains(&self, elem: &AlgElem<P>) -> (Vec4<P>, bool) {
        let mut elem_vec = dim4::vec_scalar_mul(&self.denom, &elem.coord);
        let prod = dim4::mat_scalar_mul(&elem.denom, &self.basis);
        let mut coords = [Ibz::ZERO; 4];
        let mut res = true;
        for i in (0..4).rev() {
            let (q, r) = elem_vec[i].div_rem(&prod[i][i]);
            res = res && r.is_zero();
            let column = core::array::from_fn(|k| prod[k][i]);
            elem_vec = dim4::vec_sub(&elem_vec, &dim4::vec_scalar_mul(&q, &column));
            coords[i] = q;
        }
        (coords, res)
    }
}
