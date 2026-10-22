//! Per-level bounds of the dimension-4 reduction (vendor `lll_config.h`).

use crate::quat::QuatParams;

/// The fixed-point bounds, derived from the security level and `p`; C
/// `LIMB_ALIGN` is `next_multiple_of(64)`.
pub trait LllConfig: QuatParams {
    /// C `FP_LAMBDA`.
    const LAMBDA: usize = Self::SECURITY_BITS;
    /// C `FP_NORM`.
    const NORM: usize = Self::P_BITS + Self::LAMBDA;
    /// C `FP_GAP_PUB`.
    const GAP: usize = 2 * Self::NORM - Self::P_BITS;
    /// Transform entries (C `FP_U_BITS`, `FP_HEAD`, `BLOCK_U_BITS`).
    const U: usize = (Self::GAP / 4 + Self::LAMBDA / 2).next_multiple_of(64);
    /// The fixed-point precision (C `FP_P_BITS`).
    const PREC: usize = (2 * Self::U + Self::LAMBDA).next_multiple_of(64);
    /// `L` entries (C `FP_L_BITS`).
    const L: usize = (Self::PREC + Self::U).next_multiple_of(64);
    /// Right shift of the `D` entries (C `FP_D_SHIFT`).
    const D_SHIFT: usize = Self::P_BITS - Self::LAMBDA - Self::LAMBDA / 2;
    /// `D` entries (C `FP_E_BITS`).
    const E: usize = (2 * Self::NORM).next_multiple_of(64) - Self::D_SHIFT;
    /// Block Gram entries (C `GRAM_TOT_BITS`).
    const GRAM_TOT: usize = (Self::GAP + Self::LAMBDA).next_multiple_of(64);
    /// The block Gram working bound (C `GRAM_WORK_BITS`).
    const GRAM_WORK: usize = Self::GRAM_TOT + 32;
    /// C `LG_OUTER_ITS`.
    const LG_OUTER_ITS: usize = Self::U / 8;
    /// C `CT_LLL_TOURS`.
    const TOURS: usize = Self::GAP.ilog2() as usize + 4;
    /// The HNF entries (C `QUAT_HNF_BITS`).
    const HNF: usize = Self::NORM + 2;
    /// The returned transform (C `QUAT_AINV_BITS`, `QUAT_AINVOUT_BITS`).
    const AINV: usize = Self::U + 32;
    /// The returned basis (C `QUAT_BASIS_BITS`).
    const BASIS: usize = (Self::HNF + Self::AINV + 2).next_multiple_of(64);
    /// Gram norms (C `QUAT_GRAM_BITS`).
    const GRAM: usize = Self::E + 40;
}

impl<T: QuatParams> LllConfig for T {}

/// Newton rounds of the reciprocals (C `FP_NR_ROUNDS`).
pub const NR_ROUNDS: usize = 5;

/// Lagrange-Gauss outer passes of tour step `round` (C
/// `lg_outer_its_for_round`).
pub fn lg_outer_its<C: LllConfig>(round: usize) -> usize {
    let shift = round.saturating_sub(1) / 3;
    (C::LG_OUTER_ITS >> shift.min(24)).max(4)
}
