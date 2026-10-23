//! Direct checks of the strong Lucas test, whose rare exits `probab_prime`
//! does not reach.

use super::*;
use crate::gf::P324;

/// The sieve rejects every value whose Selfridge search meets a shared
/// factor (11 meets `|D| = 11`), so only a direct call reaches that exit.
#[test]
fn strong_lucas_rejects_a_shared_factor() {
    assert_eq!(jacobi_u64(0, 7), 0);
    for v in [11, 21, 39] {
        let n = Ibz::<P324>::from_i64(v, 7);
        assert!(!n.strong_lucas(&Monty::<P324, 2>::new(&n)), "{v}");
    }
}

/// Selfridge strong Lucas pseudoprimes, all stopped by the sieve or by
/// Miller-Rabin to base 2 in `probab_prime`.
#[test]
fn strong_lucas_passes_its_pseudoprimes() {
    for v in [5459, 5777, 10877, 16109, 18971, 324_899] {
        let n = Ibz::<P324>::from_i64(v, 20);
        assert!(n.strong_lucas(&Monty::<P324, 2>::new(&n)), "{v}");
    }
}
