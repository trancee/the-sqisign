//! Uniform sampling (vendor `mp.c`: `ibz_rand_interval*`).

use super::{nlimbs, Ibz, MpParams, MAX_IBZ_LIMBS};
use crate::prng::Domain;

impl<P: MpParams> Ibz<P> {
    /// A uniform value in `[a, b]`, `a <= b`, bounded by the bound of `b`,
    /// which must therefore also hold `a`, and must leave 64 bits of
    /// capacity (C `ibz_rand_interval_with_domain`). Draws `b.bound() + 64`
    /// bits per attempt and reduces them modulo `b - a + 1`.
    pub fn rand_interval(a: &Self, b: &Self, domain: &mut Domain) -> Self {
        let bma = *b - *a + Self::ONE;
        let numbits = b.bitlen + 64;
        let mut buf = [0u8; 8 * MAX_IBZ_LIMBS];
        let bytes = &mut buf[..numbits.div_ceil(8)];
        // Draws until a candidate is accepted; the stream never ends.
        let t = core::iter::repeat_with(|| {
            domain.fill(bytes);
            Self::candidate(bytes, numbits, &bma)
        })
        .flatten()
        .next()
        .unwrap();
        let mut r = *a + t.modulo(&bma);
        r.set_bound(b.bitlen);
        r
    }

    /// The little-endian `bytes`, truncated to `numbits` bits, plus `bma`; or
    /// `None` when the sum needs more than `numbits` bits, in which case C
    /// (under `NDEBUG`) draws again.
    fn candidate(bytes: &[u8], numbits: usize, bma: &Self) -> Option<Self> {
        let mut t = Self::zero(numbits + 1);
        for (limb, chunk) in t.limbs.as_mut().iter_mut().zip(bytes.chunks(8)) {
            let mut w = [0; 8];
            w[..chunk.len()].copy_from_slice(chunk);
            *limb = u64::from_le_bytes(w);
        }
        let top = nlimbs(numbits + 1) - 1;
        t.limbs[top] &= (1 << (numbits - 64 * top)) - 1;
        let t = t + *bma;
        (t.bitsize() <= numbits).then_some(t)
    }

    /// A uniform value in `[-m, m]`, `m > 0`, bounded by 32 bits (C
    /// `ibz_rand_interval_minm_m_with_domain`).
    pub fn rand_interval_minm_m(m: i32, domain: &mut Domain) -> Self {
        let mm = Self::from_i64(m.into(), 32);
        let mut r = Self::rand_interval(&Self::ZERO, &(mm + mm), domain) - mm;
        r.set_bound(32);
        r
    }
}

#[cfg(test)]
mod tests {
    use crate::gf::P324;
    use crate::mp::Ibz;

    type Z = Ibz<P324>;

    #[test]
    fn candidate_rejects_sums_beyond_the_drawn_width() {
        let bma = Z::from_i64(2, 32);
        assert_eq!(Z::candidate(&[0xff; 12], 96, &bma), None);
        assert_eq!(
            Z::candidate(&[0xfd; 12], 96, &bma).map(|t| t.bitsize()),
            Some(96)
        );
    }
}
