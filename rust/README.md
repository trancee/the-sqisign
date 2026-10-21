# SQIsign in Rust

A Rust port of the [SQIsign](https://sqisign.org) round-3 reference implementation in this repository's `src/`. It is
built to be byte-compatible with the C code and checked module by module against it. The plan, the evidence required
and the reasons C stays the default backend are in
[ADR 0004](../kotlin/docs/adr/0004-rust-port-opt-in-backend.md).

> [!WARNING]
> Work in progress, not reviewed. Do not use it to protect real data.

## Status

| Module | State |
|---|---|
| `gf`: GF(p), GF(p^2) | ported; differential tests against C `ref` for all three primes |
| `ec`: points, curves, ladders (`ec.c`) | ported; differential tests against C `ref` for all three primes |
| `ec`: isogenies, bases, pairings, normalisation (`isog.c`, `basis.c`, `biextension.c`, `normalize.c`) | ported; differential tests against C `ref` for all three primes |
| `hd`, verification | not started |
| `mp`, `quaternion`, `id2iso`, signing | not started |
| C ABI + Kotlin backend (`sqisign.backend=rust`) | not started |

## Build and test

Requirements: rustup (it installs the toolchain pinned in `rust-toolchain.toml`) and a C compiler with `__int128`
(Clang or GCC) for the C reference used by the tests.

```sh
cd rust
cargo test
cargo llvm-cov -p sqisign --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100
```

The `sqisign-reference` crate compiles the vendor C code from `../src` with the `ref` build type, so the tests compare
the port with the C implementation in the same checkout.

## License

Apache-2.0, like the vendor implementation.
