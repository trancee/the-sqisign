//! Unit tests for the private `ec` scalar arithmetic.

use super::scalar::Scalar;
use super::{BaryCoords, Basis, Curve, Point};
use crate::gf::{Fp2, P324};
use core::cmp::Ordering;

#[test]
fn types_are_cloneable() {
    fn duplicate<T: Clone>(x: &T) -> T {
        x.clone()
    }
    let p = duplicate(&Point::<P324>::IDENTITY);
    assert_eq!(p.ct_eq(&Point::IDENTITY), u32::MAX);
    let e = duplicate(&Curve::<P324>::default());
    assert_eq!(e.c.is_one(), u32::MAX);
    let b = duplicate(&Basis { p, q: p, pmq: p });
    assert_eq!(b.q.is_zero(), u32::MAX);
    let z = Fp2::<P324>::ZERO;
    let c = duplicate(&BaryCoords {
        u: z,
        v: z,
        w: Fp2::ONE,
    });
    assert_eq!(c.w.is_one(), u32::MAX);
}

fn s(words: &[u64]) -> Scalar {
    Scalar::from_bits(words, 64 * words.len())
}

#[test]
fn from_bits_masks_the_top_word() {
    let x = Scalar::from_bits(&[u64::MAX, u64::MAX], 70);
    assert_eq!(&x.words()[..3], &[u64::MAX, 0x3f, 0]);
    assert!(Scalar::from_bits(&[], 0).is_zero());
    assert!(Scalar::from_bits(&[u64::MAX], 64).words()[0] == u64::MAX);
}

#[test]
#[should_panic(expected = "scalar too wide")]
fn from_bits_rejects_wide_scalars() {
    let _ = Scalar::from_bits(&[0; 12], 705);
}

#[test]
fn decrement_wraps_modulo_the_bit_length() {
    let z = s(&[0, 0]);
    assert_eq!(&z.dec_mod2exp(70).words()[..3], &[u64::MAX, 0x3f, 0]);
    let x = s(&[0, 1]);
    assert_eq!(&x.dec_mod2exp(128).words()[..2], &[u64::MAX, 0]);
}

#[test]
fn predicates_and_shifts() {
    let one = s(&[1]);
    assert!(one.is_one() && !one.is_zero() && one.bit0() == 1);
    assert!(!s(&[1, 1]).is_one() && !s(&[2]).is_one());
    let mut x = s(&[0, 3]);
    x.shr1();
    assert_eq!(&x.words()[..2], &[1 << 63, 1]);
    let y = s(&[u64::MAX, 1]).shl2();
    assert_eq!(&y.words()[..3], &[u64::MAX << 2, 7, 0]);
    let mut top = [0u64; 12];
    top[11] = 1;
    let mut t = Scalar::from_bits(&top[..11], 704);
    assert!(t.is_zero());
    t = t.dec_mod2exp(704);
    t.shr1();
    assert_eq!(t.words()[10], u64::MAX >> 1);
}

#[test]
fn subtraction_propagates_borrows() {
    let mut x = s(&[0, 0, 1]);
    x.sub_assign(&s(&[1]));
    assert_eq!(&x.words()[..3], &[u64::MAX, u64::MAX, 0]);
    let mut y = s(&[5]);
    y.sub_assign(&s(&[5]));
    assert!(y.is_zero());
}

#[test]
fn swap_and_compare() {
    let (mut a, mut b) = (s(&[1, 2]), s(&[3, 1]));
    assert_eq!(a.cmp(&b), Ordering::Greater);
    assert_eq!(b.cmp(&a), Ordering::Less);
    assert_eq!(a.cmp(&a), Ordering::Equal);
    Scalar::cswap(&mut a, &mut b, 0);
    assert_eq!(a.words()[0], 1);
    Scalar::cswap(&mut a, &mut b, u64::MAX);
    assert_eq!((a.words()[0], b.words()[0]), (3, 1));
}
