//! Bindings for the NIST CTR-DRBG `randombytes` (`common/ref`, compiled once
//! and not namespaced) and, per level, the SHAKE256 PRNG (`prng.c`) with the
//! sampling functions of `mp.c` built on it.

use core::ffi::c_int;

mod ffi {
    use super::c_int;
    extern "C" {
        pub fn randombytes_init(entropy: *mut u8, personalization: *mut u8, strength: c_int);
        pub fn randombytes(out: *mut u8, len: u64) -> c_int;
    }
}

/// C `randombytes_init` without personalization: reseeds the calling
/// thread's DRBG.
pub fn randombytes_init(entropy: &[u8; 48]) {
    let mut e = *entropy;
    // SAFETY: `e` holds the 48 bytes C reads; a null personalization is
    // allowed.
    unsafe { ffi::randombytes_init(e.as_mut_ptr(), core::ptr::null_mut(), 256) };
}

/// C `randombytes`: the next `len` bytes of the calling thread's DRBG.
pub fn randombytes(len: usize) -> Vec<u8> {
    let mut out = vec![0; len];
    // SAFETY: `out` holds `len` bytes.
    let ret = unsafe { ffi::randombytes(out.as_mut_ptr(), len as u64) };
    assert_eq!(ret, 0);
    out
}

macro_rules! prng_api {
    ($lvl:literal) => {
        /// C `prng_domain_ctx_t`.
        #[repr(C)]
        pub struct Domain {
            ctx: [u64; 26],
            seeded: u32,
        }

        mod prng_ffi {
            use super::{c_int, Domain, Ibz};
            ext!($lvl; prng_seed() -> c_int);
            ext!($lvl; prng_clear());
            ext!($lvl; prng_domain_seed(ctx: *mut Domain, sep: *const u8) -> c_int);
            ext!($lvl; prng_domain_clear(ctx: *mut Domain));
            ext!($lvl; prng_random_bytes(ctx: *mut Domain, out: *mut u8, len: usize) -> c_int);
            ext!($lvl; ibz_rand_interval(r: *mut Ibz, a: *const Ibz, b: *const Ibz) -> c_int);
            ext!($lvl; ibz_rand_interval_with_domain(r: *mut Ibz, a: *const Ibz, b: *const Ibz,
                d: *mut Domain) -> c_int);
            ext!($lvl; ibz_rand_interval_minm_m(r: *mut Ibz, m: i32) -> c_int);
            ext!($lvl; ibz_rand_interval_minm_m_with_domain(r: *mut Ibz, m: i32, d: *mut Domain)
                -> c_int);
        }

        /// Seeds the DRBG from `entropy`, then the calling thread's PRNG root
        /// and default domain (C `prng_seed`). Returns the root seed drawn.
        pub fn prng_seed(entropy: &[u8; 48]) -> [u8; 48] {
            $crate::prng::randombytes_init(entropy);
            let seed = $crate::prng::randombytes(48).try_into().unwrap();
            $crate::prng::randombytes_init(entropy);
            // SAFETY: no arguments.
            assert_eq!(unsafe { prng_ffi::prng_seed() }, 0);
            seed
        }

        /// C `prng_clear`.
        pub fn prng_clear() {
            // SAFETY: no arguments.
            unsafe { prng_ffi::prng_clear() };
        }

        impl Domain {
            /// C `prng_domain_seed` for the 3-byte separator `sep`, or its
            /// status when that fails.
            pub fn new(sep: &[u8]) -> Result<Self, c_int> {
                let mut d = Domain { ctx: [0; 26], seeded: 0 };
                let mut s = sep.to_vec();
                s.push(0);
                // SAFETY: `s` is NUL-terminated.
                match unsafe { prng_ffi::prng_domain_seed(&mut d, s.as_ptr()) } {
                    0 => Ok(d),
                    e => Err(e),
                }
            }

            /// C `prng_random_bytes`.
            pub fn fill(&mut self, len: usize) -> Vec<u8> {
                let mut out = vec![0; len];
                // SAFETY: `out` holds `len` bytes.
                let ret = unsafe { prng_ffi::prng_random_bytes(self, out.as_mut_ptr(), len) };
                assert_eq!(ret, 0);
                out
            }
        }

        impl Drop for Domain {
            fn drop(&mut self) {
                // SAFETY: a seeded context.
                unsafe { prng_ffi::prng_domain_clear(self) };
            }
        }

        /// C `ibz_rand_interval_with_domain`, or `ibz_rand_interval` on the
        /// default domain.
        pub fn rand_interval(a: &Ibz, b: &Ibz, d: Option<&mut Domain>) -> (Ibz, c_int) {
            let mut r = Ibz::default();
            // SAFETY: valid values and a seeded context.
            let ret = unsafe {
                match d {
                    Some(d) => prng_ffi::ibz_rand_interval_with_domain(&mut r, a, b, d),
                    None => prng_ffi::ibz_rand_interval(&mut r, a, b),
                }
            };
            (r, ret)
        }

        /// C `ibz_rand_interval_minm_m_with_domain`, or
        /// `ibz_rand_interval_minm_m` on the default domain.
        pub fn rand_interval_minm_m(m: i32, d: Option<&mut Domain>) -> (Ibz, c_int) {
            let mut r = Ibz::default();
            // SAFETY: valid values and a seeded context.
            let ret = unsafe {
                match d {
                    Some(d) => prng_ffi::ibz_rand_interval_minm_m_with_domain(&mut r, m, d),
                    None => prng_ffi::ibz_rand_interval_minm_m(&mut r, m),
                }
            };
            (r, ret)
        }
    };
}
