//! The C ABI against the NIST KAT files, with the vendor CTR-DRBG
//! `randombytes` of `sqisign-reference` as the randomness source, as in the
//! test-only KAT library.

#[path = "../../sqisign/tests/support/kat.rs"]
mod kat;

use core::ptr;
use sqisign_capi::*;
use sqisign_reference::prng::randombytes_init;

const SETS: [(i32, &str); 3] = [
    (0, "PQCsignKAT_270_SQIsign_p324_3.rsp"),
    (1, "PQCsignKAT_417_SQIsign_p500_27.rsp"),
    (2, "PQCsignKAT_549_SQIsign_p664_17.rsp"),
];

/// The KAT entry `i` of `file` and its public key, secret key and
/// signature sizes.
fn first(file: &str, i: usize) -> (kat::Kat, [usize; 3]) {
    let k = kat::load(file).swap_remove(i);
    let n = [k.pk.len(), k.sk.len(), k.sm.len() - k.msg.len()];
    (k, n)
}

fn sizes(set: i32) -> (i32, [usize; 3]) {
    let mut s = [0; 3];
    let [a, b, c] = s.each_mut().map(ptr::from_mut);
    // SAFETY: three writable sizes.
    (unsafe { sqisign_kmp_sizes(set, a, b, c) }, s)
}

fn verify(set: i32, sig: &[u8], m: &[u8], pk: &[u8]) -> i32 {
    // SAFETY: the slices hold the lengths passed; `pk` has the set's size.
    unsafe {
        sqisign_kmp_verify(
            set,
            sig.as_ptr(),
            sig.len(),
            m.as_ptr(),
            m.len(),
            pk.as_ptr(),
        )
    }
}

#[test]
fn sizes_match_the_parameter_sets() {
    for (set, file) in SETS {
        assert_eq!(sizes(set), (SQISIGN_KMP_OK, first(file, 0).1));
    }
}

#[test]
fn invalid_sets_are_rejected() {
    let mut buf = [0u8; 1024];
    let p = buf.as_mut_ptr();
    for set in [-1, 3, i32::MAX] {
        assert_eq!(sizes(set), (SQISIGN_KMP_INVALID_SET, [0; 3]));
        // SAFETY: the buffers are never touched for an invalid set.
        unsafe {
            assert_eq!(sqisign_kmp_keypair(set, p, p), SQISIGN_KMP_INVALID_SET);
            assert_eq!(sqisign_kmp_sign(set, p, p, 0, p), SQISIGN_KMP_INVALID_SET);
        }
        assert_eq!(verify(set, &[], &[], &buf), SQISIGN_KMP_INVALID_SET);
    }
}

#[test]
fn kats_match() {
    for (set, file) in SETS {
        let (k, [npk, nsk, nsig]) = first(file, 0);
        randombytes_init(k.seed.as_slice().try_into().expect("a 48-byte seed"));
        let (mut pk, mut sk, mut sig) = (vec![0; npk], vec![0; nsk], vec![0; nsig]);
        // SAFETY: buffers of the reported sizes and the message length.
        unsafe {
            assert_eq!(
                sqisign_kmp_keypair(set, pk.as_mut_ptr(), sk.as_mut_ptr()),
                SQISIGN_KMP_OK
            );
            let r = sqisign_kmp_sign(
                set,
                sig.as_mut_ptr(),
                k.msg.as_ptr(),
                k.msg.len(),
                sk.as_ptr(),
            );
            assert_eq!(r, SQISIGN_KMP_OK);
        }
        assert_eq!((&pk, &sk), (&k.pk, &k.sk));
        assert_eq!(sig, k.sm[..nsig]);
        assert_eq!(verify(set, &sig, &k.msg, &pk), SQISIGN_KMP_OK);
        // A flipped bit, a wrong length and another message are rejected.
        sig[0] ^= 1;
        assert_eq!(verify(set, &sig, &k.msg, &pk), SQISIGN_KMP_ERROR);
        sig[0] ^= 1;
        assert_eq!(verify(set, &sig[1..], &k.msg, &pk), SQISIGN_KMP_ERROR);
        assert_eq!(verify(set, &sig, &[], &pk), SQISIGN_KMP_ERROR);
    }
}

#[test]
fn empty_messages_sign_and_verify() {
    let (set, file) = SETS[0];
    let (k, [npk, nsk, nsig]) = first(file, 1);
    randombytes_init(k.seed.as_slice().try_into().expect("a 48-byte seed"));
    let (mut pk, mut sk, mut sig) = (vec![0; npk], vec![0; nsk], vec![0; nsig]);
    // SAFETY: buffers of the reported sizes; a null message of length 0.
    unsafe {
        assert_eq!(
            sqisign_kmp_keypair(set, pk.as_mut_ptr(), sk.as_mut_ptr()),
            SQISIGN_KMP_OK
        );
        let r = sqisign_kmp_sign(set, sig.as_mut_ptr(), ptr::null(), 0, sk.as_ptr());
        assert_eq!(r, SQISIGN_KMP_OK);
        let r = sqisign_kmp_verify(set, sig.as_ptr(), nsig, ptr::null(), 0, pk.as_ptr());
        assert_eq!(r, SQISIGN_KMP_OK);
        let r = sqisign_kmp_verify(set, ptr::null(), 0, ptr::null(), 0, pk.as_ptr());
        assert_eq!(r, SQISIGN_KMP_ERROR);
    }
}

#[test]
fn panics_are_reported_as_errors() {
    // A secret key whose integers exceed their capacity fails to decode, or
    // signing with it fails; either way the call returns an error.
    let (set, file) = SETS[0];
    let [_, nsk, nsig] = first(file, 0).1;
    let sk = vec![0xff; nsk];
    let mut sig = vec![0; nsig];
    randombytes_init(&[7; 48]);
    // SAFETY: buffers of the reported sizes.
    let r = unsafe { sqisign_kmp_sign(set, sig.as_mut_ptr(), ptr::null(), 0, sk.as_ptr()) };
    assert_eq!(r, SQISIGN_KMP_ERROR);
}

#[test]
fn secure_clear_zeroes() {
    let mut buf = [0xa5u8; 37];
    // SAFETY: valid buffers, a null pointer and an empty length.
    unsafe {
        sqisign_kmp_secure_clear(buf.as_mut_ptr().cast(), 0);
        assert_eq!(buf, [0xa5; 37]);
        sqisign_kmp_secure_clear(ptr::null_mut(), 16);
        sqisign_kmp_secure_clear(buf.as_mut_ptr().cast(), buf.len());
    }
    assert_eq!(buf, [0; 37]);
}
