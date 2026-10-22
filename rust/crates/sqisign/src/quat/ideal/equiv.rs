//! Reduced bases and small equivalent ideals (vendor `ideal.c`,
//! `lll_applications.c`).

use super::Ideal;
use crate::mp::Ibz;
use crate::prng::Domain;
use crate::quat::{dim4, Alg, AlgElem, Draw, Lattice, QuatParams};

/// `ibz_bitsize(p)`, as a signed C `int`.
fn p_bits<P: QuatParams>() -> i64 {
    Alg::<P>::pinfty().p.bitsize() as i64
}

/// `max(b, 1)` as a bound; C clamps the nonpositive bounds of the
/// `j`-coordinates of norms below `sqrt(p)`, which are then zero.
fn at_least_one(b: i64) -> usize {
    b.max(1) as usize
}

impl<P: QuatParams> Ideal<P> {
    /// A reduced basis of `self` with C's output bounds (C
    /// `quat_ideal_reduce_basis`).
    pub fn reduce_basis(&self) -> Lattice<P> {
        let nb = self.norm.bound();
        let mut r = self.to_lattice().reduce_o0_ideal(&Alg::pinfty(), nb + 2);
        r.denom = Ibz::from_i64(2, 3);
        let (bs, nb) = (p_bits::<P>(), nb as i64);
        for (i, row) in r.basis.iter_mut().enumerate() {
            let b = 1 + ((if i < 2 { bs } else { 0 }) + nb) / 2;
            row.iter_mut().for_each(|x| x.set_bound(b as usize));
        }
        for row in &mut r.basis[..2] {
            row[0].set_bound((1 + (bs / 2 + nb) / 2 + 1) as usize);
        }
        for row in &mut r.basis[2..] {
            row[0].set_bound(at_least_one(1 + (-bs / 2 + nb) + 1));
        }
        r
    }

    /// The shortest element of `self` and the ideal it makes equivalent to
    /// `self`, in canonical form up to `i` (C `quat_ideal_shortest_equivalent`
    /// without `gen`). Draws from the default stream only.
    pub fn shortest_equivalent(&self, default: &mut Domain) -> (AlgElem<P>, Self) {
        let alg = Alg::pinfty();
        let lat = self.reduce_basis();
        let mut elem = AlgElem {
            denom: lat.denom,
            coord: dim4::mat_eval(&lat.basis, &dim4::vec_from([1, 0, 0, 0])),
        };
        let (bs, nb) = (p_bits::<P>(), self.norm.bound() as i64);
        for c in &mut elem.coord[..2] {
            c.set_bound(((bs / 2 + nb) / 2 + 1) as usize);
        }
        for c in &mut elem.coord[2..] {
            c.set_bound(at_least_one((-bs / 2 + nb) / 2 + 1));
        }
        elem.denom.set_bound(3);
        let mut equiv = elem;
        let mut n = alg.norm(&elem).0.div_rem(&self.norm).0;
        n.set_bound(bs as usize + 1);
        let gcd = n.gcd(&self.norm);
        let mut elem = elem.conj();
        if !gcd.is_one() {
            let prod = n * self.norm;
            let mut draw = Draw::default_only(default);
            let (tmp, _) =
                Self::small_equivalent_coprime_enumeration(&lat, &self.norm, &prod, &mut draw);
            elem = alg.mul(&tmp, &elem);
            elem.denom = elem.denom * self.norm;
            elem.normalize();
            elem = elem.mod_o0(&n);
        }
        let (mut red, _) = Self::create_o0_inert(&elem, &n);
        if red.x.mul_2exp(1) > red.norm {
            red.x = red.norm - red.x;
            red.y = red.norm - red.y - Ibz::ONE;
            equiv = alg.mul(&AlgElem::from_i32(1, [0, 1, 0, 0]), &equiv);
        }
        (equiv, red)
    }

    /// A random small element `gen` of the ideal with the reduced basis
    /// `lattice` and norm `norm`, and the ideal `equiv` it makes equivalent,
    /// of odd norm that is prime if `coprime_to` is zero and else coprime to
    /// `coprime_to` unless that is 2, or `None` for `equiv` where C leaves it
    /// unwritten (C `quat_ideal_small_equivalent_coprime_enumeration`).
    fn small_equivalent_coprime_enumeration(
        lattice: &Lattice<P>,
        norm: &Ibz<P>,
        coprime_to: &Ibz<P>,
        draw: &mut Draw,
    ) -> (AlgElem<P>, Option<Self>) {
        let alg = Alg::pinfty();
        let prime = coprime_to.is_zero();
        let add_check = *coprime_to != Ibz::TWO;
        let bound = (i32::BITS - P::EQUIV_BOUND_COEFF.leading_zeros()) as usize;
        let bs = alg.p.bitsize();
        let nb = norm.bound();
        loop {
            // C redraws all-zero coefficients.
            let coeffs = core::iter::repeat_with(|| -> [Ibz<P>; 4] {
                core::array::from_fn(|_| {
                    Ibz::rand_interval_minm_m(P::EQUIV_BOUND_COEFF, draw.stream())
                })
            })
            .find(|c| !dim4::vec_is_zero(c))
            .expect("an endless draw finds nonzero coefficients");
            let (coeffs, _) = dim4::vec_scalar_div(&dim4::vec_content(&coeffs), &coeffs);
            let mut elem = AlgElem {
                denom: lattice.denom,
                coord: dim4::mat_eval(&lattice.basis, &coeffs),
            };
            for (i, c) in elem.coord.iter_mut().enumerate() {
                c.set_bound((nb + bound * 2 + if i < 2 { bs } else { 0 }) / 2);
            }
            let mut n = alg.norm(&elem).0.div_rem(norm).0;
            let found = n.is_odd()
                && (!add_check
                    || if prime {
                        n.probab_prime(P::PRIMALITY_NUM_ITER, draw.default())
                    } else {
                        n.gcd(coprime_to).is_one()
                    });
            if found {
                n.set_bound(bound * 2 + bs + 2);
                let (equiv, split) = Self::create_o0_odd(&elem.conj(), &n);
                let gen = alg.mul(&split, &elem);
                let (n, _) = alg.norm(&split);
                let gen = AlgElem {
                    denom: gen.denom,
                    coord: dim4::vec_scalar_div(&n, &gen.coord).0,
                };
                return (gen, equiv);
            }
        }
    }

    /// A random small element `gen` of `self` and the ideal it makes
    /// equivalent to `self`, of odd norm that is prime if `coprime_to` is
    /// zero and else coprime to `coprime_to` unless that is 2 (C
    /// `quat_ideal_small_equivalent_coprime` with `equiv` aliasing `ideal`,
    /// as at every C call site, so it stays `self` where C leaves it
    /// unwritten).
    pub fn small_equivalent_coprime(
        &self,
        coprime_to: &Ibz<P>,
        draw: &mut Draw,
    ) -> (AlgElem<P>, Self) {
        let red = self.reduce_basis();
        let (mut gen, equiv) =
            Self::small_equivalent_coprime_enumeration(&red, &self.norm, coprime_to, draw);
        let equiv = equiv.unwrap_or(*self);
        gen.normalize();
        let b = (self.norm.bound() + equiv.norm.bound()) / 2 + 1;
        gen.coord.iter_mut().for_each(|c| c.set_bound(b));
        gen.denom.set_bound(3);
        (gen, equiv)
    }
}
