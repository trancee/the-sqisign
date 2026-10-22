//! The constant-time dual reduction of a quaternion ideal (vendor
//! `lll/lll_dim4.c`, parts 2, 4 and 5): fixed LLL tours over the
//! closed-form dual LDL, then Minkowski reduction.

use super::bounded;
use super::config::{lg_outer_its, LllConfig};
use super::gram;
use super::ldl::{Hnf, Ldl};
use super::minkowski::{self, eval_norm};
use crate::mp::Ibz;
use crate::quat::{Alg, Lattice, Mat4, QuatParams, Vec4};

/// The reduced lattice, the Gram diagonal of the reduced dual and the
/// transform with `reduced.basis = hnf.basis * ainv`.
pub struct DualReduced<P: QuatParams> {
    /// The reduced basis, with the denominator of the input.
    pub reduced: Lattice<P>,
    /// The Gram diagonal of the reduced dual, in the order of `reduced`.
    pub gram_diag: Vec4<P>,
    /// The transform from the input basis.
    pub ainv: Mat4<P>,
}

/// One fixed-cost pass over block `(k, k+1)` (C `ct_reduce_block`).
fn reduce_block<P: LllConfig>(st: &mut Ldl<P>, k: usize, prec: usize, round: usize) {
    let mut g = gram::materialise(&st.d[k], &st.d[k + 1], &st.l[k + 1][k], prec);
    let u = gram::lehmer(&mut g, lg_outer_its::<P>(round));
    st.update_block(k, &u, prec);
    st.size_reduce_from(k, prec);
}

impl<P: QuatParams> Lattice<P> {
    /// Reduces the ideal with the HNF basis `self` (diagonal entries
    /// `2N, 2N, ...`) through its dual, in constant time for honestly
    /// generated inputs (C `quat_lll_dual_reduce_ideal`).
    pub fn dual_reduce_ideal(&self, alg: &Alg<P>) -> DualReduced<P> {
        let prec = P::PREC;
        let m = &self.basis;
        let hnf = |x: &Ibz<P>| bounded(*x, P::HNF);
        let (two_n, p) = (hnf(&m[0][0]), hnf(&alg.p));
        let (e, dq, c, b) = (hnf(&m[0][2]), hnf(&m[0][3]), hnf(&m[1][2]), hnf(&m[1][3]));
        let h = Hnf {
            two_n: &two_n,
            b: &b,
            c: &c,
            dq: &dq,
            e: &e,
            p: &p,
        };
        let mut st = Ldl::init_dual(&h, prec);
        for t in 0..P::TOURS {
            for (s, k) in [1, 0, 2].into_iter().enumerate() {
                reduce_block(&mut st, k, prec, 3 * t + s);
            }
        }
        let res = minkowski::reduce(&st, prec);

        // The pair swap (2, 3, 0, 1) on rows, the column order reversed.
        const SIGMA: [usize; 4] = [2, 3, 0, 1];
        let ainv: Mat4<P> = core::array::from_fn(|i| {
            core::array::from_fn(|c| bounded(res.ainv[SIGMA[i]][3 - c], P::AINV))
        });
        let basis = core::array::from_fn(|r| {
            core::array::from_fn(|c| {
                (0..4).fold(bounded(Ibz::ZERO, P::BASIS), |acc, k| {
                    bounded(acc + hnf(&m[r][k]) * ainv[k][c], P::BASIS)
                })
            })
        });
        let gram_diag = core::array::from_fn(|i| {
            bounded(eval_norm(&res.g, &res.t[3 - i]), P::GRAM).mul_2exp(P::D_SHIFT)
        });
        DualReduced {
            reduced: Self {
                denom: self.denom,
                basis,
            },
            gram_diag,
            ainv,
        }
    }

    /// A box `|x_i| <= box[i]` whose image `x U` covers the ball of
    /// `radius` (relative to the Gram matrix scaled by `1 / 2N`) in the
    /// ideal with HNF basis `self`, and whether the box is more than the
    /// origin (C `quat_lattice_bound_parallelogram`).
    pub fn bound_parallelogram(&self, radius: &Ibz<P>) -> (Vec4<P>, Mat4<P>, bool) {
        let alg = Alg::pinfty();
        let red = self.dual_reduce_ideal(&alg);
        let den = self.basis[0][0] * alg.p;
        let mut bx = red
            .gram_diag
            .map(|g| (g * *radius).div_rem(&den).0.sqrt_floor());
        let nontrivial = bx.iter().any(|b| !b.is_zero());
        let tight = |x: &mut Ibz<P>| x.set_bound(x.bitsize() + 1);
        let mut u: Mat4<P> = core::array::from_fn(|i| core::array::from_fn(|j| red.ainv[j][i]));
        u.iter_mut().flatten().for_each(tight);
        bx.iter_mut().for_each(tight);
        (bx, u, nontrivial)
    }
}
