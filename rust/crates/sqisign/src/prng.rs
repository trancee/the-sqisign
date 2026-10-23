//! Domain-separated SHAKE256 PRNG (vendor `common/generic/prng.c`).
//!
//! A [`Prng`] holds the root seed of one operation and its default stream.
//! Each [`Domain`] is the independent stream `SHAKE256(seed || separator)`.
//! C keeps the root and the default stream in thread-locals seeded by
//! `prng_seed()`; here they are owned values passed explicitly, so the
//! unseeded and malformed-separator errors of C cannot occur.

use crate::shake::{Shake256, Shake256Reader};

/// Bytes of the root seed (C `PRNG_SEED_BYTES`).
pub const SEED_BYTES: usize = 48;

/// Domain separators (C `DS_*_DOMAIN`). Changing one breaks the KATs.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Separator {
    /// Call sites without a domain of their own (`"def"`).
    Default,
    /// Signing response (`"res"`).
    Response,
    /// Signing commitment (`"com"`).
    Commitment,
    /// Key generation (`"key"`).
    Keygen,
}

impl Separator {
    /// The three ASCII bytes absorbed after the seed.
    pub const fn bytes(self) -> &'static [u8; 3] {
        match self {
            Self::Default => b"def",
            Self::Response => b"res",
            Self::Commitment => b"com",
            Self::Keygen => b"key",
        }
    }
}

/// One output stream (C `prng_domain_ctx_t`).
#[derive(Clone)]
pub struct Domain(Shake256Reader);

impl Domain {
    /// Fills `out` with the next bytes of the stream (C `prng_random_bytes`).
    pub fn fill(&mut self, out: &mut [u8]) {
        self.0.squeeze(out);
    }
}

/// The root seed and the default stream of one operation (C `prng_root`,
/// `PRNG_default_domain`).
pub struct Prng {
    seed: [u8; SEED_BYTES],
    default: Domain,
}

impl Prng {
    /// Seeds from `seed` (C `prng_seed` with `randombytes` output `seed`).
    pub fn new(seed: &[u8; SEED_BYTES]) -> Self {
        let default = derive(seed, Separator::Default);
        Self {
            seed: *seed,
            default,
        }
    }

    /// A fresh stream for `sep` (C `prng_domain_seed`).
    pub fn domain(&self, sep: Separator) -> Domain {
        derive(&self.seed, sep)
    }

    /// The default stream (C `PRNG_default_domain`).
    pub fn default_domain(&mut self) -> &mut Domain {
        &mut self.default
    }
}

impl Prng {
    /// Clears the seed (C `prng_clear`; best effort, see [`crate::wipe`]). The
    /// streams clear their own state when dropped (C `prng_domain_clear`).
    fn clear(&mut self) {
        crate::wipe::wipe(&mut self.seed);
    }
}

impl Drop for Prng {
    fn drop(&mut self) {
        self.clear();
    }
}

fn derive(seed: &[u8; SEED_BYTES], sep: Separator) -> Domain {
    let mut h = Shake256::new();
    h.absorb(seed);
    h.absorb(sep.bytes());
    Domain(h.finalize())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clear_zeroes_the_seed() {
        let mut p = Prng::new(&[7; SEED_BYTES]);
        p.clear();
        assert_eq!(p.seed, [0; SEED_BYTES]);
    }
}
