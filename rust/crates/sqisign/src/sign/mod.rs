//! SQIsign key generation and signing (vendor `signature/ref`,
//! `src/sqisign.c`).
//!
//! The operations take the 48-byte seed C's `prng_seed` draws from
//! `randombytes`; with the seeds the NIST DRBG produces they reproduce the
//! KAT files.

mod encode;
mod protocol;

use crate::ec::Curve;
use crate::id2iso::Id2isoParams;
use crate::mp::Ibz;
use crate::prng::SEED_BYTES;
use crate::quat::{Ideal, Mat2};
use crate::verify::{PublicKey, SqisignParams};

/// The signing parameters of a level (vendor `torsion_constants.c`,
/// `encoded_sizes.h`).
pub trait SignParams: Id2isoParams + SqisignParams {
    /// The prime norm of secret key ideals (C `SEC_DEGREE`).
    const SEC_DEGREE: Ibz<Self>;
    /// The prime norm of commitment ideals (C `COM_DEGREE`).
    const COM_DEGREE: Ibz<Self>;
    /// Encoded secret key length (C `SECRETKEY_BYTES`).
    const SECRETKEY_BYTES: usize =
        Self::PUBLICKEY_BYTES + 3 * Self::ENCODED_BYTES + 4 * Self::CHALLENGE_BYTES;
}

/// The vendor degree with bound `bitlen` and the limbs `low`, `zeros` zero
/// limbs, `ones` all-ones limbs and `high`.
macro_rules! degree {
    ($bitlen:literal, $low:literal, $zeros:literal, $ones:literal, $high:literal) => {{
        const LIMBS: [u64; 2 + $zeros + $ones] = {
            let mut l = [0; 2 + $zeros + $ones];
            l[0] = $low;
            let mut i = 0;
            while i < $ones {
                l[1 + $zeros + i] = u64::MAX;
                i += 1;
            }
            l[1 + $zeros + $ones] = $high;
            l
        };
        Ibz::from_raw($bitlen, &LIMBS)
    }};
}

impl SignParams for crate::gf::P324 {
    const SEC_DEGREE: Ibz<Self> = degree!(583, 0x9b, 3, 5, 0x2f);
    const COM_DEGREE: Ibz<Self> = degree!(583, 0x9b, 3, 5, 0x2f);
}

impl SignParams for crate::gf::P500 {
    const SEC_DEGREE: Ibz<Self> = degree!(890, 0x50f, 5, 7, 0x01af_ffff_ffff_ffff);
    const COM_DEGREE: Ibz<Self> = degree!(890, 0x50f, 5, 7, 0x01af_ffff_ffff_ffff);
}

impl SignParams for crate::gf::P664 {
    const SEC_DEGREE: Ibz<Self> = degree!(1182, 0x51, 7, 10, 0x10ff_ffff);
    const COM_DEGREE: Ibz<Self> = degree!(1182, 0x51, 7, 10, 0x10ff_ffff);
}

/// A secret key (C `secret_key_t` without the canonical basis, which
/// signing does not use) with its public key.
pub struct SecretKey<P: SignParams> {
    /// The public key.
    pub pk: PublicKey<P>,
    /// The secret ideal.
    pub secret_ideal: Ideal<P>,
    /// The change of basis from the canonical basis of `E_A[2^f]` to the
    /// image of the basis of `E0[2^f]`, `f = CHALLENGE_BITS`.
    pub mat: Mat2<P>,
    /// The public curve (C `secret_key_t.curve`).
    pub curve: Curve<P>,
}

/// The encoded key pair `(pk, sk)` from the seed `seed` (C
/// `sqisign_keypair`).
pub fn keypair<P: SignParams>(seed: &[u8; SEED_BYTES]) -> (Vec<u8>, Vec<u8>) {
    let sk = SecretKey::<P>::generate(seed);
    (sk.pk.to_bytes(), sk.to_bytes())
}

/// The signature of `msg` under the encoded secret key `sk` from the seed
/// `seed`, or `None` if `sk` has the wrong length or signing fails, which
/// happens with negligible probability (C `sqisign_sign_signature`).
pub fn sign<P: SignParams>(sk: &[u8], msg: &[u8], seed: &[u8; SEED_BYTES]) -> Option<Vec<u8>> {
    SecretKey::<P>::decode(sk)
        .and_then(|sk| sk.sign(msg, seed))
        .map(|sig| sig.to_bytes())
}

/// The signed message `sig || msg` (C `sqisign_sign`).
pub fn sign_attached<P: SignParams>(
    sk: &[u8],
    msg: &[u8],
    seed: &[u8; SEED_BYTES],
) -> Option<Vec<u8>> {
    sign::<P>(sk, msg, seed).map(|mut sm| {
        sm.extend_from_slice(msg);
        sm
    })
}
