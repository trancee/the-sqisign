//! Canonical byte encoding of GF(p) elements.

use super::consts::MAX_LIMBS;
use super::fp::{mask64, sub_masked, Fp};
use super::params::FpParams;

impl<P: FpParams> Fp<P> {
    /// The canonical value in `[0, p)`, out of Montgomery form.
    fn canonical(&self) -> [u64; MAX_LIMBS] {
        let mut raw = [0u64; MAX_LIMBS];
        raw[0] = 1;
        let t = *self * Self::from_raw(&raw);
        let mut s = [0u64; MAX_LIMBS];
        s[..P::N].copy_from_slice(t.limbs());
        let borrow = sub_masked(&mut s[..P::N], P::P.as_ref(), u64::MAX);
        let keep = 0u64.wrapping_sub(borrow);
        let mut m = [0u64; MAX_LIMBS];
        for ((mi, &ti), &si) in m.iter_mut().zip(t.limbs()).zip(&s) {
            *mi = (ti & keep) | (si & !keep);
        }
        m
    }

    /// Writes the canonical little-endian encoding into `dst`.
    ///
    /// # Panics
    /// If `dst.len() != ENCODED_BYTES`.
    pub fn encode(&self, dst: &mut [u8]) {
        assert_eq!(dst.len(), P::ENCODED_BYTES, "encoded length");
        let m = self.canonical();
        for (i, byte) in dst.iter_mut().enumerate() {
            *byte = (m[i / 8] >> (8 * (i % 8))) as u8;
        }
    }

    /// Decodes a little-endian encoding.
    ///
    /// Returns the element and `0xFFFF_FFFF` if the input is canonical
    /// (`< p`); otherwise zero and `0`.
    ///
    /// # Panics
    /// If `src.len() != ENCODED_BYTES`.
    pub fn decode(src: &[u8]) -> (Self, u32) {
        assert_eq!(src.len(), P::ENCODED_BYTES, "encoded length");
        let mut v = [0u64; MAX_LIMBS];
        for (i, &byte) in src.iter().enumerate() {
            v[i / 8] |= u64::from(byte) << (8 * (i % 8));
        }
        let mut s = v;
        let ok = 0u32.wrapping_sub(sub_masked(&mut s[..P::N], P::P.as_ref(), u64::MAX) as u32);
        let mut d = Self::from_raw(&v) * Self { v: P::R2 };
        for x in d.v.as_mut() {
            *x &= mask64(ok);
        }
        (d, ok)
    }

    /// Reduces an arbitrary-length little-endian byte string modulo p.
    pub fn decode_reduce(src: &[u8]) -> Self {
        let block = 8 * P::N;
        let last = src.len().div_ceil(block).wrapping_sub(1);
        let mut acc = Self::ZERO;
        for (index, chunk) in src.chunks(block).enumerate().rev() {
            let mut w = [0u64; MAX_LIMBS + 1];
            for (i, &byte) in chunk.iter().enumerate() {
                w[i / 8] |= u64::from(byte) << (8 * (i % 8));
            }
            let reduced = Self::partial_reduce(&w[..=P::N]);
            acc = if index == last {
                reduced
            } else {
                acc * Self { v: P::R2 } + reduced
            };
        }
        acc * Self { v: P::R2 }
    }
}
