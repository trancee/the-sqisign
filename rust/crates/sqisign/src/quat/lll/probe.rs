//! Boundary-heavy word generators for the differential tests of the word
//! steps against the C `static` functions (exported by test shims in
//! `sqisign-reference`).

/// Iterations per word-step property.
pub const RUNS: usize = 100_000;

/// Deterministic SplitMix64 generator.
pub struct Rng(u64);

impl Rng {
    pub fn new(seed: u64) -> Self {
        Self(seed)
    }

    pub fn next(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9e37_79b9_7f4a_7c15);
        let mut z = self.0;
        z = (z ^ (z >> 30)).wrapping_mul(0xbf58_476d_1ce4_e5b9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94d0_49bb_1331_11eb);
        z ^ (z >> 31)
    }

    pub fn below(&mut self, n: u64) -> u64 {
        self.next() % n
    }

    /// A word of uniformly chosen bitsize: random, a small multiple of a
    /// power of two, next to a power of two, or all ones.
    pub fn word(&mut self) -> u64 {
        let bits = self.below(65) as u32;
        if bits == 0 {
            return 0;
        }
        let top = 1u64 << (bits - 1);
        let low = top - 1;
        match self.below(4) {
            0 => top | (self.next() & low),
            1 => (1 + self.below(15)) << self.below(u64::from(bits.min(60))),
            2 => (top | low).wrapping_add(self.below(3)).wrapping_sub(1),
            _ => top | low,
        }
    }

    /// A word related to `x`: equal, shifted either way, off by one, or
    /// fresh.
    pub fn near(&mut self, x: u64) -> u64 {
        let k = self.below(64) as u32;
        match self.below(6) {
            0 => x,
            1 => x >> k,
            2 if x.leading_zeros() >= k => x << k,
            3 => x.wrapping_add(1),
            4 => x.wrapping_sub(1),
            _ => self.word(),
        }
    }

    /// `x` as a signed word of either sign.
    pub fn signed(&mut self, x: u64) -> i64 {
        let s = (x >> 1) as i64;
        if self.below(2) == 0 {
            s
        } else {
            s.wrapping_neg()
        }
    }
}

/// The level the multi-word steps are compared at.
pub type I = crate::mp::Ibz<crate::gf::P324>;
/// Its C counterpart.
pub type CIbz = sqisign_reference::p324_3::mp::Ibz;

/// The C value of `r`, with the same bound.
pub fn to_c(r: &I) -> CIbz {
    let mut x = CIbz {
        bitlen: r.bound() as i32,
        ..CIbz::default()
    };
    x.limbs[..r.limbs().len()].copy_from_slice(r.limbs());
    x
}

/// Asserts Rust and C agree on the bound and the significant limbs.
pub fn same(r: &I, x: &CIbz) {
    assert_eq!(r.bound(), x.bitlen as usize, "bound of {r:?}");
    assert_eq!(r.limbs(), &x.limbs[..r.limbs().len()], "limbs of {r:?}");
}

impl Rng {
    /// A non-negative value of exactly `bits` bits, of bound `bound`.
    pub fn value(&mut self, bits: usize, bound: usize) -> I {
        let mut r = if bits == 0 {
            I::ZERO
        } else {
            let mut w: Vec<u64> = (0..bits.div_ceil(64)).map(|_| self.next()).collect();
            let (word, bit) = ((bits - 1) / 64, (bits - 1) % 64);
            w[word] = w[word] & (u64::MAX >> (63 - bit)) | 1 << bit;
            I::from_bits(&w, bits)
        };
        r.set_bound(bound);
        r
    }

    /// `d` plus a small offset in `-3..=3`, at least 1.
    pub fn around(&mut self, d: i64) -> usize {
        (d + self.below(7) as i64 - 3).max(1) as usize
    }
}
