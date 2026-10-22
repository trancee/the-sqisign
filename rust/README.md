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
| `hd`: theta structures, gluing, (2,2)-isogeny chains, splitting | ported; differential tests against C `ref` for all three primes, Kani chains for two |
| verification (`verify`, encodings, SHAKE256) | ported; all 300 NIST KAT signatures verify, differential tests against C `ref` for all three primes, including every single-bit signature flip |
| `mp`: integers, arithmetic, bounds, bit queries (`mp.c` core) | ported; differential tests against C `ref` for all three capacities |
| `mp`: division, reduction, square root (`mp.c` division section) | ported; differential tests against C `ref` for all three capacities |
| `mp`: gcd, extended gcd, inverses, CRT, Jacobi symbol, 2x2 inverse mod 2^e (`mp.c` gcd section) | ported; differential tests against C `ref` for all three capacities |
| `mp`: constant-time shifts, high products, masks, reciprocal, rounding (`mp.c` `ibz_ct_*`) | ported; differential tests against C `ref` for all three capacities |
| `mp`: uniform sampling (`mp.c` `ibz_rand_interval*`) and the domain-separated SHAKE256 PRNG (`prng.c`) | ported; differential tests against C `ref` seeded through the NIST CTR-DRBG, all three capacities |
| `mp`: primality (BPSW plus drawn Miller-Rabin rounds) and modular square roots (`mp.c` `ibz_probab_prime`, `ibz_sqrt_*`) | ported; differential tests against C `ref`, including PRNG draws, all three capacities |
| `quat`: 2- and 4-dimensional vectors and matrices, Gaussian integers, algebra elements, lattice membership, the standard order `O0` (`dim2.c`, `dim4.c`, `algebra.c`, `integers.c`, `lattice.c`, `stdorder.c`) | ported; differential tests against C `ref` for all three primes |
| `quat`: constant-time lattice reductions: 2-dimensional Lehmer and Gaussian reduction, sums of two squares, the dual 4-dimensional reduction of `O0` ideal products and the bounding parallelogram (`lll/`, `lll_applications.c`) | ported; differential tests against C `ref` for all three primes, on `O0` ideal products drawn by the C generators |
| `quat`: `O0` ideals: products, intersections, construction from generators, random ideals, integer representation, small equivalent ideals, ball sampling and the signing response element (`ideal.c`, `quat_represent_integer`, `quat_lattice_sample_from_ball`, `protocol.c`) | ported; differential tests against C `ref` for all three primes, including PRNG draws |
| `quat`: the norm equation solver (`qlapoty.c`) | ported; differential tests against C `ref` for all three primes, including PRNG draws |
| `id2iso`: the action of `O0` on `E0[2^f]`, kernels to ideals, changes of basis, the dimension-2 ideal-to-isogeny translation (`id2iso.c`, `dim2id2iso.c`) | ported; differential tests against C `ref` for all three primes, including PRNG draws |
| key generation, signing, secret-key encoding (`keygen.c`, `sign.c`, `encode_secret.c`, `sqisign.c`) | ported; byte-exact key pairs and signatures for all 300 NIST KAT entries |
| C ABI (`sqisign-capi`) + Kotlin backend (`sqisign.backend=rust`) | ported; the `sqisign_kmp_*` functions of `kotlin/native/include/sqisign_kmp.h`, KAT entries byte-exact through the ABI, the Kotlin test suites pass on the Rust backend |

## Build and test

Requirements: rustup (it installs the toolchain pinned in `rust-toolchain.toml`) and a C compiler with `__int128`
(Clang or GCC) for the C reference used by the tests.

```sh
cd rust
cargo test --release
cargo llvm-cov -p sqisign -p sqisign-capi --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100
```

The signing tests are slow in debug builds; `--release` runs the suite in minutes.

The `sqisign-reference` crate compiles the vendor C code from `../src` with the `ref` build type, so the tests compare
the port with the C implementation in the same checkout.

## Use it from the Kotlin project

The Kotlin project uses the C backend by default. Pass `-Psqisign.backend=rust` to build its native libraries from
the `sqisign-capi` crate instead (`kotlin/native/rust.cmake`), for example from `kotlin/`:

```sh
./gradlew jvmTest -Psqisign.backend=rust
```

CMake builds the crate for the Rust target of each native build, so add the targets once with `rustup target add`:
`aarch64-linux-android x86_64-linux-android` (Android), `aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-darwin`
(Apple). Desktop JNI builds use the host target; a MinGW build on a host whose default Rust target is MSVC also needs
`x86_64-pc-windows-gnu` and `-Psqisign.hostCMakeArgs=-DSQISIGN_KMP_RUST_TARGET=x86_64-pc-windows-gnu`.

## License

Apache-2.0, like the vendor implementation.
