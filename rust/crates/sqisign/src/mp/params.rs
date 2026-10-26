//! The integer capacity of each parameter set (vendor `encoded_sizes.h`).

use core::ops::{IndexMut, Range, RangeFrom, RangeTo};

use crate::gf::{FpParams, P324, P500, P664};

/// Limbs of the widest [`Ibz`](super::Ibz) (C `IBZ_NLIMBS` of `p664_17`),
/// which sizes the scratch buffers shared by all parameter sets.
pub const MAX_IBZ_LIMBS: usize = 55;

/// The limb array of an [`Ibz`](super::Ibz): `[u64; IBZ_LIMBS]`, indexable
/// like a slice.
pub trait Limbs:
    Copy
    + AsRef<[u64]>
    + AsMut<[u64]>
    + IndexMut<usize, Output = u64>
    + IndexMut<Range<usize>, Output = [u64]>
    + IndexMut<RangeFrom<usize>, Output = [u64]>
    + IndexMut<RangeTo<usize>, Output = [u64]>
{
    /// All zeros, written in place rather than copied from a constant.
    fn zeroed() -> Self;
}

impl<const N: usize> Limbs for [u64; N] {
    fn zeroed() -> Self {
        [0; N]
    }
}

/// Integer capacity of a parameter set (vendor `encoded_sizes.h`).
pub trait MpParams: FpParams {
    /// Limbs of an [`Ibz`](super::Ibz) (C `IBZ_NLIMBS`, `RADIX == 64`).
    const IBZ_LIMBS: usize;
    /// Largest bound (C `IBZ_MAX_BITS`).
    const IBZ_MAX_BITS: usize = 64 * Self::IBZ_LIMBS;
    /// `[u64; IBZ_LIMBS]`, so an [`Ibz`](super::Ibz) carries no unused limbs.
    type IbzLimbs: Limbs;
    /// The limbs of 0, 1, 2 and 3, for the constants [`Ibz::ZERO`](super::Ibz::ZERO) to
    /// [`Ibz::THREE`](super::Ibz::THREE).
    const SMALL_LIMBS: [Self::IbzLimbs; 4];
}

/// `[0, 1, 2, 3]` as `N`-limb integers.
const fn small_limbs<const N: usize>() -> [[u64; N]; 4] {
    let mut s = [[0; N]; 4];
    s[1][0] = 1;
    s[2][0] = 2;
    s[3][0] = 3;
    s
}

macro_rules! mp_params {
    ($p:ty, $limbs:literal) => {
        impl MpParams for $p {
            const IBZ_LIMBS: usize = $limbs;
            type IbzLimbs = [u64; $limbs];
            const SMALL_LIMBS: [Self::IbzLimbs; 4] = small_limbs();
        }
    };
}

mp_params!(P324, 30);
mp_params!(P500, 40);
mp_params!(P664, 55);

#[cfg(test)]
mod tests {
    use super::small_limbs;

    /// `small_limbs` otherwise only runs in const items, at compile time.
    #[test]
    fn small_limbs_are_zero_to_three() {
        assert_eq!(small_limbs::<2>(), [[0, 0], [1, 0], [2, 0], [3, 0]]);
    }
}
