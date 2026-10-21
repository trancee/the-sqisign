//! Prime-field parameter sets `p = C * 2^T - 1` used by SQIsign.

use super::consts;

/// Shape and Montgomery constants of one prime field.
///
/// Elements use `N` saturated 64-bit limbs in Montgomery form with
/// `R = 2^(64 N)`, partially reduced to `[0, 2^BITS)`.
pub trait FpParams: 'static {
    /// Limb storage, `[u64; N]`.
    type Limbs: Copy + AsRef<[u64]> + AsMut<[u64]>;
    /// Number of 64-bit limbs.
    const N: usize;
    /// Cofactor `C` of `p + 1 = C * 2^T`.
    const C: u64;
    /// Two-adic valuation `T` of `p + 1`.
    const T: u32;
    /// Bit length of `p`.
    const BITS: u32 = consts::bits(Self::C, Self::T);
    /// Length of the canonical little-endian encoding.
    const ENCODED_BYTES: usize = (Self::BITS as usize).div_ceil(8);
    /// All-zero limbs.
    const ZERO: Self::Limbs;
    /// The modulus `p`.
    const P: Self::Limbs;
    /// `R mod p` (one, in Montgomery form).
    const ONE: Self::Limbs;
    /// `R^2 mod p`.
    const R2: Self::Limbs;
    /// `1/2` in Montgomery form.
    const TWO_INV: Self::Limbs;
    /// `1/3` in Montgomery form.
    const THREE_INV: Self::Limbs;
}

macro_rules! prime {
    ($(#[$doc:meta])* $name:ident, n = $n:literal, c = $c:literal, t = $t:literal) => {
        $(#[$doc])*
        #[derive(Debug)]
        pub enum $name {}

        const _: () = consts::check_shape::<$n>($c, $t);

        impl FpParams for $name {
            type Limbs = [u64; $n];
            const N: usize = $n;
            const C: u64 = $c;
            const T: u32 = $t;
            const ZERO: [u64; $n] = [0; $n];
            const P: [u64; $n] = consts::modulus($c, $t);
            const ONE: [u64; $n] = consts::pow2_mod(&Self::P, 64 * $n);
            const R2: [u64; $n] = consts::pow2_mod(&Self::P, 128 * $n);
            const TWO_INV: [u64; $n] = consts::half_mod(&Self::ONE, &Self::P);
            const THREE_INV: [u64; $n] = consts::third_mod(&Self::ONE, &Self::P);
        }
    };
}

prime!(
    /// NIST level I: `p = 3 * 2^324 - 1` (`p324_3`).
    P324, n = 6, c = 3, t = 324
);
prime!(
    /// NIST level III: `p = 27 * 2^500 - 1` (`p500_27`).
    P500, n = 8, c = 27, t = 500
);
prime!(
    /// NIST level V: `p = 17 * 2^664 - 1` (`p664_17`).
    P664, n = 11, c = 17, t = 664
);

#[cfg(test)]
#[path = "params_tests.rs"]
mod tests;
