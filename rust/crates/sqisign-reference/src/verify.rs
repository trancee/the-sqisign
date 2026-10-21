//! Safe wrappers over the C reference verification (`verification/ref`,
//! `src/sqisign.c`).

macro_rules! verify_api {
    ($lvl:literal, $order_words:literal) => {
        /// C reference SQIsign verification.
        pub mod verify {
            use super::ec::Curve;
            use super::Fp2;
            use core::ffi::c_int;

            /// C `scalar_t`.
            pub type Scalar = [u64; $order_words];

            /// C `public_key_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct PublicKey {
                /// The curve.
                pub curve: Curve,
                /// `hint_pk`.
                pub hint: u8,
            }

            /// C `signature_t`.
            #[repr(C)]
            #[derive(Clone, Copy, Debug, Default)]
            pub struct Signature {
                /// `E_aux_A`.
                pub aux_a: Fp2,
                /// `mat_Bchall_can_to_B_chall`.
                pub mat: [[Scalar; 2]; 2],
                /// `chall_coeff`.
                pub chall: Scalar,
                /// `hint_aux`.
                pub hint_aux: u8,
                /// `hint_chall`.
                pub hint_chall: u8,
            }

            mod ffi {
                use super::{c_int, Curve, PublicKey, Scalar, Signature};
                ext!($lvl; public_key_from_bytes(pk: *mut PublicKey, enc: *const u8) -> *const u8);
                ext!($lvl; public_key_to_bytes(enc: *mut u8, pk: *const PublicKey) -> *mut u8);
                ext!($lvl; signature_from_bytes(sig: *mut Signature, enc: *const u8));
                ext!($lvl; signature_to_bytes(enc: *mut u8, sig: *const Signature));
                ext!($lvl; hash_to_challenge(s: *mut Scalar, pk: *const PublicKey,
                                             com: *const Curve, m: *const u8, l: usize));
                ext!($lvl; protocols_verify(sig: *mut Signature, pk: *const PublicKey,
                                            m: *const u8, l: usize) -> c_int);
                ext!($lvl; sqisign_verify(sig: *const u8, siglen: u64, m: *const u8, mlen: u64,
                                          pk: *const u8) -> c_int);
                ext!($lvl; sqisign_open(m: *mut u8, mlen: *mut u64, sm: *const u8, smlen: u64,
                                        pk: *const u8) -> c_int);
            }

            /// C `public_key_from_bytes`; `enc` must hold `PUBLICKEY_BYTES`.
            pub fn public_key_from_bytes(enc: &[u8]) -> PublicKey {
                let mut pk = PublicKey::default();
                // SAFETY: the caller passes a full encoding; `pk` is valid.
                unsafe { ffi::public_key_from_bytes(&mut pk, enc.as_ptr()) };
                pk
            }

            /// C `public_key_to_bytes` into `len` bytes.
            pub fn public_key_to_bytes(pk: &PublicKey, len: usize) -> Vec<u8> {
                let mut out = vec![0; len];
                // SAFETY: `out` holds `PUBLICKEY_BYTES` when `len` is that size.
                unsafe { ffi::public_key_to_bytes(out.as_mut_ptr(), pk) };
                out
            }

            /// C `signature_from_bytes`; `enc` must hold `SIGNATURE_BYTES`.
            pub fn signature_from_bytes(enc: &[u8]) -> Signature {
                let mut sig = Signature::default();
                // SAFETY: the caller passes a full encoding; `sig` is valid.
                unsafe { ffi::signature_from_bytes(&mut sig, enc.as_ptr()) };
                sig
            }

            /// C `signature_to_bytes` into `len` bytes.
            pub fn signature_to_bytes(sig: &Signature, len: usize) -> Vec<u8> {
                let mut out = vec![0; len];
                // SAFETY: `out` holds `SIGNATURE_BYTES` when `len` is that size.
                unsafe { ffi::signature_to_bytes(out.as_mut_ptr(), sig) };
                out
            }

            /// C `hash_to_challenge`.
            pub fn hash_to_challenge(pk: &PublicKey, com: &Curve, m: &[u8]) -> Scalar {
                let mut s = Scalar::default();
                // SAFETY: all pointers reference valid values; `m` has `m.len()` bytes.
                unsafe { ffi::hash_to_challenge(&mut s, pk, com, m.as_ptr(), m.len()) };
                s
            }

            /// C `protocols_verify`.
            pub fn protocols_verify(sig: &Signature, pk: &PublicKey, m: &[u8]) -> bool {
                let mut sig = *sig;
                // SAFETY: all pointers reference valid values; `m` has `m.len()` bytes.
                unsafe { ffi::protocols_verify(&mut sig, pk, m.as_ptr(), m.len()) == 1 }
            }

            /// C `sqisign_verify` returning 0; `pk` must hold `PUBLICKEY_BYTES`.
            pub fn sqisign_verify(sig: &[u8], m: &[u8], pk: &[u8]) -> bool {
                let len = |x: &[u8]| u64::try_from(x.len()).expect("fits u64");
                // SAFETY: lengths are passed with their buffers; C reads `pk` in full.
                unsafe {
                    ffi::sqisign_verify(sig.as_ptr(), len(sig), m.as_ptr(), len(m), pk.as_ptr()) == 0
                }
            }

            /// C `sqisign_open`: the message if the signature is valid.
            pub fn sqisign_open(sm: &[u8], pk: &[u8]) -> Option<Vec<u8>> {
                let mut m = vec![0; sm.len()];
                let mut mlen = 0u64;
                let smlen = u64::try_from(sm.len()).expect("fits u64");
                // SAFETY: `m` holds `smlen` bytes; C reads `pk` in full.
                let ret = unsafe {
                    ffi::sqisign_open(m.as_mut_ptr(), &mut mlen, sm.as_ptr(), smlen, pk.as_ptr())
                };
                m.truncate(usize::try_from(mlen).expect("fits usize"));
                (ret == 0).then_some(m)
            }
        }
    };
}
