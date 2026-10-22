//! Rust port of the SQIsign round-3 reference implementation.
//!
//! Ported module by module from the vendor C sources and checked
//! byte-for-byte against them; see `kotlin/docs/adr/0004-rust-port-opt-in-backend.md`.

#![forbid(unsafe_code)]

pub mod ec;
pub mod gf;
pub mod hd;
pub mod id2iso;
pub mod mp;
pub mod prng;
pub mod quat;
pub mod shake;
pub mod sign;
pub mod verify;
