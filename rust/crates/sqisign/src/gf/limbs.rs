//! Fixed-size limb kernels for the hot field operations.
//!
//! Implemented for every `[u64; N]`, so loops have a constant trip count and
//! unroll into straight-line code per prime.

use super::consts::MAX_LIMBS;

/// Runs `$body` for each listed `$i` below `$bound`, as straight-line code.
macro_rules! unrolled {
    ($i:ident in [$($n:literal)*] < $bound:expr => $body:expr) => {
        $(
            let $i: usize = $n;
            if $i < $bound {
                $body;
            }
        )*
    };
}

// The index lists below spell out 0..MAX_LIMBS and 0..=MAX_LIMBS.
const _: () = assert!(MAX_LIMBS == 11, "unrolled! index lists assume 11 limbs");

/// Limb storage of a field element, `[u64; N]`.
pub trait LimbArray: Copy + AsRef<[u64]> + AsMut<[u64]> {
    /// Montgomery product `a * b / 2^(64 N)` for `p = C * 2^T - 1`, given
    /// `k = T / 64` and `pp1 = C << (T % 64)`, the single limb of `p + 1`.
    fn mont_mul(&self, b: &Self, k: usize, pp1: u64) -> Self;
    /// `self += m & mask` (wrapping).
    fn add_masked(&mut self, m: &Self, mask: u64);
    /// `self -= m & mask` (wrapping), returning the final borrow.
    fn sub_masked(&mut self, m: &Self, mask: u64) -> u64;
}

/// Adds `a * bi * 2^(64 i)` to `t`, whose words from `i + N` on are zero.
///
/// The low product halves go to word `i + j` and the high halves to `i + j + 1`, in
/// two carry chains (`adcs`), as the vendor arm64 assembly does; a `u128` accumulator
/// costs a flag materialisation per product. The sum fits below `2^(64 (i + N + 1))`,
/// so the second chain ends without a carry.
#[inline(always)]
fn row<const N: usize>(t: &mut [u64; 2 * MAX_LIMBS], a: &[u64; N], bi: u64, i: usize) {
    let mut hi = [0u64; N];
    let mut carry = false;
    for (j, &aj) in a.iter().enumerate() {
        let x = u128::from(aj) * u128::from(bi);
        hi[j] = (x >> 64) as u64;
        (t[i + j], carry) = t[i + j].carrying_add(x as u64, carry);
    }
    t[i + N] = u64::from(carry);
    carry = false;
    for (j, &h) in hi.iter().enumerate() {
        (t[i + j + 1], carry) = t[i + j + 1].carrying_add(h, carry);
    }
}

impl<const N: usize> LimbArray for [u64; N] {
    #[inline(always)]
    fn mont_mul(&self, b: &Self, k: usize, pp1: u64) -> Self {
        let mut t = [0u64; 2 * MAX_LIMBS];
        // Rows are unrolled by hand: LLVM keeps the outer loop rolled for 11 limbs.
        unrolled!(i in [0 1 2 3 4 5 6 7 8 9 10] < N => row(&mut t, self, b[i], i));
        // Montgomery digits m_i = t[i] (as updated): t + m_i p 2^(64 i) = t - m_i 2^(64 i)
        // + m_i (p + 1) 2^(64 i), and p + 1 = pp1 * 2^(64 k). Word i is cleared and not read
        // again, so only the m_i (p + 1) terms are added, in one carry pass from word k.
        // m_i (p + 1) for i < N; the pass reads one entry past N (k = N - 1), which stays zero.
        let mut prod = [0u128; MAX_LIMBS + 1];
        for i in 0..k {
            prod[i] = u128::from(t[i]) * u128::from(pp1);
        }
        let mut carry = 0u128;
        // Word k + o for o in 0..2N - k (N + 1 <= MAX_LIMBS + 1 words, as k = N - 1).
        let mut step = |o: usize| {
            let j = k + o;
            let mut x = u128::from(t[j]) + carry + (prod[o] & u128::from(u64::MAX));
            if o > 0 {
                x += prod[o - 1] >> 64;
            }
            t[j] = x as u64;
            carry = x >> 64;
            if j < N {
                prod[j] = u128::from(t[j]) * u128::from(pp1);
            }
        };
        unrolled!(o in [0 1 2 3 4 5 6 7 8 9 10 11] < 2 * N - k => step(o));
        let mut r = [0u64; N];
        r.copy_from_slice(&t[N..2 * N]);
        r
    }

    // `carrying_add` compiles to one `adcs` (or `adc`) per limb; `u128` sums
    // and `borrowing_sub` leave a flag materialisation per limb.
    #[inline(always)]
    fn add_masked(&mut self, m: &Self, mask: u64) {
        let mut carry = false;
        for (x, y) in self.iter_mut().zip(m) {
            (*x, carry) = x.carrying_add(y & mask, carry);
        }
    }

    /// Subtracts as `self + !(m & mask) + 1`, which keeps the carry chain.
    #[inline(always)]
    fn sub_masked(&mut self, m: &Self, mask: u64) -> u64 {
        let mut carry = true;
        for (x, y) in self.iter_mut().zip(m) {
            (*x, carry) = x.carrying_add(!(y & mask), carry);
        }
        u64::from(!carry)
    }
}
