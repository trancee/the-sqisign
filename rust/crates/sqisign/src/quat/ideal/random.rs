//! Random `O0` ideals (vendor `ideal.c`).

use super::Ideal;
use crate::mp::Ibz;
use crate::quat::{Alg, AlgElem, Draw, QuatParams};

impl<P: QuatParams> Ideal<P> {
    /// A uniformly random ideal of prime norm `norm`, `norm != p` (C
    /// `quat_random_ideal_O0_given_prime_norm`).
    pub fn random_given_prime_norm(norm: &Ibz<P>, draw: &mut Draw) -> Self {
        let p = Alg::<P>::pinfty().p;
        let n2 = *norm + *norm;
        let n4 = n2 + n2;
        let nm1 = *norm - Ibz::ONE;
        let (x, tmp, rp) = loop {
            let x = Ibz::rand_interval(&Ibz::ZERO, &nm1, draw.stream());
            let x = x + x;
            let tmp = (x * x).modulo(&n4);
            let tmp = (-(p + tmp)).modulo(&n4);
            let rp = tmp.modulo(norm);
            if rp.legendre(norm) == 1 && (tmp.get() & 3) < 2 {
                break (x, tmp, rp);
            }
        };
        let rp = rp
            .sqrt_mod_p(norm, draw.default())
            .expect("a quadratic residue");
        let r4 = Ibz::from_i64((tmp.get() & 1).into(), 2);
        let y = Ibz::crt(&r4, &rp, &Ibz::from_i64(4, 4), norm);
        let bit = Ibz::<P>::rand_interval(&Ibz::ZERO, &Ibz::ONE, draw.stream()).get() & 1;
        let y = if bit == 1 { -y } else { y };
        let y = (y.modulo(&n2) - Ibz::ONE).div_2exp(1);
        Self::from_xyn(&x.div_2exp(1), &y, norm)
    }

    /// A random ideal of odd norm `norm` times `shift_gen`, as the inert
    /// part and the Gaussian split part, or `None` if no element of norm
    /// `QUAT_prime_cofactor * norm` was found (C
    /// `quat_random_ideal_O0_given_arbitrary_odd_norm`). The inert part is
    /// `None` where C leaves its output ideal unwritten (see
    /// [`Self::create_o0_odd`]).
    ///
    /// C also rejects zero generators, but every coordinate C samples here
    /// is at least 1.
    pub fn random_given_odd_norm(
        norm: &Ibz<P>,
        shift_gen: &AlgElem<P>,
        draw: &mut Draw,
    ) -> Option<(Option<Self>, AlgElem<P>)> {
        let alg = Alg::pinfty();
        let gen = AlgElem::represent_integer(&(P::prime_cofactor() * *norm), draw)?;
        let rerand = loop {
            let mut r = AlgElem::from_i32(1, [0; 4]);
            for c in &mut r.coord {
                *c = Ibz::rand_interval(&Ibz::ONE, norm, draw.stream());
            }
            if alg.norm(&r).0.gcd(norm).is_one() {
                break r;
            }
        };
        let gen = alg.mul(&gen, &rerand).mod_o0(norm);
        let gen = alg.mul(&gen, &shift_gen.mod_o0(norm)).mod_o0(norm);
        Some(Self::create_o0_odd(&gen, norm))
    }
}
