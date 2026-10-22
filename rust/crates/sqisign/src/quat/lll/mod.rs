//! Constant-time lattice reductions (vendor `quaternion/ref/lvlx/lll`).

mod config;
mod dual;
mod gaussian;
mod gram;
mod ldl;
mod ldl_update;
mod lehmer;
mod minkowski;
mod mk_select;
#[cfg(test)]
mod probe;
mod word;
mod zi_inner;

use crate::mp::{Ibz, MpParams};

pub use dual::DualReduced;
pub use lehmer::{dim2_short_basis, dim2_sum_of_squares};

/// Bounds of the 2-dimensional values (C `DIM2_VAL_BITS`,
/// `DIM2I_VAL_BITS`).
fn val_bits(bits: usize) -> usize {
    bits + 1
}

/// `x` with the bound `bits` (C `ibz_set_bound_ct` after a copy).
fn bounded<P: MpParams>(mut x: Ibz<P>, bits: usize) -> Ibz<P> {
    x.set_bound(bits);
    x
}

/// `a * b / 2^prec`, rounded down, over `out_bitlen` (C
/// `ibz_ct_highmul_p`).
fn highmul_p<P: MpParams>(a: &Ibz<P>, b: &Ibz<P>, prec: usize, out_bitlen: usize) -> Ibz<P> {
    Ibz::ct_highmul(a, b, -(prec as i32), prec as u32, out_bitlen)
}
