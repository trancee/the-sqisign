//! Finite fields GF(p) and GF(p^2) for the SQIsign primes.

mod consts;
mod fp;
mod fp2;
mod fp2_pow;
mod fp_codec;
mod fp_pow;
mod limbs;
mod params;

pub use fp::Fp;
pub use fp2::Fp2;
pub use params::{FpParams, P324, P500, P664};

#[cfg(test)]
mod fp_tests;
