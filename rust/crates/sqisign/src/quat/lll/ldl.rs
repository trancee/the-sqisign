//! The fixed-point LDL state of the dimension-4 reduction (vendor
//! `lll/lll_dim4.c`, part 1): closed-form dual initialisation and
//! size reduction mirrored on the accumulated inverse transform.

use super::bounded;
use super::config::{LllConfig, NR_ROUNDS};
use crate::mp::Ibz;
use crate::quat::{Mat4, Vec4};

/// `G = L D L^T` at scale `2^prec`, with `Ainv` the inverse transform
/// applied so far (C `fp_ldl_t`).
pub struct Ldl<P: LllConfig> {
    /// Raw diagonal, bound `E`.
    pub d: Vec4<P>,
    /// Lower unitriangular factor at scale `2^prec`, bound `L`.
    pub l: Mat4<P>,
    /// The exact inverse transform, bound `U`.
    pub ainv: Mat4<P>,
}

/// The HNF entries the dual LDL depends on (C qlapoty quadruple).
pub struct Hnf<'a, P: LllConfig> {
    pub two_n: &'a Ibz<P>,
    pub b: &'a Ibz<P>,
    pub c: &'a Ibz<P>,
    pub dq: &'a Ibz<P>,
    pub e: &'a Ibz<P>,
    pub p: &'a Ibz<P>,
}

/// The identity at bound `bits`.
fn identity<P: LllConfig>(bits: usize, one: Ibz<P>) -> Mat4<P> {
    let zero = bounded(Ibz::ZERO, bits);
    let mut m = [[zero; 4]; 4];
    for (i, row) in m.iter_mut().enumerate() {
        row[i] = bounded(one, bits);
    }
    m
}

impl<P: LllConfig> Ldl<P> {
    /// The closed-form dual LDL of the HNF `(2N, B, C, Dq, E, p)`:
    /// `D = ((2N)^2, (2N)^2, p, p)` scaled down by `D_SHIFT`, and
    /// `L[2][0] = -E/2N`, `L[2][1] = -Dq/2N`, `L[3][0] = -C/2N`,
    /// `L[3][1] = -B/2N`, then size-reduced (C `fp_ldl_init_dual`).
    pub fn init_dual(h: &Hnf<'_, P>, prec: usize) -> Self {
        let l = identity(P::L, Ibz::ONE.mul_2exp(prec));
        let shift = P::D_SHIFT;
        let sq = (*h.two_n * *h.two_n).ct_shift(-(shift as i32), shift as u32, P::E);
        let p = h.p.ct_shift(-(shift as i32), shift as u32, P::E);
        let mut st = Self {
            d: [sq, sq, p, p],
            l,
            ainv: identity(P::U, Ibz::ONE),
        };
        let (r, s) = h.two_n.ct_fp_recip(prec, NR_ROUNDS);
        let max_shift = h.two_n.bound() as u32;
        let neg_ratio = |x: &Ibz<P>| -Ibz::ct_highmul(x, &r, -(s as i32), max_shift, P::E);
        st.l[2][0] = neg_ratio(h.e);
        st.l[2][1] = neg_ratio(h.dq);
        st.l[3][0] = neg_ratio(h.c);
        st.l[3][1] = neg_ratio(h.b);
        st.size_reduce_from(0, prec);
        st
    }

    /// Restores `|L[r][j]| <= 2^(prec-1)` for rows `r >= k`, mirroring each
    /// step `L[r] -= q L[j]` on `Ainv` as column `j += q` column `r` (C
    /// `fp_size_reduce_from`).
    pub fn size_reduce_from(&mut self, k: usize, prec: usize) {
        let (lb, ub) = (P::L, P::U);
        for r in k..4 {
            for j in (0..r).rev() {
                let q = self.l[r][j].ct_round_shift(prec, ub);
                for m in 0..j {
                    self.l[j][m].set_bound(P::PREC);
                    let prod = bounded(self.l[j][m] * q, lb);
                    self.l[r][m] = bounded(self.l[r][m] - prod, lb);
                }
                let prod = bounded(q.mul_2exp(prec), lb);
                self.l[r][j] = bounded(self.l[r][j] - prod, lb);
                for row in self.ainv.iter_mut() {
                    let prod = bounded(row[r] * q, 2 * ub);
                    row[j] = bounded(row[j] + prod, ub);
                }
            }
        }
    }
}
