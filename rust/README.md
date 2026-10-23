# SQIsign in Rust

A Rust port of the [SQIsign](https://sqisign.org) round-3 reference implementation in this repository's `src/`. It is
built to be byte-compatible with the C code and checked module by module against it. The plan, the evidence required
and the reasons C stays the default backend are in
[ADR 0004](../kotlin/docs/adr/0004-rust-port-opt-in-backend.md).

> [!WARNING]
> Work in progress, not reviewed. Do not use it to protect real data. The scope, claims and evidence for the required
> expert review are in [`REVIEW.md`](REVIEW.md).

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
| C ABI (`sqisign-capi`) + Kotlin backend (`sqisign.backend=rust`) | ported; the `sqisign_kmp_*` functions of `kotlin/native/include/sqisign_kmp.h`, KAT entries byte-exact through the ABI, the Kotlin test suites pass on the Rust backend (JVM on Linux, macOS and Windows, macOS, iOS Simulator, an `x86_64` Android emulator in CI that also runs the `arm64-v8a` libraries through ARM translation, and an `arm64-v8a` Android phone including the seeded KATs) |

## Performance

The field kernels are unrolled per prime and the primality tests size their residues to the modulus, but the port stays
in safe Rust without assembly: it is 0.90–1.27× the time of the C `ref` build it follows, and 1.41–1.71× that of the C
`arm64` build that the Kotlin project ships for that architecture. Measured with the Kotlin JMH benchmarks (`./gradlew
jvmBenchmarkBenchmark`, `main` profile; mean ms/op, the three backends run back to back) on an Apple M1 under heavy
background load, so compare the ratios, not the absolute values with the [reference
baseline](../kotlin/benchmarks/README.md):

| Parameter set | Operation | C `arm64` | C `ref` | Rust | Rust / C `ref` |
|---|---|---|---|---|---|
| `P324_3` | Key generation | 41.9 | 54.6 | 69.5 | 1.27 |
| | Signing | 115 | 152 | 194 | 1.27 |
| | Verification | 15.2 | 22.3 | 23.5 | 1.05 |
| `P500_27` | Key generation | 145 | 219 | 226 | 1.03 |
| | Signing | 402 | 572 | 568 | 0.99 |
| | Verification | 38.1 | 72.8 | 65.3 | 0.90 |
| `P664_17` | Key generation | 258 | 372 | 440 | 1.18 |
| | Signing | 809 | 1104 | 1355 | 1.23 |
| | Verification | 90.4 | 134 | 149 | 1.12 |

Backends selected with `-Psqisign.backend=c|rust` and, for C, `-Psqisign.hostBuildType=arm64|ref`.

On the Nothing Phone (1) used for the [device benchmarks](../kotlin/benchmarks/README.md#device-benchmarks)
(`arm64-v8a`, Android 15, `cpuLocked: true`), `ANDROID_SERIAL=<serial> ./gradlew
:benchmark-android:connectedReleaseAndroidTest -Psqisign.backend=rust` gives these medians in ms, against the committed
C `arm64` results from the same phone:

| Parameter set | Operation | C `arm64` | Rust | Rust / C |
|---|---|---|---|---|
| `P324_3` | Key generation | 15.5 | 25.7 | 1.66 |
| | Signing | 45.4 | 71.8 | 1.58 |
| | Verification | 5.81 | 9.61 | 1.65 |
| `P500_27` | Key generation | 47.1 | 77.8 | 1.65 |
| | Signing | 151 | 242 | 1.60 |
| | Verification | 15.2 | 27.0 | 1.78 |
| `P664_17` | Key generation | 103 | 172 | 1.67 |
| | Signing | 348 | 532 | 1.53 |
| | Verification | 36.1 | 68.7 | 1.90 |

Both tables predate the constant-time barriers ([ADR 0004](../kotlin/docs/adr/0004-rust-port-opt-in-backend.md) item
16), which add about 6–10 % (`timing` example, `P324_3`, Apple M1).

Without Gradle, `cargo run --release -p sqisign --example timing -- p324 all 20` times key generation, signing and
verification with fixed seeds (median and minimum; parameter sets `p324`, `p500`, `p664`).

## Build and test

Requirements: rustup (it installs the toolchain pinned in `rust-toolchain.toml`) and a C compiler with `__int128`
(Clang or GCC) for the C reference used by the tests.

```sh
cd rust
cargo test --release
cargo llvm-cov -p sqisign -p sqisign-capi --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100
python3 scripts/ct_branches.py
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
(Apple), `x86_64-pc-windows-gnullvm` (Windows). Linux and macOS JNI builds use the host target. On Windows the JNI
library is built with MinGW clang, as for the C backend (see [`kotlin/README.md`](../kotlin/README.md)), and CMake pairs
it with `x86_64-pc-windows-gnullvm`, whose UCRT and LLVM runtime match MinGW clang; `rustup`'s default MSVC target
does not. Set `-DSQISIGN_KMP_RUST_TARGET=<triple>` in `-Psqisign.hostCMakeArgs` to override the derived target.

## License

Apache-2.0, like the vendor implementation.
