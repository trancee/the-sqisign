//! Byte encodings of public keys and signatures (vendor `encode_public.c`).
//!
//! As in C, a field element that is not canonical decodes with that half
//! set to zero rather than being rejected.

use super::{PublicKey, Scalar, Signature, SqisignParams};
use crate::ec::Curve;
use crate::gf::Fp2;

/// `x` as `n` little-endian bytes (C `encode_digits`).
fn encode_digits(out: &mut Vec<u8>, x: &Scalar, n: usize) {
    out.extend(x.iter().flat_map(|w| w.to_le_bytes()).take(n));
}

/// The little-endian bytes `src`, zero-extended (C `decode_digits`).
fn decode_digits(src: &[u8]) -> Scalar {
    let mut x = Scalar::default();
    for (i, &b) in src.iter().enumerate() {
        x[i / 8] |= u64::from(b) << (8 * (i % 8));
    }
    x
}

fn encode_fp2<P: SqisignParams>(out: &mut Vec<u8>, x: &Fp2<P>) {
    let start = out.len();
    out.resize(start + Fp2::<P>::ENCODED_BYTES, 0);
    x.encode(&mut out[start..]);
}

impl<P: SqisignParams> PublicKey<P> {
    /// The encoding `A/C || hint` (C `public_key_to_bytes`).
    ///
    /// # Panics
    /// If `C = 0`.
    pub fn to_bytes(&self) -> Vec<u8> {
        assert!(self.curve.c.is_zero() == 0, "C = 0");
        let mut out = Vec::with_capacity(P::PUBLICKEY_BYTES);
        encode_fp2(&mut out, &(self.curve.a * self.curve.c.inv()));
        out.push(self.hint);
        out
    }

    /// Decodes `A || hint` to the curve `(A : 1)`; `None` if `src` has the
    /// wrong length (C `public_key_from_bytes`).
    pub fn decode(src: &[u8]) -> Option<Self> {
        if src.len() != P::PUBLICKEY_BYTES {
            return None;
        }
        let (a, hint) = src.split_at(Fp2::<P>::ENCODED_BYTES);
        Some(Self {
            curve: Curve {
                a: Fp2::decode(a).0,
                ..Curve::default()
            },
            hint: hint[0],
        })
    }
}

impl<P: SqisignParams> Signature<P> {
    /// The encoding `A_aux || M || chall || hint_aux || hint_chall` (C
    /// `signature_to_bytes`); `M` row by row.
    pub fn to_bytes(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(P::SIGNATURE_BYTES);
        encode_fp2(&mut out, &self.aux_a);
        for x in self.mat.iter().flatten() {
            encode_digits(&mut out, x, P::RESPONSE_BYTES);
        }
        encode_digits(&mut out, &self.chall, P::CHALLENGE_BYTES);
        out.extend([self.hint_aux, self.hint_chall]);
        out
    }

    /// Decodes a signature; `None` if `src` has the wrong length (C
    /// `signature_from_bytes`).
    pub fn decode(src: &[u8]) -> Option<Self> {
        if src.len() != P::SIGNATURE_BYTES {
            return None;
        }
        let (a, rest) = src.split_at(Fp2::<P>::ENCODED_BYTES);
        let (m, rest) = rest.split_at(4 * P::RESPONSE_BYTES);
        let (chall, hints) = rest.split_at(P::CHALLENGE_BYTES);
        let r = P::RESPONSE_BYTES;
        let e: [Scalar; 4] = core::array::from_fn(|i| decode_digits(&m[i * r..(i + 1) * r]));
        Some(Self {
            aux_a: Fp2::decode(a).0,
            mat: [[e[0], e[1]], [e[2], e[3]]],
            chall: decode_digits(chall),
            hint_aux: hints[0],
            hint_chall: hints[1],
        })
    }
}
