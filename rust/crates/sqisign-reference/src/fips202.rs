//! C reference SHAKE256 (`common/generic/fips202.c`, compiled once and not
//! namespaced).

mod ffi {
    extern "C" {
        pub fn shake256(output: *mut u8, outlen: usize, input: *const u8, inlen: usize);
    }
}

/// C `shake256`: `outlen` bytes of SHAKE256 of `input`.
pub fn shake256(input: &[u8], outlen: usize) -> Vec<u8> {
    let mut out = vec![0; outlen];
    // SAFETY: both buffers are passed with their lengths.
    unsafe { ffi::shake256(out.as_mut_ptr(), outlen, input.as_ptr(), input.len()) };
    out
}
