//! The C ABI of `kotlin/native/include/sqisign_kmp.h` over the Rust port,
//! so the Kotlin JNI glue and cinterop definitions link against either
//! backend (ADR 0004).
//!
//! Randomness comes from the C function `randombytes`, which the final link
//! provides: the vendor `randombytes_system.c` (operating system) in the
//! shipped library, the vendor NIST CTR-DRBG in the test-only KAT library
//! (ADR 0002). Like the C `prng_seed`, key generation and signing each draw
//! a 48-byte seed from it.

use core::ffi::{c_int, c_ulonglong, c_void};
use core::ptr::{self, write_volatile};
use core::sync::atomic::{compiler_fence, Ordering};
use std::panic::{catch_unwind, AssertUnwindSafe};

use sqisign::gf::{P324, P500, P664};
use sqisign::prng::SEED_BYTES;
use sqisign::sign::{self, SignParams};
use sqisign::verify;

/// `SQISIGN_KMP_OK`.
pub const SQISIGN_KMP_OK: c_int = 0;
/// `SQISIGN_KMP_ERROR`.
pub const SQISIGN_KMP_ERROR: c_int = -1;
/// `SQISIGN_KMP_INVALID_SET`.
pub const SQISIGN_KMP_INVALID_SET: c_int = -2;

extern "C" {
    fn randombytes(x: *mut u8, xlen: c_ulonglong) -> c_int;
}

/// Runs `f` for the parameter set `set` (`SQISIGN_KMP_P324_3`,
/// `SQISIGN_KMP_P500_27`, `SQISIGN_KMP_P664_17`). A panic, which would be a
/// defect, is reported as `SQISIGN_KMP_ERROR` instead of unwinding into C.
macro_rules! dispatch {
    ($set:expr, $f:ident($($arg:expr),*)) => {
        catch_unwind(AssertUnwindSafe(|| match $set {
            0 => $f::<P324>($($arg),*),
            1 => $f::<P500>($($arg),*),
            2 => $f::<P664>($($arg),*),
            _ => SQISIGN_KMP_INVALID_SET,
        }))
        .unwrap_or(SQISIGN_KMP_ERROR)
    };
}

fn status(ok: bool) -> c_int {
    [SQISIGN_KMP_ERROR, SQISIGN_KMP_OK][usize::from(ok)]
}

/// Zeroes `buf` with volatile writes the compiler cannot elide.
fn clear(buf: &mut [u8]) {
    // SAFETY: the pointer is valid for `buf.len()` bytes.
    unsafe { sqisign_kmp_secure_clear(buf.as_mut_ptr().cast(), buf.len()) };
}

/// A seed that is cleared when dropped, also when a panic unwinds.
struct Seed([u8; SEED_BYTES]);

impl Drop for Seed {
    fn drop(&mut self) {
        clear(&mut self.0);
    }
}

/// Runs `f` on a fresh seed from `randombytes`, clearing the seed after.
fn with_seed(f: impl FnOnce([u8; SEED_BYTES]) -> c_int) -> c_int {
    let mut seed = Seed([0; SEED_BYTES]);
    // SAFETY: `seed` holds the `SEED_BYTES` bytes written.
    let drawn = unsafe { randombytes(seed.0.as_mut_ptr(), SEED_BYTES as c_ulonglong) } == 0;
    drawn.then_some(seed.0).map_or(SQISIGN_KMP_ERROR, f)
}

/// `len` bytes at `p`, or none for `len == 0` (where `p` may be null).
///
/// # Safety
///
/// For `len > 0`, `p` must be valid for reads of `len` bytes.
unsafe fn bytes<'a>(p: *const u8, len: usize) -> &'a [u8] {
    if len == 0 {
        &[]
    } else {
        // SAFETY: the caller's contract.
        unsafe { core::slice::from_raw_parts(p, len) }
    }
}

fn sizes<P: SignParams>(pk: *mut usize, sk: *mut usize, sig: *mut usize) -> c_int {
    // SAFETY: the caller's contract of `sqisign_kmp_sizes`.
    unsafe {
        pk.write(P::PUBLICKEY_BYTES);
        sk.write(P::SECRETKEY_BYTES);
        sig.write(P::SIGNATURE_BYTES);
    }
    SQISIGN_KMP_OK
}

fn keypair<P: SignParams>(pk: *mut u8, sk: *mut u8) -> c_int {
    with_seed(|seed| {
        let (p, mut s) = sign::keypair::<P>(seed);
        // SAFETY: the caller's contract of `sqisign_kmp_keypair`; the
        // encodings have exactly the reported sizes.
        unsafe {
            ptr::copy_nonoverlapping(p.as_ptr(), pk, P::PUBLICKEY_BYTES);
            ptr::copy_nonoverlapping(s.as_ptr(), sk, P::SECRETKEY_BYTES);
        }
        clear(&mut s);
        SQISIGN_KMP_OK
    })
}

fn sign<P: SignParams>(sig: *mut u8, m: *const u8, mlen: usize, sk: *const u8) -> c_int {
    // SAFETY: the caller's contract of `sqisign_kmp_sign`.
    let (m, sk) = unsafe { (bytes(m, mlen), bytes(sk, P::SECRETKEY_BYTES)) };
    with_seed(|seed| {
        let out = sign::sign::<P>(sk, m, seed).map(|s| {
            // SAFETY: the caller's contract; the signature has the reported
            // size.
            unsafe { ptr::copy_nonoverlapping(s.as_ptr(), sig, P::SIGNATURE_BYTES) };
        });
        status(out.is_some())
    })
}

fn verify<P: SignParams>(
    sig: *const u8,
    siglen: usize,
    m: *const u8,
    mlen: usize,
    pk: *const u8,
) -> c_int {
    // SAFETY: the caller's contract of `sqisign_kmp_verify`.
    let (sig, m, pk) = unsafe {
        (
            bytes(sig, siglen),
            bytes(m, mlen),
            bytes(pk, P::PUBLICKEY_BYTES),
        )
    };
    status(verify::verify::<P>(sig, m, pk))
}

/// Writes the encoded sizes for `set`. Returns `SQISIGN_KMP_OK` or
/// `SQISIGN_KMP_INVALID_SET`.
///
/// # Safety
///
/// The three pointers must be valid for writes.
#[no_mangle]
pub unsafe extern "C" fn sqisign_kmp_sizes(
    set: c_int,
    public_key_bytes: *mut usize,
    secret_key_bytes: *mut usize,
    signature_bytes: *mut usize,
) -> c_int {
    dispatch!(
        set,
        sizes(public_key_bytes, secret_key_bytes, signature_bytes)
    )
}

/// Generates a key pair.
///
/// # Safety
///
/// `pk` and `sk` must be valid for writes of the sizes reported by
/// [`sqisign_kmp_sizes`].
#[no_mangle]
pub unsafe extern "C" fn sqisign_kmp_keypair(set: c_int, pk: *mut u8, sk: *mut u8) -> c_int {
    dispatch!(set, keypair(pk, sk))
}

/// Writes a detached signature of exactly `signature_bytes` bytes to `sig`.
///
/// # Safety
///
/// `sig` must be valid for writes and `sk` for reads of the reported sizes,
/// and `m` for reads of `mlen` bytes unless `mlen` is 0.
#[no_mangle]
pub unsafe extern "C" fn sqisign_kmp_sign(
    set: c_int,
    sig: *mut u8,
    m: *const u8,
    mlen: usize,
    sk: *const u8,
) -> c_int {
    dispatch!(set, sign(sig, m, mlen, sk))
}

/// Returns `SQISIGN_KMP_OK` if `sig` is a valid signature of `m` under
/// `pk`, `SQISIGN_KMP_ERROR` otherwise.
///
/// # Safety
///
/// `sig` and `m` must be valid for reads of `siglen` and `mlen` bytes unless
/// those are 0, and `pk` for reads of the reported public key size.
#[no_mangle]
pub unsafe extern "C" fn sqisign_kmp_verify(
    set: c_int,
    sig: *const u8,
    siglen: usize,
    m: *const u8,
    mlen: usize,
    pk: *const u8,
) -> c_int {
    dispatch!(set, verify(sig, siglen, m, mlen, pk))
}

/// Zeroizes `len` bytes at `mem` in a way the compiler cannot elide.
///
/// # Safety
///
/// `mem` must be null or valid for writes of `len` bytes.
#[no_mangle]
pub unsafe extern "C" fn sqisign_kmp_secure_clear(mem: *mut c_void, len: usize) {
    let p = mem.cast::<u8>();
    for i in (0..len).filter(|_| !p.is_null()) {
        // SAFETY: the caller's contract.
        unsafe { write_volatile(p.add(i), 0) };
    }
    compiler_fence(Ordering::SeqCst);
}
