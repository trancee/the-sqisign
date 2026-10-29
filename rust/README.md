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
| C ABI (`sqisign-capi`) + Kotlin backend (`sqisign.backend=rust`) | ported; the `sqisign_kmp_*` functions of `kotlin/native/include/sqisign_kmp.h`, KAT entries byte-exact through the ABI, the Kotlin test suites pass on the Rust backend (JVM on Linux and macOS on `x86_64` and `aarch64` and on Windows, macOS, iOS Simulator, the Swift package on macOS and the iOS Simulator through the XCFramework, an `x86_64` Android emulator in CI that also runs the `arm64-v8a` libraries through ARM translation, and an `arm64-v8a` Android phone including the seeded KATs) |

## Performance

The field kernels are unrolled per prime, the primality tests size their residues to the modulus and integers are
stored at their parameter set's capacity (30, 40 or 55 limbs), but the port stays in safe Rust without assembly, with
the constant-time barriers ([ADR 0004](../kotlin/docs/adr/0004-rust-port-opt-in-backend.md) item 16): it is
0.61–0.95× the time of the C `ref` build it follows, and 1.05–1.27× that of the C `arm64` build that the Kotlin
project ships for that architecture. Measured with the Kotlin JMH benchmarks (`./gradlew jvmBenchmarkBenchmark`,
`main` profile; mean ms/op, the three backends run back to back) on an Apple M1, 2026-10-26, at the commit that
removed the subtraction borrow barrier; compare the ratios, not the absolute values with the [reference
baseline](../kotlin/benchmarks/README.md):

| Parameter set | Operation | C `arm64` | C `ref` | Rust | Rust / C `ref` | Rust / C `arm64` |
|---|---|---|---|---|---|---|
| `P324_3` | Key generation | 8.79 | 11.8 | 11.2 | 0.95 | 1.27 |
| | Signing | 25.9 | 33.9 | 31.6 | 0.93 | 1.22 |
| | Verification | 3.33 | 4.83 | 3.60 | 0.74 | 1.08 |
| `P500_27` | Key generation | 34.5 | 49.7 | 38.3 | 0.77 | 1.11 |
| | Signing | 95.2 | 135 | 109 | 0.81 | 1.14 |
| | Verification | 8.39 | 15.1 | 9.26 | 0.61 | 1.10 |
| `P664_17` | Key generation | 58.4 | 76.0 | 71.2 | 0.94 | 1.22 |
| | Signing | 183 | 230 | 218 | 0.95 | 1.19 |
| | Verification | 19.5 | 28.3 | 20.6 | 0.73 | 1.05 |

Backends selected with `-Psqisign.backend=c|rust` and, for C, `-Psqisign.hostBuildType=arm64|ref`.

A rerun on the same M1 on 2026-10-29 at `b846b09` (C `arm64` and Rust only, back to back) gave Rust / C `arm64` of
1.14, 1.18, 1.10 (`P324_3` key generation, signing, verification), 0.98, 1.00, 1.00 (`P500_27`) and 1.11, 1.07, 1.06
(`P664_17`). `python3 kotlin/benchmarks/compare.py` with the C run as baseline flags three of the nine over the 10 %
budget (P4): `P324_3` key generation and signing, and `P664_17` key generation.

On the Nothing Phone (1) used for the [device benchmarks](../kotlin/benchmarks/README.md#device-benchmarks)
(`arm64-v8a`, Android 15, `cpuLocked: true`), `ANDROID_SERIAL=<serial> ./gradlew
:benchmark-android:connectedReleaseAndroidTest -Psqisign.backend=c|rust` gives these medians in ms, both backends run
back to back on the same commit and day (the committed C results differ from this C run by at most 10 %, `P664_17`
signing):

| Parameter set | Operation | C `arm64` | Rust | Rust / C |
|---|---|---|---|---|
| `P324_3` | Key generation | 15.5 | 18.6 | 1.20 |
| | Signing | 45.0 | 52.4 | 1.16 |
| | Verification | 5.80 | 6.52 | 1.12 |
| `P500_27` | Key generation | 46.4 | 55.5 | 1.20 |
| | Signing | 145 | 159 | 1.10 |
| | Verification | 15.2 | 16.8 | 1.10 |
| `P664_17` | Key generation | 97.3 | 119 | 1.22 |
| | Signing | 314 | 346 | 1.10 |
| | Verification | 36.1 | 41.1 | 1.14 |

Before the optimisations below the port took 0.90–1.27× the time of C `ref` and 1.41–1.71× that of C `arm64` in the
JMH benchmarks, and 1.53–1.90× that of C `arm64` on the Nothing Phone (1), without the barriers, which add about
6–10 % (`timing` example, `P324_3`, Apple M1).

The carry-chain field kernels came first: limb additions, subtractions and the product rows of the Montgomery
multiplication use `u64::carrying_add`, which compiles to one `adcs` per limb as in the vendor assembly. On a Nothing
Phone (2) pinned to one Cortex-A710 core (`taskset 10`), the `timing` example and the vendor `benchmark_*` apps (C
`arm64`, Android NDK, same core) give these medians in ms over 10 runs.

The Montgomery multiplication then interleaves each product row with one word reduction, as the vendor `fp_mul` does,
so the accumulator stays at `N + 1` words instead of a `2N`-word product that LLVM spilled to the stack ("interleaved"
column). Finally, field addition and subtraction OR their reduction masks with one opaque zero instead of passing each
mask through `black_box`, which took a store and reload on the carry chain, and start subtraction with a plain `subs`
("add/sub" column). The Fp2 multiplication and squaring then multiply the unreduced sums `a0 + a1` (and `b0 + b1`), as
the vendor arm64 `fp2_mul` does; `check_shape` requires the spare bits that keep the product in range ("Fp2 sums"
column). Primality tests, 64 Miller-Rabin rounds for each prime found as in C, then took up to 31 % of signing (`P664_17`, Apple M1): their
Montgomery residues are now one word wider than the modulus, so the multiplication's loop bound is a constant that the
compiler unrolls, and exponentiation uses 4-bit windows ("Primality" column; verification tests no primes, so its column
differs from the previous one only by drift between sessions). Last, LLVM's machine scheduler moved every independent
product of a Montgomery multiplication ahead of its carry chains, which spilled them to the stack (228 stores for
`P664_17`). With the scheduler off for AArch64 (`.cargo/config.toml`) and each carry chain computing its own product
halves, the code keeps the order of the vendor assembly (11 stores; "No scheduler" column, last ratio against C). On a
Cortex-A55 core this saves 12–16 % for `P664_17` and up to 7 % for `P500_27`, and costs about 1 % for `P324_3`; on an
Apple M1 the times do not change:

| Parameter set | Operation | C `arm64` | Rust before | Carry chains | Interleaved | Add/sub | Fp2 sums | Primality | No scheduler | / C |
|---|---|---|---|---|---|---|---|---|---|---|
| `P324_3` | Key generation | 15.6 | 28.4 | 23.9 | 22.9 | 21.0 | 20.3 | 19.5 | 18.6 | 1.19 |
| | Signing | 45.1 | 84.5 | 73.1 | 70.4 | 65.1 | 63.1 | 59.4 | 57.5 | 1.27 |
| | Verification | 5.92 | 11.6 | 9.41 | 9.00 | 7.95 | 7.52 | 7.53 | 7.16 | 1.21 |
| `P500_27` | Key generation | 59.6 | 90.3 | 77.7 | 70.9 | 67.4 | 67.0 | 62.9 | 59.8 | 1.00 |
| | Signing | 155 | 309 | 274 | 258 | 248 | 246 | 223 | 215 | 1.39 |
| | Verification | 15.3 | 32.4 | 25.7 | 22.3 | 20.5 | 20.1 | 19.6 | 18.1 | 1.18 |
| `P664_17` | Key generation | 102 | 194 | 172 | 156 | 153 | 150 | 142 | 120 | 1.18 |
| | Signing | 293 | 607 | 551 | 508 | 498 | 492 | 442 | 385 | 1.31 |
| | Verification | 36.3 | 72.3 | 62.0 | 54.2 | 53.2 | 51.6 | 50.1 | 39.9 | 1.10 |

Key generation and signing vary between runs because they draw random values; verification varies by less than 1 %.

Integers (`Ibz`) have the full capacity of their parameter set (448 bytes for `P664_17`), and moving them by value
compiled to `memmove` calls that took 5.9 % of `P664_17` signing on an Apple M1. Sign changes, additions and
subtractions now work in place, division writes into output parameters, and zero values are built in place instead of
copied from a constant, which brings `memmove` down to 3.4 % and signing 2–3 % faster on the M1.

Fp2 squaring then multiplies `(a0 + a1) (a0 - a1 + 2p)` and `(a0 + a0) a1` without reducing either operand, as the
vendor arm64 `fp2_sqr` does, instead of reducing `a0 - a1` and the doubling of `a0 a1`; `check_shape`
bounds the wider operand. On the M1 this saves 1–4 % (`P500_27` signing 2.7 %, `P324_3` 1 %); on a Pixel 6 Cortex-X1
core (`taskset 80`) the times stay within its ±1.5 % thermal drift, although `Fp` addition and subtraction drop from
21 % to 15 % of `P324_3` cycles (`simpleperf`).

### Device fleet

The comparison of the Cortex-A710 table, with the changes above including the cheaper Lehmer quotient steps, on 27
`arm64-v8a` phones (rerun 2026-10-29 at `eacab85`, NDK 30):
the `timing` example and the vendor `benchmark_*` apps (C `arm64`, Android NDK, API 29, API 26 for Android 8.0), 2
rounds of 15 runs each, back to back and pinned with `taskset` to the fastest core the `adb` shell may use (by CPU part
where all cores report the same maximum frequency, as on the RMX3263; the P10 lite to its big cluster, because its
shell lost the single core mid-run). Some phones keep their prime core or cap its frequency for the shell, so absolute
times do not compare between phones; the table gives Rust / C of the minimum medians, phones roughly ordered by C
`P664_17` signing time, fastest first:

| Phone | SoC | Android | `P324_3` key generation | Signing | Verification | `P500_27` key generation | Signing | Verification | `P664_17` key generation | Signing | Verification |
|---|---|---|---|---|---|---|---|---|---|---|---|
| OPPO CPH2359 | Dimensity 1200 | 14 | 1.17 | 1.18 | 1.17 | 1.02 | 1.05 | 1.16 | 1.08 | 1.11 | 1.08 |
| OnePlus Nord 2 (DN2103) | Dimensity 1200 | 13 | 1.14 | 1.14 | 1.17 | 1.09 | 1.13 | 1.16 | 1.12 | 1.11 | 1.09 |
| Google Pixel 6 | Tensor G1 | 17 | 1.09 | 1.21 | 1.21 | 1.17 | 1.05 | 1.19 | 1.04 | 0.98 | 1.07 |
| Samsung Galaxy Z Flip4 (SM-F721B) | Snapdragon 8+ Gen 1 | 16 | 1.17 | 1.24 | 1.21 | 0.97 | 1.10 | 1.19 | 1.04 | 1.10 | 1.09 |
| Nothing Phone (1) (A063) | Snapdragon 778G+ | 15 | 1.15 | 1.17 | 1.16 | 1.22 | 1.11 | 1.16 | 1.13 | 1.13 | 1.08 |
| Nothing Phone (2) | Snapdragon 8+ Gen 1 | 16 | 1.15 | 1.22 | 1.21 | 1.14 | 1.09 | 1.19 | 1.13 | 1.08 | 1.09 |
| Huawei nova 9 (NAM-LX9) | Snapdragon 778G | 12 | 1.15 | 1.18 | 1.17 | 1.14 | 1.13 | 1.16 | 1.19 | 1.03 | 1.08 |
| Gigaset E940 | Dimensity 900 | 13 | 1.11 | 1.16 | 1.17 | 1.16 | 1.12 | 1.15 | 1.18 | 1.11 | 1.07 |
| OPPO CPH2251 | Dimensity 900 | 13 | 1.11 | 1.12 | 1.16 | 1.19 | 1.12 | 1.15 | 1.08 | 1.17 | 1.07 |
| motorola edge 30 fusion | Snapdragon 888+ | 14 | 1.16 | 1.17 | 1.16 | 1.15 | 1.08 | 1.15 | 1.08 | 1.04 | 1.07 |
| OnePlus 6 (A6003) | Snapdragon 845 | 11 | 1.10 | 1.11 | 1.05 | 0.97 | 1.05 | 1.14 | 1.07 | 1.06 | 0.97 |
| POCOPHONE F1 (throttles) | Snapdragon 845 | 10 | 1.07 | 1.10 | 1.08 | 1.14 | 1.11 | 1.15 | 1.05 | 1.18 | 1.17 |
| Samsung Galaxy S10e (SM-G970U1) | Snapdragon 855 | 12 | 1.02 | 1.08 | 1.04 | 1.01 | 1.10 | 1.10 | 1.03 | 1.06 | 1.00 |
| Google Pixel 4a | Snapdragon 730G | 13 | 1.04 | 1.06 | 1.04 | 1.07 | 1.16 | 1.10 | 1.03 | 1.07 | 1.00 |
| Xiaomi 22071219CG | Helio G99 | 14 | 1.03 | 1.04 | 1.04 | 1.03 | 1.13 | 1.11 | 1.04 | 1.03 | 1.00 |
| Xiaomi Mi 8 (throttles) | Snapdragon 845 | 10 | 1.07 | 1.06 | 1.15 | 1.14 | 1.14 | 1.15 | 1.07 | 1.12 | 1.03 |
| Huawei P30 lite (MAR-LX1A) | Kirin 710 | 10 | 1.12 | 1.14 | 1.11 | 1.17 | 1.16 | 1.17 | 1.09 | 1.10 | 1.04 |
| Xiaomi Mi Note 3 | Snapdragon 660 | 9 | 1.08 | 1.12 | 1.11 | 1.06 | 1.23 | 1.16 | 1.08 | 1.13 | 1.04 |
| Xiaomi 2201117PG | Helio G96 | 13 | 1.00 | 1.04 | 1.04 | 1.03 | 1.11 | 1.10 | 1.05 | 1.07 | 1.00 |
| Xiaomi 23053RN02A | Helio G88 | 13 | 1.07 | 1.10 | 1.08 | 1.14 | 1.18 | 1.15 | 1.08 | 1.10 | 1.03 |
| realme RMX3710 | Helio G88 | 15 | 1.09 | 1.10 | 1.08 | 1.09 | 1.16 | 1.15 | 1.08 | 1.12 | 1.03 |
| Nokia X20 | Snapdragon 480 | 14 | 1.03 | 1.05 | 1.03 | 1.03 | 1.14 | 1.10 | 1.03 | 1.07 | 1.00 |
| Xiaomi 2201117TY | Snapdragon 680 | 13 | 1.08 | 1.13 | 1.11 | 1.10 | 1.16 | 1.16 | 1.12 | 1.15 | 1.05 |
| realme RMX3263 | Unisoc T618 | 11 | 1.07 | 1.09 | 1.08 | 1.10 | 1.18 | 1.14 | 1.08 | 1.10 | 1.02 |
| OPPO CPH2385 | Helio G35 | 14 | 1.04 | 1.03 | 1.03 | 1.10 | 1.02 | 1.06 | 1.02 | 1.03 | 0.98 |
| Huawei P10 lite (WAS-LX1A) | Kirin 658 | 8.0 | 0.95 | 1.05 | 1.02 | 1.08 | 0.98 | 1.06 | 1.04 | 0.99 | 0.98 |
| OPPO CPH2135 | Snapdragon 460 | 12 | 1.01 | 1.07 | 1.03 | 1.04 | 1.10 | 1.06 | 1.01 | 1.02 | 0.98 |

Without the two phones marked "throttles", whose C verification times drift by up to 20 % between rounds, Rust takes
0.95–1.24× the time of C (median 1.09×, 1.11× before the cheaper Lehmer quotient steps); `P664_17` is closest
(signing 0.98–1.17×, verification 0.97–1.09×). Key generation and signing, which use the gcd and division, moved from
median 1.12× to 1.10× and from at most 1.34× to 1.24×; verification, which does not, stayed at median 1.09×. The ten
fastest phones show the widest gap (0.97–1.24×, median 1.14×), the others 0.95–1.23× (median 1.07×).

Without Gradle, `cargo run --release -p sqisign --example timing -- p324 all 20` times key generation, signing and
verification with fixed seeds (median and minimum; parameter sets `p324`, `p500`, `p664`).

## Build and test

Requirements: rustup (it installs the toolchain pinned in `rust-toolchain.toml`) and a C compiler with `__int128`
(Clang or GCC) for the C reference used by the tests.

```sh
cd rust
cargo test --release
scripts/coverage.sh
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
