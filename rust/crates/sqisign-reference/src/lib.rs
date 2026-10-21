//! Test-only bindings to the vendor SQIsign C reference implementation.
//!
//! Each parameter set is compiled with the `ref` build type, so symbols are
//! `sqisign_<level>_ref_<name>`. Values are the C reference's own (unsaturated)
//! representation; compare them with the Rust port only through encodings.

#![allow(unsafe_code)]

#[macro_use]
mod gf;

level!(p324_3, "p324_3", 6, 41);
level!(p500_27, "p500_27", 9, 64);
level!(p664_17, "p664_17", 11, 84);
