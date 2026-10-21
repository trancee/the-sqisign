//! SQIsign verification (vendor `verification/ref`, `src/sqisign.c`).

mod encode;
mod protocol;

use crate::ec::{Curve, EcParams};
use crate::gf::{Fp2, P324, P500, P664};

/// Extra torsion bits of the 2-dimensional isogeny kernel (C `HD_EXTRA_TORSION`).
pub const HD_EXTRA_TORSION: usize = 2;
/// Extra torsion bits of the challenge isogeny kernel (C `EC_EXTRA_TORSION`).
pub const EC_EXTRA_TORSION: usize = 2;
/// Words of the largest scalar modulo the curve order (`NWORDS_ORDER` of `p664_17`).
pub const MAX_ORDER_WORDS: usize = 11;

/// A little-endian scalar of `ORDER_WORDS` significant words (C `scalar_t`).
pub type Scalar = [u64; MAX_ORDER_WORDS];

/// Protocol sizes of a parameter set (vendor `precomp/ref/<level>/include/encoded_sizes.h`).
pub trait SqisignParams: EcParams {
    /// Bits of the challenge (C `CHALLENGE_BITS`).
    const CHALLENGE_BITS: usize;
    /// Length of the response isogeny (C `RESPONSE_BITS`).
    const RESPONSE_BITS: usize;
    /// Encoded challenge length (C `CHALLENGE_BYTES`).
    const CHALLENGE_BYTES: usize = Self::CHALLENGE_BITS.div_ceil(8);
    /// Encoded length of one basis change matrix entry (C `RESPONSE_BYTES`).
    const RESPONSE_BYTES: usize = (Self::RESPONSE_BITS + HD_EXTRA_TORSION).div_ceil(8);
    /// Encoded public key length (C `PUBLICKEY_BYTES`).
    const PUBLICKEY_BYTES: usize = 2 * Self::ENCODED_BYTES + 1;
    /// Encoded signature length (C `SIGNATURE_BYTES`).
    const SIGNATURE_BYTES: usize =
        2 * Self::ENCODED_BYTES + 4 * Self::RESPONSE_BYTES + Self::CHALLENGE_BYTES + 2;
}

impl SqisignParams for P324 {
    const CHALLENGE_BITS: usize = 128;
    const RESPONSE_BITS: usize = 196;
}

impl SqisignParams for P500 {
    const CHALLENGE_BITS: usize = 192;
    const RESPONSE_BITS: usize = 302;
}

impl SqisignParams for P664 {
    const CHALLENGE_BITS: usize = 256;
    const RESPONSE_BITS: usize = 400;
}

/// A public key: the curve `E_A` and its basis hint (C `public_key_t`).
pub struct PublicKey<P: SqisignParams> {
    /// The curve `(A : C)`; decoded keys have `C = 1`.
    pub curve: Curve<P>,
    /// The hint of the canonical basis of `E_A[2^f]`.
    pub hint: u8,
}

/// A signature (C `signature_t`).
pub struct Signature<P: SqisignParams> {
    /// The Montgomery coefficient of the auxiliary curve.
    pub aux_a: Fp2<P>,
    /// The basis change matrix from the canonical basis of `E_chall`.
    pub mat: [[Scalar; 2]; 2],
    /// The challenge.
    pub chall: Scalar,
    /// The hint of the canonical basis of `E_aux`.
    pub hint_aux: u8,
    /// The hint of the canonical basis of `E_chall`.
    pub hint_chall: u8,
}

/// Whether `sig` is a valid signature of `msg` under the encoded public key
/// `pk` (C `sqisign_verify` returning 0). Inputs of the wrong length are
/// rejected.
pub fn verify<P: SqisignParams>(sig: &[u8], msg: &[u8], pk: &[u8]) -> bool {
    match (PublicKey::<P>::decode(pk), Signature::<P>::decode(sig)) {
        (Some(pk), Some(sig)) => pk.verify(&sig, msg),
        _ => false,
    }
}

/// The message of the signed message `sm = sig || msg` if its signature is
/// valid under `pk` (C `sqisign_open`).
pub fn open<'a, P: SqisignParams>(sm: &'a [u8], pk: &[u8]) -> Option<&'a [u8]> {
    let (sig, msg) = sm.split_at_checked(P::SIGNATURE_BYTES)?;
    verify::<P>(sig, msg, pk).then_some(msg)
}

#[cfg(test)]
mod tests {
    use super::SqisignParams;
    use crate::gf::{P324, P500, P664};

    fn sizes<P: SqisignParams>() -> [usize; 4] {
        [
            P::CHALLENGE_BYTES,
            P::RESPONSE_BYTES,
            P::PUBLICKEY_BYTES,
            P::SIGNATURE_BYTES,
        ]
    }

    /// The vendor `encoded_sizes.h` values.
    #[test]
    fn sizes_match_vendor() {
        assert_eq!(sizes::<P324>(), [16, 25, 83, 200]);
        assert_eq!(sizes::<P500>(), [24, 38, 129, 306]);
        assert_eq!(sizes::<P664>(), [32, 51, 169, 406]);
    }

    /// The challenge isogeny fits the 2-power torsion (C `_Static_assert`).
    #[test]
    fn challenge_fits_torsion() {
        fn fits<P: SqisignParams>() -> bool {
            P::CHALLENGE_BITS + super::EC_EXTRA_TORSION <= P::T as usize
        }
        assert!(fits::<P324>() && fits::<P500>() && fits::<P664>());
    }
}
