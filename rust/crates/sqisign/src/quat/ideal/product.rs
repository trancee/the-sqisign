//! Products, Gram matrices and intersections of `O0` ideals (vendor
//! `ideal.c`).

use super::Ideal;
use crate::mp::Ibz;
use crate::quat::{dim2, dim4, Alg, AlgElem, Lattice, Mat4, QuatParams};

impl<P: QuatParams> Ideal<P> {
    /// The lattice `self * other` for coprime norms (C `quat_ideal_mul_O0`).
    pub fn mul_o0(&self, other: &Self) -> Lattice<P> {
        let (a, b) = (self, other);
        let (_, u, v) = a.norm.xgcd(&b.norm);
        let (an2, bn2) = (a.norm + a.norm, b.norm + b.norm);
        let x1 = (-(v * (a.x + a.x))).modulo(&an2) * b.norm;
        let x2 = (u * (b.x + b.x)).modulo(&bn2) * a.norm;
        let y1 = (-(v * (a.y + a.y + Ibz::ONE))).modulo(&an2) * b.norm;
        let y2 = (u * (b.y + b.y + Ibz::ONE)).modulo(&bn2) * a.norm;
        let n = a.norm * b.norm;
        let mut basis = dim4::mat_identity();
        basis[0][0] = n + n;
        basis[1][1] = basis[0][0];
        basis[0][2] = x2 + x1;
        basis[1][2] = y2 - y1;
        basis[1][3] = x2 - x1;
        basis[0][3] = -(y2 + y1);
        Lattice {
            denom: Ibz::from_i64(2, 3),
            basis,
        }
    }

    /// The intersection of `self` and `other` for coprime norms, `other`
    /// first multiplied by the conjugate of `split`, a primitive Gaussian
    /// integer of norm coprime to `other` (C `quat_ideal_intersect_O0`).
    pub fn intersect_o0(&self, other: &Self, split: Option<&AlgElem<P>>) -> Self {
        let (a, b) = (self, other);
        let (x, y) = match split {
            None => (b.x, b.y),
            Some(s) => {
                let setter = [b.x + b.x, b.y + b.y + Ibz::ONE];
                let t = b.norm + b.norm;
                let c = [s.coord[0], -s.coord[1]];
                let c = dim2::gaussian_mul(&c, &c).map(|x| x.modulo(&t));
                let c = dim2::gaussian_mul(&setter, &c).map(|x| x.modulo(&t));
                let (u2, v2) = (s.coord[0] * s.coord[0], s.coord[1] * s.coord[1]);
                let (t, _) = (u2 + v2).invmod(&b.norm);
                let x = (c[0].div_2exp(1) * t).modulo(&b.norm);
                let y = ((c[1] - u2 - v2).div_2exp(1) * t).modulo(&b.norm);
                (x, y)
            }
        };
        let x = Ibz::crt(&a.x, &x, &a.norm, &b.norm);
        let y = Ibz::crt(&a.y, &y, &a.norm, &b.norm);
        Self::from_xyn(&x, &y, &(a.norm * b.norm))
    }
}

impl<P: QuatParams> Lattice<P> {
    /// The Gram matrix of twice the reduced norm divided by the norm, for a
    /// lattice output by [`Ideal::mul_o0`] (C
    /// `quat_ideal_product_gram_matrix`).
    pub fn product_gram_matrix(&self) -> Mat4<P> {
        let b = &self.basis;
        let ax2 = (b[0][2] - b[1][3]).div_2exp(1);
        let bx2 = (b[0][2] + b[1][3]).div_2exp(1);
        let by = (-(b[0][3] + b[1][2])).div_2exp(1);
        let ay = (b[1][2] - b[0][3]).div_2exp(1);
        let nanb = b[0][0].div_2exp(1);
        let p = Alg::<P>::pinfty().p;
        let mut g = dim4::mat_zero();
        g[1][1] = b[0][0];
        g[0][0] = g[1][1];
        let (sx, dy) = (ax2 + bx2, ay - by);
        g[2][2] = (sx * sx + dy * dy + p).div_rem(&g[0][0]).0;
        let (r, s) = (ay * by, ax2 * bx2);
        let t = (bx2 + ay) * (ax2 + by);
        g[3][3] = (r - s).div_rem(&nanb).0.mul_2exp(1) + g[2][2];
        g[2][3] = (r + s - t).div_rem(&nanb).0;
        g[0][2] = ax2 + bx2;
        g[0][3] = -(ay + by);
        g[1][2] = ay - by;
        g[1][3] = bx2 - ax2;
        let upper = nanb.bound().max(p.bitsize());
        core::array::from_fn(|i| {
            core::array::from_fn(|j| {
                let mut v = g[i.min(j)][i.max(j)];
                v.set_bound(upper + 5);
                v
            })
        })
    }
}
