//! SHAKE256 (FIPS 202), the extendable-output function behind the SQIsign
//! challenge hash (vendor `common/generic/fips202.c`).

/// Rate of SHAKE256 in bytes.
const RATE: usize = 136;

const ROUND_CONSTANTS: [u64; 24] = [
    0x0000_0000_0000_0001,
    0x0000_0000_0000_8082,
    0x8000_0000_0000_808a,
    0x8000_0000_8000_8000,
    0x0000_0000_0000_808b,
    0x0000_0000_8000_0001,
    0x8000_0000_8000_8081,
    0x8000_0000_0000_8009,
    0x0000_0000_0000_008a,
    0x0000_0000_0000_0088,
    0x0000_0000_8000_8009,
    0x0000_0000_8000_000a,
    0x0000_0000_8000_808b,
    0x8000_0000_0000_008b,
    0x8000_0000_0000_8089,
    0x8000_0000_0000_8003,
    0x8000_0000_0000_8002,
    0x8000_0000_0000_0080,
    0x0000_0000_0000_800a,
    0x8000_0000_8000_000a,
    0x8000_0000_8000_8081,
    0x8000_0000_0000_8080,
    0x0000_0000_8000_0001,
    0x8000_0000_8000_8008,
];

/// Rotation offsets of the rho step, in pi-step order.
const RHO: [u32; 24] = [
    1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44,
];

/// Lane order of the combined rho and pi steps.
const PI: [usize; 24] = [
    10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1,
];

/// The Keccak-f\[1600\] permutation.
fn keccak_f(a: &mut [u64; 25]) {
    for rc in ROUND_CONSTANTS {
        let c: [u64; 5] =
            core::array::from_fn(|x| a[x] ^ a[x + 5] ^ a[x + 10] ^ a[x + 15] ^ a[x + 20]);
        for x in 0..5 {
            let d = c[(x + 4) % 5] ^ c[(x + 1) % 5].rotate_left(1);
            for y in 0..5 {
                a[x + 5 * y] ^= d;
            }
        }
        let mut last = a[1];
        for (&j, &r) in PI.iter().zip(&RHO) {
            let t = a[j];
            a[j] = last.rotate_left(r);
            last = t;
        }
        for y in 0..5 {
            let row: [u64; 5] = core::array::from_fn(|x| a[5 * y + x]);
            for x in 0..5 {
                a[5 * y + x] = row[x] ^ (!row[(x + 1) % 5] & row[(x + 2) % 5]);
            }
        }
        a[0] ^= rc;
    }
}

/// The sponge state and the byte position within the current block.
#[derive(Clone)]
struct Sponge {
    s: [u64; 25],
    pos: usize,
}

impl Sponge {
    fn xor_byte(&mut self, i: usize, b: u8) {
        self.s[i / 8] ^= u64::from(b) << (8 * (i % 8));
    }

    fn byte(&self, i: usize) -> u8 {
        self.s[i / 8].to_le_bytes()[i % 8]
    }
}

/// A SHAKE256 instance in its absorbing phase (C `shake256_inc_*`).
#[derive(Clone)]
pub struct Shake256(Sponge);

/// A SHAKE256 instance in its squeezing phase.
#[derive(Clone)]
pub struct Shake256Reader(Sponge);

impl Default for Shake256 {
    fn default() -> Self {
        Self(Sponge { s: [0; 25], pos: 0 })
    }
}

impl Shake256 {
    /// An empty instance (C `shake256_inc_init`).
    pub fn new() -> Self {
        Self::default()
    }

    /// Absorbs `data` (C `shake256_inc_absorb`).
    pub fn absorb(&mut self, data: &[u8]) {
        let st = &mut self.0;
        for &b in data {
            st.xor_byte(st.pos, b);
            st.pos += 1;
            if st.pos == RATE {
                keccak_f(&mut st.s);
                st.pos = 0;
            }
        }
    }

    /// Pads the input and switches to squeezing (C `shake256_inc_finalize`).
    pub fn finalize(self) -> Shake256Reader {
        let mut st = self.0;
        st.xor_byte(st.pos, 0x1f);
        st.xor_byte(RATE - 1, 0x80);
        st.pos = RATE;
        Shake256Reader(st)
    }
}

impl Shake256Reader {
    /// Fills `out` with the next output bytes (C `shake256_inc_squeeze`).
    pub fn squeeze(&mut self, out: &mut [u8]) {
        let st = &mut self.0;
        for o in out {
            if st.pos == RATE {
                keccak_f(&mut st.s);
                st.pos = 0;
            }
            *o = st.byte(st.pos);
            st.pos += 1;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::Shake256;

    fn shake(input: &[u8], n: usize) -> Vec<u8> {
        let mut h = Shake256::new();
        h.absorb(input);
        let mut out = vec![0; n];
        h.finalize().squeeze(&mut out);
        out
    }

    fn hex(b: &[u8]) -> String {
        b.iter().map(|x| format!("{x:02x}")).collect()
    }

    /// SHAKE256 values from an independent implementation (Python
    /// `hashlib`, backed by OpenSSL).
    #[test]
    fn known_answers() {
        assert_eq!(
            hex(&shake(b"", 32)),
            "46b9dd2b0ba88d13233b3feb743eeb243fcd52ea62b81b82b50c27646ed5762f"
        );
        assert_eq!(
            hex(&shake(b"abc", 32)),
            "483366601360a8771c6863080cc4114d8db44530f8f1e1ee4f94ea37e78b5739"
        );
        // 200 bytes of 0xa3 span two input blocks; 512 output bytes span four.
        let out = shake(&[0xa3; 200], 512);
        assert_eq!(
            hex(&out[480..]),
            "6a1a9d7846436e4dca5728b6f760eef0ca92bf0be5615e96959d767197a0beeb"
        );
    }
}
