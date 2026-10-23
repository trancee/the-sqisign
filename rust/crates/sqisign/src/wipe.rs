//! Best-effort clearing of secret values.
//!
//! The crate forbids `unsafe`, so it cannot use volatile writes. Each store
//! is followed by `black_box` on the cleared memory, which the compiler must
//! treat as a read, so the store is not dead and is not elided.

use core::hint::black_box;

/// Overwrites `s` with `T::default()`.
pub(crate) fn wipe<T: Copy + Default>(s: &mut [T]) {
    s.fill(T::default());
    black_box(&*s);
}

#[cfg(test)]
mod tests {
    use super::wipe;

    #[test]
    fn wipe_zeroes() {
        let mut s = [7u64; 3];
        wipe(&mut s);
        assert_eq!(s, [0; 3]);
    }
}
