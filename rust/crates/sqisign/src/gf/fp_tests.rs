//! Domain-boundary tests: partially reduced inputs in `[p, 2^BITS)` are not
//! reachable through `decode`, so the invariants are checked on raw limbs.

use super::{Fp, FpParams, P324, P500, P664};

/// `2^BITS - 1`, the largest value of the partially reduced domain.
fn domain_max<P: FpParams>() -> Fp<P> {
    let mut v = P::ZERO;
    let top = P::BITS - 64 * (P::N as u32 - 1);
    for (i, limb) in v.as_mut().iter_mut().enumerate() {
        *limb = if i + 1 == P::N {
            (1u64 << top) - 1
        } else {
            u64::MAX
        };
    }
    Fp { v }
}

fn in_domain<P: FpParams>(x: &Fp<P>) -> bool {
    let top = P::BITS - 64 * (P::N as u32 - 1);
    x.limbs()[P::N - 1] >> top == 0
}

fn encoded<P: FpParams>(x: &Fp<P>) -> Vec<u8> {
    let mut out = vec![0u8; P::ENCODED_BYTES];
    x.encode(&mut out);
    out
}

fn check_domain_edges<P: FpParams>() {
    let max = domain_max::<P>();
    let p = Fp::<P> { v: P::P };
    let small = Fp::<P>::from_small(7);
    let edges = [Fp::ZERO, p, max, Fp::ONE, small, -small];
    for a in &edges {
        for b in &edges {
            let (sum, diff, prod) = (*a + *b, *a - *b, *a * *b);
            assert!(in_domain(&sum) && in_domain(&diff) && in_domain(&prod));
            assert_eq!(encoded(&(sum - *b)), encoded(a), "(a + b) - b = a");
            assert_eq!(encoded(&(diff + *b)), encoded(a), "(a - b) + b = a");
            assert_eq!(encoded(&prod), encoded(&(*b * *a)), "commutative product");
            assert_eq!(encoded(&(prod * small)), encoded(&(*a * (*b * small))));
        }
        assert!(in_domain(&a.mul_small(u32::MAX)));
        assert_eq!(encoded(&a.mul_small(3)), encoded(&(*a + *a + *a)));
    }
    assert_eq!(p.is_zero(), u32::MAX);
    assert_eq!(p.ct_eq(&Fp::ZERO), u32::MAX);
    assert_eq!(encoded(&p), encoded(&Fp::<P>::ZERO));
}

#[test]
fn partially_reduced_operands_stay_in_domain() {
    check_domain_edges::<P324>();
    check_domain_edges::<P500>();
    check_domain_edges::<P664>();
}

#[test]
fn elements_are_cloneable() {
    fn duplicate<T: Clone>(x: &T) -> T {
        x.clone()
    }
    let one = duplicate(&Fp::<P324>::ONE);
    assert_eq!(one.ct_eq(&Fp::ONE), u32::MAX);
}

#[test]
fn maximal_sum_needs_two_reductions() {
    let max = domain_max::<P324>();
    let sum = max + max;
    assert!(in_domain(&sum));
    assert_eq!(encoded(&sum), encoded(&max.mul_small(2)));
}

#[test]
#[should_panic(expected = "encoded length")]
fn encode_rejects_wrong_length() {
    Fp::<P324>::ONE.encode(&mut [0u8; 40]);
}

#[test]
#[should_panic(expected = "encoded length")]
fn decode_rejects_wrong_length() {
    let _ = Fp::<P324>::decode(&[0u8; 42]);
}

#[test]
#[should_panic(expected = "encoded length")]
fn fp2_decode_rejects_wrong_length() {
    let _ = super::Fp2::<P324>::decode(&[0u8; 81]);
}
