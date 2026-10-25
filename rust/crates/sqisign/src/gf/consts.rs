//! Compile-time derivation of the field constants for p = C * 2^T - 1.
//!
//! Every helper is a `const fn` over little-endian 64-bit limbs, so the
//! Montgomery constants are computed by the compiler from `(N, C, T)` alone and
//! unit-tested against the vendor `fp_generic_params.h` values.

/// Largest supported limb count (p664_17 uses 11).
pub const MAX_LIMBS: usize = 11;

/// Rejects shapes that break the arithmetic invariants of [`super::Fp`].
///
/// Requires: `N <= MAX_LIMBS`, `C` odd and `> 1`, `T % 64 != 0`, `C << (T % 64)`
/// fits one limb (so `p + 1` has a single nonzero limb), at least four spare
/// bits above `p` in `N` limbs, and that limb is the top one (`T / 64 == N - 1`).
///
/// [`super::Fp::mul_sums`] multiplies unreduced sums below `2^(BITS + 1)`; the
/// product stays in `[0, 2^BITS)` if `2^(2 BITS + 2 - 64 N) <= 2^T`, as
/// `2^BITS - p > 2^T`, so the spare bits must also cover `bitlen(C) + 2`.
pub const fn check_shape<const N: usize>(c: u64, t: u32) {
    assert!(N <= MAX_LIMBS, "too many limbs");
    assert!(c > 1 && c % 2 == 1, "C must be odd and greater than one");
    assert!(
        !t.is_multiple_of(64) && t >= 64,
        "T must be at least 64 and not a multiple of 64"
    );
    let s = t % 64;
    assert!((c << s) >> s == c, "C << (T % 64) must fit one limb");
    assert!(bits(c, t) + 4 <= 64 * N as u32, "need four spare bits");
    assert!(
        2 * bits(c, t) + 2 <= 64 * N as u32 + t,
        "unreduced sums need bitlen(C) + 2 spare bits"
    );
    assert!(t / 64 + 1 == N as u32, "p + 1 must sit in the top limb");
}

/// Bit length of `C * 2^T - 1`.
pub const fn bits(c: u64, t: u32) -> u32 {
    t + 64 - c.leading_zeros()
}

/// `p = C * 2^T - 1`.
pub const fn modulus<const N: usize>(c: u64, t: u32) -> [u64; N] {
    let mut p = [0u64; N];
    let k = (t / 64) as usize;
    let mut i = 0;
    while i < k {
        p[i] = u64::MAX;
        i += 1;
    }
    p[k] = (c << (t % 64)) - 1;
    p
}

/// `2^e mod p`, by repeated modular doubling of one.
pub const fn pow2_mod<const N: usize>(p: &[u64; N], e: u32) -> [u64; N] {
    let mut x = [0u64; N];
    x[0] = 1;
    let mut i = 0;
    while i < e {
        x = double_mod(&x, p);
        i += 1;
    }
    x
}

/// `x / 2 mod p` for `x < p`.
pub const fn half_mod<const N: usize>(x: &[u64; N], p: &[u64; N]) -> [u64; N] {
    let mut y = *x;
    if y[0] & 1 == 1 {
        y = add(&y, p);
    }
    let mut i = 0;
    while i < N {
        let hi = if i + 1 < N { y[i + 1] << 63 } else { 0 };
        y[i] = (y[i] >> 1) | hi;
        i += 1;
    }
    y
}

/// `x / 3 mod p` for `x < p` and `p` not divisible by three.
pub const fn third_mod<const N: usize>(x: &[u64; N], p: &[u64; N]) -> [u64; N] {
    let (xr, pr) = (mod3(x), mod3(p));
    let mut y = *x;
    let mut k = 0;
    while (xr + k * pr) % 3 != 0 {
        y = add(&y, p);
        k += 1;
    }
    let mut rem: u128 = 0;
    let mut i = N;
    while i > 0 {
        i -= 1;
        let cur = (rem << 64) | y[i] as u128;
        y[i] = (cur / 3) as u64;
        rem = cur % 3;
    }
    y
}

/// `x mod 3`, using `2^64 = 1 (mod 3)`.
const fn mod3<const N: usize>(x: &[u64; N]) -> u64 {
    let mut acc = 0;
    let mut i = 0;
    while i < N {
        acc = (acc + x[i] % 3) % 3;
        i += 1;
    }
    acc
}

const fn double_mod<const N: usize>(x: &[u64; N], p: &[u64; N]) -> [u64; N] {
    let y = add(x, x);
    let (d, borrow) = sub(&y, p);
    if borrow {
        y
    } else {
        d
    }
}

const fn add<const N: usize>(a: &[u64; N], b: &[u64; N]) -> [u64; N] {
    let mut r = [0u64; N];
    let mut carry = 0u128;
    let mut i = 0;
    while i < N {
        let t = a[i] as u128 + b[i] as u128 + carry;
        r[i] = t as u64;
        carry = t >> 64;
        i += 1;
    }
    r
}

/// `a - b` and whether it borrowed (`a < b`).
const fn sub<const N: usize>(a: &[u64; N], b: &[u64; N]) -> ([u64; N], bool) {
    let mut r = [0u64; N];
    let mut borrow = false;
    let mut i = 0;
    while i < N {
        let (d0, b0) = a[i].overflowing_sub(b[i]);
        let (d1, b1) = d0.overflowing_sub(borrow as u64);
        r[i] = d1;
        borrow = b0 | b1;
        i += 1;
    }
    (r, borrow)
}
