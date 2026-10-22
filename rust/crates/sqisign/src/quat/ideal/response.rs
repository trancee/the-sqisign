//! Signing response elements (vendor `lattice.c`, `protocol.c`).

use super::Ideal;
use crate::mp::Ibz;
use crate::prng::Domain;
use crate::quat::{dim4, AlgElem, Draw, Lattice, QuatParams};

impl<P: QuatParams> Lattice<P> {
    /// A random element of `self`, a lattice output by [`Ideal::mul_o0`], of
    /// norm divided by the norm at most `radius` and not divisible by 2,
    /// with that quotient; `None` if the bounding parallelogram holds only
    /// the origin (C `quat_lattice_sample_from_ball`).
    pub fn sample_from_ball(
        &self,
        radius: &Ibz<P>,
        stream: &mut Domain,
    ) -> Option<(AlgElem<P>, Ibz<P>)> {
        let g = self.product_gram_matrix();
        let rad = radius.mul_2exp(1);
        let (bx, u, ok) = self.bound_parallelogram(&rad);
        if !ok {
            return None;
        }
        let (x, tmp) = loop {
            let x = bx.map(|b| {
                if b.is_zero() {
                    return Ibz::ZERO;
                }
                let mut x = Ibz::rand_interval(&Ibz::ZERO, &b.mul_2exp(1), stream) - b;
                x.set_bound(b.bound());
                x
            });
            let x = dim4::mat_eval_t(&x, &u);
            let tmp = dim4::qf_eval(&g, &x);
            if tmp.get() % 4 != 0 && tmp <= rad {
                break (x, tmp);
            }
        };
        let mut res = AlgElem {
            denom: self.denom,
            coord: dim4::mat_eval(&self.basis, &x),
        };
        res.normalize();
        res.denom.set_bound(3);
        let b = (radius.bound() + self.basis[0][0].bound()) / 2 + res.denom.bound() + 3;
        res.coord.iter_mut().for_each(|c| c.set_bound(b));
        let mut norm = tmp.div_2exp(1);
        norm.set_bound(radius.bound());
        Some((res, norm))
    }
}

/// The outputs of [`Ideal::response_element`].
pub struct Response<P: QuatParams> {
    /// The ideal equivalent to the intersection of the secret and the
    /// challenge ideals.
    pub sk_chall_ideal: Ideal<P>,
    /// The response element.
    pub resp_quat: AlgElem<P>,
    /// Its norm divided by the norm of its ideal.
    pub norm: Ibz<P>,
    /// The conjugate of the element making `sk_chall_ideal` equivalent.
    pub sk_chall_quat: AlgElem<P>,
}

impl<P: QuatParams> Ideal<P> {
    /// The signing response for the secret ideal `self`, the challenge
    /// ideal `chall_two` with split part `chall_split`, the commitment ideal
    /// `commit` and response length `e` (C `quat_response_element`).
    /// Samples from `stream`; the equivalent ideal draws from `default`.
    pub fn response_element(
        &self,
        chall_two: &Self,
        chall_split: &AlgElem<P>,
        commit: &Self,
        stream: &mut Domain,
        default: &mut Domain,
        e: usize,
    ) -> Response<P> {
        let sk_chall = chall_two.intersect_o0(self, Some(chall_split));
        let (gen, sk_chall_ideal) =
            sk_chall.small_equivalent_coprime(&Ibz::ZERO, &mut Draw::default_only(default));
        let prod = sk_chall_ideal.mul_o0(commit);
        let mut bound = Ibz::ONE.mul_2exp(e) - Ibz::ONE;
        bound.set_bound(e + 1);
        let (resp_quat, mut norm) = prod
            .sample_from_ball(&bound, stream)
            .expect("the ball holds a nonzero element");
        norm.set_bound(e + 1);
        Response {
            sk_chall_ideal,
            resp_quat,
            norm,
            sk_chall_quat: gen.conj(),
        }
    }
}
