//! `O0` ideals from generators (vendor `ideal.c`).

use super::Ideal;
use crate::mp::Ibz;
use crate::quat::{dim2, Alg, AlgElem, QuatParams};

impl<P: QuatParams> Ideal<P> {
    /// The ideal `O0 gen + O0 norm` for odd `norm` and `gen` of norm
    /// divisible by `norm` without integer factors of `norm`, or `None` where
    /// C leaves its output unwritten: when `gcd(c^2 + d^2, norm) != 1` after
    /// its `j`-scaling correction (C `quat_ideal_create_O0_inert_odd`).
    pub fn create_o0_inert_odd(gen: &AlgElem<P>, norm: &Ibz<P>) -> Option<Self> {
        let mut g = *gen;
        g.normalize();
        let mut g = g.mod_o0(norm);
        g.denom = Ibz::ONE;
        let mut s = (g.coord[2] * g.coord[2] + g.coord[3] * g.coord[3]).modulo(norm);
        let mut tmp = norm.gcd(&s);
        if !tmp.is_one() {
            let mut q = *norm;
            while !tmp.is_one() {
                q = q.div_rem(&tmp).0;
                tmp = q.gcd(&s);
            }
            let mut helper = AlgElem::from_i32(1, [0, 0, 1, 0]);
            helper.coord[0] = q;
            g = Alg::pinfty().mul(&helper, &g);
            g.coord = g.coord.map(|x| x.modulo(norm));
            g.normalize();
            s = g.coord[2] * g.coord[2] + g.coord[3] * g.coord[3];
            if !s.gcd(norm).is_one() {
                return None;
            }
        }
        let (t, _) = (s + s).invmod(norm);
        let [a, b, c, d] = g.coord;
        let tg = dim2::gaussian_mul(&[a, b], &[c, -d]).map(|x| x.modulo(norm));
        let x = (tg[0] * t).modulo(norm);
        let y = ((tg[1] - s).modulo(norm) * t).modulo(norm);
        Some(Self::from_xyn(&x, &y, norm))
    }

    /// The ideal `O0 gen + O0 norm` for odd `norm`, as the inert part times
    /// the returned Gaussian split part `u + v i` (C
    /// `quat_ideal_create_O0_odd`). The inert part is `None` where
    /// [`Self::create_o0_inert_odd`] is: C's Gaussian gcd clamps remainder
    /// bounds, so it can miss split factors and leave a non-inert part.
    ///
    /// The split part's norm need not divide `norm` (also at signing sizes);
    /// C asserts that it does, and its `NDEBUG` build, which this port
    /// follows, continues with the truncated quotient.
    ///
    /// # Panics
    ///
    /// If that quotient is zero, where C divides by zero.
    pub fn create_o0_odd(gen: &AlgElem<P>, norm: &Ibz<P>) -> (Option<Self>, AlgElem<P>) {
        let alg = Alg::pinfty();
        let mut g = gen.mod_o0(norm);
        g.normalize();
        g.denom = Ibz::ONE;
        let [a, b, c, d] = g.coord;
        let uv = dim2::gaussian_gcd(&[a, b], &[c, -d]);
        let mut setter = AlgElem::from_i32(1, [0; 4]);
        setter.coord[0] = *norm;
        let mut uv = dim2::gaussian_gcd(&uv, &[setter.coord[0], setter.coord[1]]);
        if uv[0].is_zero() && ((-uv[1]).is_one() || uv[1].is_one()) {
            uv = dim2::vec_from(1, 0);
        }
        let n2 = Ibz::sum_two_squares(&uv[0], &uv[1]);
        let (n, _) = norm.div_rem(&n2);
        assert!(!n.is_zero(), "C divides by a zero inert norm");
        let mut setter = AlgElem::from_i32(1, [0; 4]);
        setter.coord[0] = uv[0];
        setter.coord[1] = -uv[1];
        setter.denom = n2;
        let mut g = alg.mul(&g, &setter).mod_o0(norm);
        g.normalize();
        let ideal = Self::create_o0_inert_odd(&g, &n);
        let mut split = AlgElem::from_i32(1, [1, 0, 0, 0]);
        split.coord[0] = uv[0];
        split.coord[1] = uv[1];
        (ideal, split)
    }

    /// The ideal `O0 gen + O0 norm` for a power of two `norm`, or `None` if
    /// it is not inert (C `quat_ideal_create_O0_pow_two`).
    pub fn create_o0_pow_two(gen: &AlgElem<P>, norm: &Ibz<P>) -> Option<Self> {
        if norm.is_one() {
            return Some(Self {
                norm: Ibz::ONE,
                x: Ibz::from_i64(0, 0),
                y: Ibz::ONE,
            });
        }
        let alg = Alg::pinfty();
        let mut g = *gen;
        g.normalize();
        let mut g = g.mod_o0(norm);
        let mut v = g.to_o0_basis();
        if v[2].is_even() == v[3].is_even() {
            if !(v[2].is_odd() && v[3].is_odd() && v[1].is_even() != v[0].is_even()) {
                return None;
            }
            g = alg.mul(&AlgElem::from_i32(2, [0, 1, 1, 0]), &g);
            g.normalize();
            g = g.mod_o0(norm);
            v = g.to_o0_basis();
        }
        if v[2].is_odd() {
            g = alg
                .mul(&AlgElem::from_i32(1, [0, 1, 0, 0]), &g)
                .mod_o0(norm);
            v = g.to_o0_basis();
        }
        let [a, b, c, d] = v;
        let d2 = d * d;
        let (t, _) = (c * c + d2).invmod(norm);
        let x = (a * c + b * d + c * d).modulo(norm);
        let x = (t * x).modulo(norm);
        let y = (b * c - a * d - d2).modulo(norm);
        let y = (y * t).modulo(norm);
        Some(Self::from_xyn(&x, &y, norm))
    }

    /// The ideal `O0 gen + O0 norm`, and whether it is inert (C
    /// `quat_ideal_create_O0_inert`).
    ///
    /// # Panics
    ///
    /// If [`Self::create_o0_odd`] finds no inert part, where C would reduce
    /// modulo its zero norm (which its `ibz_mod` asserts against); that needs
    /// a `gen` with zero `j`-part.
    pub fn create_o0_inert(gen: &AlgElem<P>, norm: &Ibz<P>) -> (Self, bool) {
        let alg = Alg::pinfty();
        let val = norm.two_adic();
        let n_odd = norm.div_2exp(val);
        let (odd, split) = Self::create_o0_odd(gen, &n_odd);
        let odd = odd.expect("C asserts a nonzero modulus");
        let inert_odd = alg.norm(&split).0.is_one();
        let mut inert_two = true;
        let mut n_even = Ibz::ONE.mul_2exp(val);
        let mut elem = *gen;
        if n_even.two_adic() > 0 {
            elem = elem.mod_o0(&n_even);
            let mut v = elem.to_o0_basis();
            let split = AlgElem::from_i32(2, [1, -1, 0, 0]);
            while !n_even.is_one()
                && v[2].is_odd() == v[3].is_odd()
                && (v[1].is_even() == v[0].is_even() || v[3].is_even())
            {
                inert_two = false;
                elem = alg.mul(&split, &elem).mod_o0(&n_even);
                v = elem.to_o0_basis();
                n_even = n_even.div_2exp(1);
            }
        }
        let even = Self::create_o0_pow_two(&elem, &n_even).expect("inert by construction");
        (odd.intersect_o0(&even, None), inert_odd && inert_two)
    }
}
