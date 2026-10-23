//! Byte encoding of secret keys (vendor `encode_secret.c`).

use super::{SecretKey, SignParams};
use crate::mp::Ibz;
use crate::verify::PublicKey;
use crate::wipe::wipe;

/// The non-negative `x` as `n` little-endian bytes (C `sig_ibz_to_bytes`).
///
/// # Panics
///
/// If `x` does not fit `n` bytes rounded up to whole words (C overflows a
/// stack buffer; its keys always fit).
fn ibz_to_bytes<P: SignParams>(out: &mut Vec<u8>, x: &Ibz<P>, n: usize) {
    let mut d = vec![0u64; n.div_ceil(8)];
    x.to_digits(&mut d);
    out.extend(d.iter().flat_map(|w| w.to_le_bytes()).take(n));
    wipe(&mut d);
}

/// The little-endian bytes `src` as a non-negative integer (C
/// `sig_ibz_from_bytes`).
fn ibz_from_bytes<P: SignParams>(src: &[u8]) -> Ibz<P> {
    let mut d = vec![0u64; src.len().div_ceil(8)];
    for (i, &b) in src.iter().enumerate() {
        d[i / 8] |= u64::from(b) << (8 * (i % 8));
    }
    let x = Ibz::from_digits(&d, d.len());
    wipe(&mut d);
    x
}

impl<P: SignParams> SecretKey<P> {
    /// The encoding `pk || N(I) || x || y || M` with `M` row by row (C
    /// `secret_key_to_bytes`). The output is allocated at its final size,
    /// so no partial copy is left in a freed buffer.
    pub fn to_bytes(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(P::SECRETKEY_BYTES);
        out.extend(self.pk.to_bytes());
        let i = &self.secret_ideal;
        for x in [i.norm, i.x, i.y] {
            ibz_to_bytes(&mut out, &x, P::ENCODED_BYTES);
        }
        for x in self.mat.iter().flatten() {
            ibz_to_bytes(&mut out, x, P::CHALLENGE_BYTES);
        }
        out
    }

    /// Decodes a secret key; `None` if `src` has the wrong length (C
    /// `secret_key_from_bytes`).
    pub fn decode(src: &[u8]) -> Option<Self> {
        if src.len() != P::SECRETKEY_BYTES {
            return None;
        }
        let (pk, rest) = src.split_at(P::PUBLICKEY_BYTES);
        let pk = PublicKey::<P>::decode(pk).expect("the public key length");
        let (ideal, mat) = rest.split_at(3 * P::ENCODED_BYTES);
        let mut ideal: Vec<Ibz<P>> = ideal.chunks(P::ENCODED_BYTES).map(ibz_from_bytes).collect();
        let mut m: Vec<Ibz<P>> = mat.chunks(P::CHALLENGE_BYTES).map(ibz_from_bytes).collect();
        let sk = Self {
            curve: pk.curve,
            pk,
            secret_ideal: crate::quat::Ideal {
                norm: ideal[0],
                x: ideal[1],
                y: ideal[2],
            },
            mat: [[m[0], m[1]], [m[2], m[3]]],
        };
        wipe(&mut ideal);
        wipe(&mut m);
        Some(sk)
    }
}
