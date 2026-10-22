//! The 2x2 block update of the LDL state (vendor `lll/lll_dim4.c`,
//! `fp_update_block`).

use super::config::{LllConfig, NR_ROUNDS};
use super::ldl::Ldl;
use super::{bounded, highmul_p};
use crate::mp::Ibz;
use crate::quat::Mat2;

impl<P: LllConfig> Ldl<P> {
    /// Applies the column unimodular `u` (`b_k' = u00 b_k + u10 b_{k+1}`,
    /// `b_{k+1}' = u01 b_k + u11 b_{k+1}`) to block `(k, k+1)`, updating
    /// `D`, `L` and `Ainv` (C `fp_update_block`).
    pub fn update_block(&mut self, k: usize, u: &Mat2<P>, prec: usize) {
        let kp1 = k + 1;
        let (lb, eb) = (P::L, P::E);
        let mu_old = bounded(self.l[kp1][k], lb);
        let [[u00, u01], [u10, u11]] = u.map(|row| row.map(|x| bounded(x, P::U)));
        let det = bounded(
            bounded(u00 * u11, 2 * P::U) - bounded(u01 * u10, 2 * P::U),
            32,
        );
        let negate = det.ct_lt_mask(&Ibz::ZERO);

        // The local Gram in orthogonalised coordinates.
        let eta0 = bounded(u00.mul_2exp(prec) + mu_old * u10, prec + 2);
        let eta1 = bounded(bounded(u01.mul_2exp(prec), lb) + mu_old * u11, lb);
        let eta0_sq = highmul_p(&eta0, &eta0, prec, prec + 2);
        let tail = |x: &Ibz<P>, y: &Ibz<P>| bounded(bounded(self.d[kp1] * *x, eb) * *y, eb);
        let g00 = bounded(
            highmul_p(&eta0_sq, &self.d[k], prec, eb) + tail(&u10, &u10),
            eb,
        );
        let eta01 = highmul_p(&eta0, &eta1, prec, eb);
        let g01 = bounded(
            highmul_p(&eta01, &self.d[k], prec, eb) + tail(&u10, &u11),
            eb,
        );

        // The diagonal and the block coefficient.
        let (inv, s) = g00.ct_fp_recip(prec, NR_ROUNDS);
        let down = -(s as i32);
        let new_mu = Ibz::ct_highmul(&g01, &inv, down, lb as u32, lb);
        let t = Ibz::ct_highmul(&self.d[kp1], &inv, down, eb as u32, eb);
        self.d[kp1] = highmul_p(&t, &self.d[k], prec, eb);
        self.d[k] = g00;
        self.l[kp1][k] = new_mu;

        // Left tails (j < k).
        let lin = |x: &Ibz<P>, a: &Ibz<P>, y: &Ibz<P>, b: &Ibz<P>| {
            bounded(bounded(*x * *a, lb) + bounded(*y * *b, lb), lb)
        };
        for j in 0..k {
            let (x, y) = (self.l[k][j], self.l[kp1][j]);
            self.l[k][j] = lin(&x, &u00, &y, &u10);
            self.l[kp1][j] = lin(&x, &u01, &y, &u11);
        }

        // Right tails (i > k + 1), from the old coefficient.
        for i in kp1 + 1..4 {
            let (lik, likp1) = (self.l[i][k], self.l[i][kp1]);
            let m = highmul_p(&mu_old, &likp1, prec, lb);
            let eta0_l = lin(&likp1, &u00, &m, &u10);
            let eta1_l = lin(&likp1, &u01, &m, &u11);
            let new_likp1 = bounded(eta0_l - bounded(lik * u10, lb), lb).cneg(negate);
            let t0 = bounded(bounded(lik * u11, lb) - eta1_l, lb).cneg(negate);
            let hm = highmul_p(&new_mu, &new_likp1, prec, lb);
            self.l[i][k] = bounded(t0 + hm, lb);
            self.l[i][kp1] = new_likp1;
        }

        // Ainv columns: det(u) (u11 x - u01 y, u00 y - u10 x).
        let ub = P::U;
        for row in self.ainv.iter_mut() {
            let (x, y) = (row[k], row[kp1]);
            let nk = bounded(bounded(u11 * x, 2 * ub) - u01 * y, ub).cneg(negate);
            let nkp1 = bounded(u00 * y - u10 * x, ub).cneg(negate);
            row[k] = nk;
            row[kp1] = nkp1;
        }
    }
}
