//! Left ideals of the standard maximal order `O0` in inert HNF (vendor
//! `ideal.c`, `lll_applications.c`).

mod create;
mod equiv;
mod product;
mod random;
mod response;

pub use response::Response;

use super::{AlgElem, Lattice, QuatParams};
use crate::mp::{Ibz, MpParams};

/// The ideal of `O0` with the inert HNF `(x, y)` and norm `norm` (C
/// `quat_ideal_t`).
#[derive(Clone, Copy, Debug)]
pub struct Ideal<P: MpParams> {
    /// The inert HNF `x`.
    pub x: Ibz<P>,
    /// The inert HNF `y`.
    pub y: Ibz<P>,
    /// The norm.
    pub norm: Ibz<P>,
}

impl<P: MpParams> Default for Ideal<P> {
    /// The zero ideal (C `quat_ideal_init`).
    fn default() -> Self {
        Self {
            x: Ibz::ZERO,
            y: Ibz::ZERO,
            norm: Ibz::ZERO,
        }
    }
}

impl<P: QuatParams> Ideal<P> {
    /// The ideal `(x, y, n)`, `x` and `y` bounded like `n` (C
    /// `quat_xyn_to_inert_ideal`).
    pub fn from_xyn(x: &Ibz<P>, y: &Ibz<P>, n: &Ibz<P>) -> Self {
        let (mut x, mut y) = (*x, *y);
        x.set_bound(n.bound());
        y.set_bound(n.bound());
        Self { x, y, norm: *n }
    }

    /// The lattice of `self` (C `quat_to_lattice`).
    pub fn to_lattice(&self) -> Lattice<P> {
        let mut l = Lattice::o0();
        let nb = self.norm.bound() + 1;
        let with_bound = |mut x: Ibz<P>| {
            x.set_bound(nb);
            x
        };
        let b = &mut l.basis;
        b[0][0] = with_bound(self.norm.mul_2exp(1));
        b[1][1] = b[0][0];
        b[2][2] = Ibz::ONE;
        b[0][2] = with_bound(self.x.mul_2exp(1));
        b[1][2] = with_bound(self.y.mul_2exp(1)) + Ibz::ONE;
        b[1][3] = b[0][2];
        b[0][3] = with_bound(b[0][0] - b[1][2]);
        l
    }

    /// The generator `(2x + (2y + 1) i + j) / 2` of an odd inert ideal (C
    /// `quat_ideal_odd_inert_gen`).
    pub fn odd_inert_gen(&self) -> AlgElem<P> {
        let mut g = AlgElem::from_i32(2, [0, 0, 1, 0]);
        g.coord[0] = self.x + self.x;
        g.coord[1] = self.y + self.y + Ibz::ONE;
        g
    }
}
