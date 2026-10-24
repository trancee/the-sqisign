//! The pointer handling of the C ABI, small enough to run under Miri:
//! `cargo +nightly miri test -p sqisign-capi --test miri` (CI job `Miri`).
//! Key generation and signing themselves are too slow to interpret, so
//! `randombytes` is defined here, in Rust, where Miri can call it, and the
//! paths through it stop before the protocol (a failing source, a malformed
//! secret key). Verification runs on the first KAT entry of `P324_3`.

#[path = "../../sqisign/tests/support/kat.rs"]
#[allow(dead_code)] // shared with the other test files, which use `load`
mod kat;

use core::cell::Cell;
use core::ffi::{c_int, c_ulonglong};
use core::ptr;
use sqisign::gf::{FpParams, P324, P500, P664};
use sqisign::sign::SignParams;
use sqisign::verify::SqisignParams;
use sqisign_capi::*;

thread_local! {
    /// Whether this thread's `randombytes` fails, and the bytes it was asked for.
    static SOURCE: Cell<(bool, u64)> = const { Cell::new((false, 0)) };
}

/// The `randombytes` the final link provides elsewhere: a fixed pattern, or
/// a failure.
#[no_mangle]
extern "C" fn randombytes(x: *mut u8, xlen: c_ulonglong) -> c_int {
    let (fail, drawn) = SOURCE.get();
    SOURCE.set((fail, drawn + xlen));
    if fail {
        return -1;
    }
    let len = usize::try_from(xlen).expect("a buffer length");
    // SAFETY: the caller passes a buffer of `xlen` writable bytes.
    unsafe { ptr::write_bytes(x, 0x5a, len) };
    0
}

fn sizes_of<P: SignParams>() -> [usize; 3] {
    [P::PUBLICKEY_BYTES, P::SECRETKEY_BYTES, P::SIGNATURE_BYTES]
}

fn sizes(set: c_int) -> (c_int, [usize; 3]) {
    let mut s = [0; 3];
    let [a, b, c] = s.each_mut().map(ptr::from_mut);
    // SAFETY: three writable sizes.
    (unsafe { sqisign_kmp_sizes(set, a, b, c) }, s)
}

fn first() -> kat::Kat {
    kat::entries("PQCsignKAT_270_SQIsign_p324_3.rsp", 1).swap_remove(0)
}

fn verify(sig: &[u8], m: &[u8], pk: &[u8]) -> c_int {
    // SAFETY: the slices hold the lengths passed; `pk` has the set's size.
    unsafe { sqisign_kmp_verify(0, sig.as_ptr(), sig.len(), m.as_ptr(), m.len(), pk.as_ptr()) }
}

#[test]
fn sizes_are_written() {
    let expected = [sizes_of::<P324>(), sizes_of::<P500>(), sizes_of::<P664>()];
    for (set, n) in (0..).zip(expected) {
        assert_eq!(sizes(set), (SQISIGN_KMP_OK, n));
    }
    assert_eq!(sizes(3), (SQISIGN_KMP_INVALID_SET, [0; 3]));
}

/// A failing source leaves the outputs untouched; the seed buffer is cleared
/// and dropped on the error path.
#[test]
fn a_failing_source_is_an_error() {
    SOURCE.set((true, 0));
    let [npk, nsk, nsig] = sizes_of::<P324>();
    let (mut pk, mut sk, mut sig) = (vec![1; npk], vec![2; nsk], vec![3; nsig]);
    // SAFETY: buffers of the reported sizes; a null message of length 0.
    unsafe {
        let r = sqisign_kmp_keypair(0, pk.as_mut_ptr(), sk.as_mut_ptr());
        assert_eq!(r, SQISIGN_KMP_ERROR);
        let r = sqisign_kmp_sign(0, sig.as_mut_ptr(), ptr::null(), 0, sk.as_ptr());
        assert_eq!(r, SQISIGN_KMP_ERROR);
    }
    assert_eq!(SOURCE.get(), (true, 96));
    assert!(pk.iter().all(|&b| b == 1) && sk.iter().all(|&b| b == 2));
    assert!(sig.iter().all(|&b| b == 3));
}

/// A secret ideal of norm 0 divides by zero inside signing (undefined in C);
/// the panic unwinds through the seed's drop and becomes an error.
#[test]
#[cfg_attr(miri, ignore = "runs the protocol: an hour or more interpreted")]
fn a_panic_unwinds_to_an_error() {
    SOURCE.set((false, 0));
    let mut sk = first().sk;
    let at = <P324 as SqisignParams>::PUBLICKEY_BYTES;
    sk[at..at + <P324 as FpParams>::ENCODED_BYTES].fill(0);
    let mut sig = vec![3; sizes_of::<P324>()[2]];
    let m = [0x42u8; 5];
    // SAFETY: buffers of the reported sizes and the message length.
    let r = unsafe { sqisign_kmp_sign(0, sig.as_mut_ptr(), m.as_ptr(), m.len(), sk.as_ptr()) };
    assert_eq!(r, SQISIGN_KMP_ERROR);
    assert_eq!(SOURCE.get(), (false, 48));
    assert!(sig.iter().all(|&b| b == 3));
}

/// A signature of the wrong length is rejected before any arithmetic, after
/// all three inputs are read through their pointers.
#[test]
fn verify_reads_the_given_lengths() {
    let k = first();
    let n = sizes_of::<P324>()[2];
    assert_eq!(verify(&k.sm[..n - 1], &k.msg, &k.pk), SQISIGN_KMP_ERROR);
    assert_eq!(verify(&k.sm[..n + 1], &k.msg, &k.pk), SQISIGN_KMP_ERROR);
    // SAFETY: null pointers with length 0, which the contract allows.
    let r = unsafe { sqisign_kmp_verify(0, ptr::null(), 0, ptr::null(), 0, k.pk.as_ptr()) };
    assert_eq!(r, SQISIGN_KMP_ERROR);
}

#[test]
#[cfg_attr(miri, ignore = "runs the protocol: an hour or more interpreted")]
fn a_kat_signature_verifies_through_raw_pointers() {
    let k = first();
    let mut sig = k.sm[..k.sm.len() - k.msg.len()].to_vec();
    assert_eq!(verify(&sig, &k.msg, &k.pk), SQISIGN_KMP_OK);
    sig[0] ^= 1;
    assert_eq!(verify(&sig, &k.msg, &k.pk), SQISIGN_KMP_ERROR);
}

#[test]
fn secure_clear_writes_exactly_the_buffer() {
    let mut buf = [0xa5u8; 40];
    // SAFETY: the middle 32 bytes of `buf`; a null pointer.
    unsafe {
        sqisign_kmp_secure_clear(buf[4..].as_mut_ptr().cast(), 32);
        sqisign_kmp_secure_clear(ptr::null_mut(), usize::MAX);
    }
    assert_eq!(buf[..4], [0xa5; 4]);
    assert_eq!(buf[4..36], [0; 32]);
    assert_eq!(buf[36..], [0xa5; 4]);
}
