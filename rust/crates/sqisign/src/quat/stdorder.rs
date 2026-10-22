//! The standard maximal order `O0 = <1, i, (i + j) / 2, (1 + ij) / 2>`
//! (vendor `stdorder.c`).

use super::{dim4, Alg, AlgElem, Draw, Lattice, QuatParams, Vec4};
use crate::mp::{Ibz, MpParams};

impl<P: MpParams> Lattice<P> {
    /// `O0` with the C bounds (C `quat_lattice_O0_set` and the precomputed
    /// `MAXORD_O0`, which agree).
    pub fn o0() -> Self {
        let mut basis = dim4::mat_zero();
        basis[0][0] = Ibz::TWO;
        basis[1][1] = Ibz::TWO;
        basis[2][2] = Ibz::ONE;
        basis[1][2] = Ibz::ONE;
        basis[3][3] = Ibz::ONE;
        basis[0][3] = Ibz::ONE;
        Self {
            denom: Ibz::TWO,
            basis,
        }
    }
}

impl<P: MpParams> AlgElem<P> {
    /// The coordinates of `self`, an element of `O0`, in the basis of
    /// [`Lattice::o0`] (C `quat_change_to_O0_basis`).
    pub fn to_o0_basis(&self) -> Vec4<P> {
        let [c0, c1, c2, c3] = self.coord;
        [c0 - c3, c1 - c2, c2.mul_2exp(1), c3.mul_2exp(1)].map(|x| x.div_rem(&self.denom).0)
    }

    /// `self`, an element of `O0`, with its `O0` coordinates reduced into
    /// `[0, modulus)` (C `quat_mod_O0`).
    pub fn mod_o0(&self, modulus: &Ibz<P>) -> Self {
        let coords = self.to_o0_basis().map(|x| x.modulo(modulus));
        let o0 = Lattice::o0();
        let mut coord = dim4::mat_eval(&o0.basis, &coords);
        coord
            .iter_mut()
            .for_each(|x| x.set_bound(modulus.bound() + 2));
        Self {
            denom: o0.denom,
            coord,
        }
    }
}

impl<P: QuatParams> AlgElem<P> {
    /// A random element of `O0` of odd norm `n_gamma` with nonzero
    /// `j`-coordinate, or `None` after about `n_gamma / p` attempts (C
    /// `quat_represent_integer`).
    pub fn represent_integer(n_gamma: &Ibz<P>, draw: &mut Draw) -> Option<Self> {
        let p = Alg::<P>::pinfty().p;
        let bit_bound = p.bitsize();
        let mut counter = n_gamma.div_rem(&(p * p).sqrt_floor()).0;
        counter.set_bound(bit_bound);
        let mut sq_bound = n_gamma.div_rem(&p).0 - Ibz::ONE;
        sq_bound.set_bound(bit_bound);
        let bound = sq_bound.sqrt_floor();
        while !counter.is_zero() {
            counter = counter - Ibz::ONE;
            counter.set_bound(bit_bound);
            let z = Ibz::rand_interval(&Ibz::ONE, &bound, draw.stream());
            let z2 = z * z;
            let mut temp = (*n_gamma - z2 * p).div_rem(&p).0;
            temp.set_bound(bit_bound);
            let temp = temp.sqrt_floor();
            // C skips a zero second bound, which `floor(n_gamma / p) >= 2`
            // rules out (at 1 the first interval is empty and C divides by
            // zero).
            let found = (!temp.is_zero())
                .then(|| {
                    let t = Ibz::rand_interval(&Ibz::ONE, &temp, draw.stream());
                    let mut target = *n_gamma - (z2 + t * t) * p;
                    target.set_bound(n_gamma.bound());
                    (t, target)
                })
                .filter(|(_, target)| target.probab_prime(P::PRIMALITY_NUM_ITER, draw.default()))
                .and_then(|(t, target)| {
                    let (mut x, mut y) = target.cornacchia_prime()?;
                    x.set_bound(target.bound() / 2 + 2);
                    y.set_bound(target.bound() / 2 + 2);
                    Some(Self {
                        denom: Ibz::ONE,
                        coord: [x, y, z, t],
                    })
                });
            if found.is_some() {
                return found;
            }
        }
        None
    }
}
