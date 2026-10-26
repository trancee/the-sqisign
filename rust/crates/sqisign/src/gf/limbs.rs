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

// The index list below spells out 0..MAX_LIMBS.
const _: () = assert!(MAX_LIMBS == 11, "unrolled! index list assumes 11 limbs");

/// Limb storage of a field element, `[u64; N]`.
pub trait LimbArray: Copy + AsRef<[u64]> + AsMut<[u64]> {
    /// Montgomery product `a * b / 2^(64 N)` for `p = C * 2^T - 1` with `p + 1`
    /// in the top limb (`T / 64 = N - 1`), given `pp1 = C << (T % 64)`, that limb.
    fn mont_mul(&self, b: &Self, pp1: u64) -> Self;
    /// `self += m & mask` (wrapping).
    fn add_masked(&mut self, m: &Self, mask: u64);
    /// `self -= m & mask` (wrapping), returning the final borrow.
    fn sub_masked(&mut self, m: &Self, mask: u64) -> u64;
}

/// Adds `a * bi` to `z`, returning the word above it.
///
/// The low product halves go to word `j` and the high halves to `j + 1`, in two
/// carry chains (`adcs`), as the vendor arm64 assembly does; a `u128` accumulator
/// costs a flag materialisation per product. Each chain computes its own halves
/// (`mul`, then `umulh`), so no array of high halves stays live across the first
/// chain; with the machine scheduler off (`rust/.cargo/config.toml`) they stay in
/// registers. The sum fits in `N + 1` words, so the second chain ends without a
/// carry.
#[inline(always)]
fn row<const N: usize>(z: &mut [u64; N], a: &[u64; N], bi: u64) -> u64 {
    let mut carry = false;
    for (zj, &aj) in z.iter_mut().zip(a) {
        (*zj, carry) = zj.carrying_add(aj.wrapping_mul(bi), carry);
    }
    let mut top = u64::from(carry);
    carry = false;
    for j in 1..N {
        (z[j], carry) = z[j].carrying_add(mulhi(a[j - 1], bi), carry);
    }
    (top, _) = top.carrying_add(mulhi(a[N - 1], bi), carry);
    top
}

/// The high word of `a * b`.
#[inline(always)]
fn mulhi(a: u64, b: u64) -> u64 {
    ((u128::from(a) * u128::from(b)) >> 64) as u64
}

/// Montgomery word reduction of `(z, top)`: returns `(z + top 2^(64 N) + m p) / 2^64`
/// for `m = z[0]`.
///
/// With `p + 1 = pp1 * 2^(64 (N - 1))`, `z + m p = z - m + m pp1 2^(64 (N - 1))`,
/// and `z - m` clears word 0 without a borrow, so `m pp1` is added to words
/// `N - 1` and `N` before the shift. With four spare bits the sum stays below
/// `2^(64 (N + 1))`.
#[inline(always)]
fn reduce_word<const N: usize>(z: &[u64; N], top: u64, pp1: u64) -> [u64; N] {
    let x = u128::from(z[0]) * u128::from(pp1);
    let (below, carry) = z[N - 1].carrying_add(x as u64, false);
    let (top, _) = top.carrying_add((x >> 64) as u64, carry);
    core::array::from_fn(|j| {
        if j + 2 < N {
            z[j + 1]
        } else if j + 2 == N {
            below
        } else {
            top
        }
    })
}

impl<const N: usize> LimbArray for [u64; N] {
    /// Interleaves the product rows with the word reductions, as the vendor arm64
    /// assembly does: the accumulator stays at `N + 1` words, which fit in
    /// registers, where a `2N`-word product spills.
    #[inline(always)]
    fn mont_mul(&self, b: &Self, pp1: u64) -> Self {
        let mut z = [0u64; N];
        // Rows are unrolled by hand: LLVM keeps the outer loop rolled for 11 limbs.
        unrolled!(i in [0 1 2 3 4 5 6 7 8 9 10] < N => {
            let top = row(&mut z, self, b[i]);
            z = reduce_word(&z, top, pp1)
        });
        z
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

    /// Subtracts as `self + !(m & mask) + 1`, which keeps the carry chain; the first
    /// limb is a plain subtraction, since a carry-in of one costs a flag
    /// materialisation.
    #[inline(always)]
    fn sub_masked(&mut self, m: &Self, mask: u64) -> u64 {
        let borrow;
        (self[0], borrow) = self[0].overflowing_sub(m[0] & mask);
        let mut carry = !borrow;
        for j in 1..N {
            (self[j], carry) = self[j].carrying_add(!(m[j] & mask), carry);
        }
        u64::from(!carry)
    }
}
