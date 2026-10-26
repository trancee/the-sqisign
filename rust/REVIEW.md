# Expert review of the Rust port

This guide is for the expert review that [ADR 0004](../kotlin/docs/adr/0004-rust-port-opt-in-backend.md) (item 6,
Constitution S6) requires before the Rust port can become a release backend. It says what is under review, what is
claimed, where the evidence is and how to reproduce it. The design decisions and every known deviation from the C code
are in ADR 0004; this guide points to them instead of repeating them.

## What is asked

The reviewer is asked to accept or reject, with findings, these claims:

1. **Equivalence.** For valid inputs, key generation, signing and verification return the same bytes as the vendor C
   implementation (NIST round 3, `src/`) built with `CMAKE_BUILD_TYPE=Release` (`NDEBUG`), for the three parameter
   sets `P324_3`, `P500_27` and `P664_17`. Deviations are deliberate and listed in ADR 0004 items 3 and 8–15.
2. **Safety at the boundary.** Malformed or hostile public keys, signatures and secret keys never cause undefined
   behaviour: the `sqisign` crate has no `unsafe` code, and the C ABI turns panics into an error status.
3. **Constant time where C is constant time.** Field arithmetic (`gf`), curve and isogeny code on secret data (`ec`,
   `hd`), the `ibz_ct_*` layer and the lattice reductions (`quat/lll`) have no branches or memory indices that depend
   on secret data. Signing as a whole is variable-time, as in C (ADR 0004, Risks).
4. **Secret handling.** Seeds and secret-key buffers passed through the C ABI are cleared after use.

Out of scope: the SQIsign scheme and its security proofs, the vendor C code itself, and the Kotlin and JNI glue, which
is shared with the reviewed C backend.

## What is under review

| Path | Lines | Content |
|---|---|---|
| `crates/sqisign/src/gf` | 1,344 | GF(p), GF(p^2) in Montgomery form (C `sat64` backend) |
| `crates/sqisign/src/ec` | 1,802 | points, ladders, isogenies, bases, pairings |
| `crates/sqisign/src/hd` | 998 | theta structures, (2,2)-isogeny chains |
| `crates/sqisign/src/verify`, `shake.rs` | 584 | verification, encodings, SHAKE256 |
| `crates/sqisign/src/mp` | 1,910 | bounded integers (C `ibz_t`), constant-time layer, primality |
| `crates/sqisign/src/quat` | 3,715 | quaternion algebra, `O0` ideals, lattice reduction, norm equation |
| `crates/sqisign/src/id2iso` | 1,733 | ideal-to-isogeny translation |
| `crates/sqisign/src/sign`, `prng.rs` | 554 | key generation, signing, secret-key encoding, SHAKE256 PRNG |
| `crates/sqisign-capi/src/lib.rs` | 222 | `sqisign_kmp_*` C ABI, panic boundary, buffer clearing (the only `unsafe` shipped) |

The production crates have no third-party dependencies (`sqisign` none, `sqisign-capi` only `sqisign`). The toolchain
is pinned in [`rust-toolchain.toml`](rust-toolchain.toml). Large precomputed tables are generated from the vendor
`src/precomp` data, with a `--check` mode (ADR 0004 item 2).

## Suggested order

1. `crates/sqisign-capi/src/lib.rs`: pointer and length checks, `catch_unwind`, `write_volatile` clearing.
2. Decoders in `verify` and `sign` (public key, signature, secret key): the inputs an attacker controls.
3. `gf` and the constant-time parts of `ec`, `hd`, `mp` (`ct.rs`) and `quat/lll`: claim 3.
4. `sign`, `id2iso`, `quat`, `mp`: claim 1, using the deviation list in ADR 0004 items 8–15.

## Evidence

| Claim | Evidence | Where |
|---|---|---|
| 1 | Byte-exact key pairs and signatures for all 300 NIST KAT entries, seeded with the NIST CTR-DRBG (optimised builds, as in CI job `Test`; the unoptimised coverage build checks the first 100, 20 and 10) | `crates/sqisign/tests/sign.rs`, `../KAT/` |
| 1 | Differential tests of every ported module against the vendor C `ref` build, on boundary and seeded random inputs, for all three parameter sets | `crates/sqisign/tests/*.rs`, `crates/sqisign-reference` |
| 1 | Targeted mutation checks of every module's decision points; survivors recorded with reasons | ADR 0004 item 4 |
| 1, 2 | 100 % line, function and region coverage of `crates/sqisign` | CI job `Format, lint, coverage` |
| 2 | Every single-bit flip of KAT signatures, wrong keys and curves, truncated and malformed inputs | `crates/sqisign/tests/verify_reject.rs`, `crates/sqisign-capi/tests/capi.rs` |
| 2 | `#![forbid(unsafe_code)]` | `crates/sqisign/src/lib.rs` |
| 2 | The C ABI's `unsafe` code interpreted by Miri on a pinned nightly: size queries, invalid sets, length-checked reads in verification, a failing `randombytes` (seed dropped on the error path) and clearing; run locally, also a full KAT verification through raw pointers and a panic unwinding through the seed's drop to an error (see Gaps) | `crates/sqisign-capi/tests/miri.rs`, CI job `Miri (C ABI)` |
| 1, 2 | The Kotlin test suites pass on the Rust backend: JVM on Linux, macOS and Windows, macOS, iOS Simulator, Android emulator and an `arm64-v8a` phone | `.github/workflows/kotlin.yml` job `rust-backend`, [`README.md`](README.md#status) |
| 3 | Optimisation barriers on the constant-time masks, as C's `ct_barrier_*`; the shipped build (fat LTO, one codegen unit) has no conditional branch in the gated constant-time code of `gf`, `mp` and `quat/lll`, checked by disassembly on `aarch64` (macOS and Android), `x86_64`, `armv7` and `i686`; the AArch64 select optimisation is off | [`scripts/ct_branches.py`](scripts/ct_branches.py), CI job `Constant-time branches`, [`.cargo/config.toml`](.cargo/config.toml) |
| 3 | dudect-style timing test (Welch's t, fixed against random inputs) of `gf` and the `ibz_ct_*` layer, with the vendor C field code as baseline, run on an Apple M1 and on 37 Arm and Qualcomm Kryo cores of 20 Android devices (see Gaps) | `crates/sqisign/examples/dudect.rs` |

## Reproducing

From `rust/`, with a C11 compiler that has `__int128` (Clang or GCC) for the reference crate:

```sh
cargo test --release                # KATs and differential tests
cargo clippy --all-targets -- -D warnings
cargo llvm-cov -p sqisign --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100
cargo run --release -p sqisign --example timing -- p324 all 20
MIRIFLAGS=-Zmiri-disable-isolation cargo +nightly-2026-10-23 miri test -p sqisign-capi --test miri  # --include-ignored: 1-1.5 h per ignored test
python3 scripts/ct_branches.py      # add --target aarch64-linux-android, x86_64-unknown-linux-gnu, armv7-linux-androideabi, i686-linux-android
CARGO_PROFILE_RELEASE_LTO=fat CARGO_PROFILE_RELEASE_CODEGEN_UNITS=1 \
  cargo run --release -p sqisign --example dudect -- 100000
```

From `kotlin/`, `./gradlew jvmTest -Psqisign.backend=rust` runs the Kotlin suites against the Rust backend.

## Gaps known before review

- **Timing: signals in the field operations, shared with C.** The dudect example (100,000 samples per operation and
  parameter set, shipped profile) ran on an Apple M1 (macOS; also with `PSTATE.DIT` set, field tests only) and on
  Android devices, each run pinned to one core:

  | Device (SoC) | Android | Cores measured (`arm64-v8a` unless noted) |
  |---|---|---|
  | Nothing Phone (1) (SM7325) | 15 | Cortex-A78, Cortex-A55; `armeabi-v7a` on the A78 |
  | Nothing Phone (2) (SM8475) | 16 | Cortex-A710, Cortex-A510, each also with `PSTATE.DIT` set |
  | Samsung Galaxy S10e (SM8150) | 12 | Kryo 485 Gold, Kryo 485 Silver |
  | Xiaomi POCO F1 (SDM845) | 10 | Kryo 385 Gold, Kryo 385 Silver |
  | Xiaomi Mi Note 3 (SDM660) | 9 | Kryo 260 Gold, Kryo 260 Silver |
  | realme RMX3710 (Helio G88) | 15 | Cortex-A75 |
  | OPPO CPH2385 (Helio G35) | 14 | Cortex-A53 (in order) |
  | Samsung Galaxy XCover 4 (Exynos 7570), 32-bit only | 9 | Cortex-A53, `armeabi-v7a` |
  | Xiaomi TV box (Amlogic S905X5M), 32-bit only | 14 | Cortex-A55, `armeabi-v7a` |
  | Google Pixel 6 (Tensor gs101) | 17 | Cortex-X1, Cortex-A76, Cortex-A55 |
  | OnePlus Nord 2 (Dimensity 1200) | 13 | Cortex-A78, Cortex-A55 |
  | Xiaomi POCO M4 Pro (Helio G96) | 13 | Cortex-A76, Cortex-A55 |
  | Xiaomi Redmi 12 (Helio G88) | 13 | Cortex-A75, Cortex-A55 |
  | realme C21Y (Unisoc T610) | 11 | Cortex-A75, Cortex-A55 |
  | OnePlus 6 (SDM845) | 11 | Kryo 385 Gold, Kryo 385 Silver |
  | Xiaomi Redmi Note 11 (SM6225) | 13 | Kryo 265 Gold (Cortex-A73), Kryo 265 Silver |
  | Huawei P30 lite (Kirin 710) | 10 | Cortex-A73, Cortex-A53 |
  | OPPO A53s (SM4250) | 12 | Kryo 240 Gold, Kryo 240 Silver |
  | Xiaomi Mi 8 (SDM845) | 10 | Kryo 385 Gold, Kryo 385 Silver |
  | Xiaomi POCO M5 (Helio G99) | 14 | Cortex-A76, Cortex-A55 |

  The eight devices from the Pixel 6 to the P30 lite ("the last eight") and the last three ran the build after the
  AArch64 select optimisation was turned off (see Findings); the Nothing Phone (2) Cortex-A710 was rerun with it.

  On 64-bit targets the example repeats the field tests on the vendor C `ref` build as a platform baseline. The `null`
  control (random against random) stayed below 4.5 everywhere (largest 2.56), so the statistics are sound, except on
  the Nord 2 Cortex-A78 (below).
  - `ibz_ct_*` (`ct_lt_mask`, `ct_highmul`, `ct_shift` with secret value or amount, `cswap`) and `fp select`: no
    uncropped signal on the first nine devices (largest 4.35, Kryo 485 Silver; elsewhere below 4.0); cropped-only
    flags of at most 57.0 (A55 `fp select`). On the last eight, single runs flagged `p664 ibz cswap` (13.1, Redmi 12
    A75; three reruns below 1.4) and `p664 fp select` (14.4, realme C21Y A75). The latter repeats with either sign
    (−66, +89, −54), only at a batch size of 924, also on a build from before the carry-chain kernels, and not on the
    Redmi 12 A75: a layout artefact. Nord 2 A55 `p500 ct_lt_mask` repeats in most runs on two builds (uncropped up
    to 16, the fixed class slower), weakens from two fixed values to 1,024 (4.0) and is absent from `p324`
    and `p664`, which run the same generic code on the same five limbs and differ only in the size of `Ibz` (248,
    328, 448 bytes), and from the nine other A55 and A53 cores; its cause is not established. On the last three,
    everything stays below 3.7 except POCO M5 A76 `p664 ibz ct_highmul`, which repeats (−8.7, then −4.3, −6.8, −2.4;
    the fixed class faster); a build of the same source with thin LTO and 16 codegen units gave at most 1.8 in three
    runs, so it follows code layout, as on the Nord 2 A78 (below).
  - Multiplying by zero (class 0 zero) is faster, in Rust and C alike, on the A55 (Rust t = −26 to −49, C −44 to
    −49), the A510, A53, A75 and every Kryo core except the Kryo 485 Gold (largest −138 for Rust and −166 for C, Kryo
    260 Gold); `fp2 inv` and `sqrt`, whose fixed inputs contain zero, inherit it (up to −235, A75). The last eight
    devices show the same: largest −833 for Rust and −937 for C on the realme C21Y A75, −352 and −339 on the Redmi 12
    A75, −262 and −240 on the Kryo 385 Gold, −230 and −224 on the P30 lite A73, −39 to −108 and −33 to −150 on the
    A55, A53 and Silver cores; `fp2 inv` and `sqrt` up to −1,728 (realme C21Y A75). The Nothing Phone (1) A78, A710,
    Kryo 485 Gold, X1, both A76 and both 32-bit runs on the A53 and A55 show no such signal (|t| below 4.5); the Nord
    2 A78 gives −21 for Rust and −20 for C. The last three: −23 to −141 for Rust and −24 to −119 for C on the Kryo
    240, Kryo 385 and A55 cores, `fp2 inv` and `sqrt` up to −163 (Kryo 240 Gold); none on the POCO M5 A76 (Rust −2.8,
    C −3.5).
  - Dense inputs (class 0 one of two fixed dense values): on the M1 (with or without DIT), Rust and C both show
    uncropped signals of the same size (largest 46.3 for Rust and 49.8 for C). On the Android cores they are small
    and scattered over add, subtract and multiply for both implementations (Rust up to 8.1, C up to 11.1; the A710
    flags only C `fp mul dense`). One Rust `p664 fp sub dense` run on the Kryo 485 Gold gave −32.0 and did not repeat:
    three reruns on another core of the same cluster gave +4.7, +5.8 and +6.1, with C `p500 fp sub dense` up to 5.4.
    On the last eight devices: Rust up to 18.7 and C up to 21.4 (realme C21Y A55 and A75), except on the Nord 2 A78.
    On the last three: Rust up to 11.8 (A55) and C up to 15.4 (A76), both on the POCO M5.
  - The Nord 2 Cortex-A78 is not a usable platform at this sample size. Its first run flagged Rust `p324 ibz
    ct_highmul` (45; reruns 66, 42, 5.8) and dense subtract (34, C 8). On a rebuild of the example with the same
    inputs, `ct_highmul` gave uncropped |t| at most 2.1 for two to 256 fixed values and four seeds, so the first
    build's signal follows code layout, not the values. A null variant (both classes drawn from the same random pool) flagged up to
    12.4 uncropped for Rust and 11.6 for C dense add and subtract, and C reached 16.7 against Rust's 28.1 with two
    fixed values.
  - Add and subtract with a zero operand: Rust `p324 fp sub` on the POCO M4 Pro A76 gives −1.2 to −13.5 over ten
    runs (nine beyond −4), C +0.2 to +9.4 with the opposite sign; one Rust `p664 fp add` of −7.8 on the Pixel 6 A76
    did not repeat (three reruns below 1). On the last three, Rust stays below 3.9 and C below 4.3.
  - `PSTATE.DIT` is advertised only by the SM8475 (A710, A510); the DIT runs used a local copy of the example that sets
    it first. With it set, the zero-multiply signal on the A510 disappears for Rust and C (largest uncropped |t| 3.3 for
    Rust, 4.8 for C dense subtract); on the A710 the C dense-multiply signal stays (−11.1). On the M1, DIT did not
    remove the field signals.
  - Cropped at the 95th percentile, most field tests flag on every core for both implementations, with either sign;
    the standard-deviation ratio of the classes printed next to it is mostly near 1.

  The field code has no secret-dependent branches (gated, below), and the vendor C, built branch-free from the same
  formulas, shows the same signals. On the A510, setting DIT removes them, which points to operand-dependent timing in
  the core; elsewhere their cause is not established. They are a property of the platforms that the port shares with C,
  not a regression against it. The library does not set DIT (it would need `unsafe` assembly, and the M1 and A710 show
  signals that DIT does not remove); a caller on a DIT-capable core can set it per thread. The example measures the
  field and `ibz_ct_*` operations, not the `ec`, `hd` and `quat/lll` code built on them, and was not run on `x86_64` or
  `i686`. Absence of a signal is not proof of constant time.
- **Branch check limited to gated code and branches.** `scripts/ct_branches.py` checks the files and functions listed in
  its `GATED` table on `aarch64`, `x86_64`, `armv7` and `i686` (the four Android ABIs and macOS `aarch64`; CI runs
  all five). A branch in inlined standard library arithmetic counts for the gated line it was inlined into; other
  inlined standard library code (iterators, slices) is taken as loop control over public bounds. On `armv7` the
  limb loop of `Ibz::cswap` stays rolled, and its back-edge, a backward branch on the byte offset against the constant
  length, is attributed to the loop body; the script accepts backward branches there and only on that target
  (`BACK_EDGES`), after inspection. Memory indices are not checked. `ec` and `hd` are not gated themselves: their
  secret-dependent choices go through `Fp::select` and `Fp::cswap`, which are. `black_box` is the barrier because the
  crate forbids `unsafe` (C uses `volatile` or inline assembly); it is best effort, so the check runs in CI on every
  change. The barriers cost about 6–10 % in key generation, signing and verification (`timing` example, `P324_3`, Apple
  M1).
- **Clearing is best effort.** The core clears what C clears (the PRNG seed and SHAKE256 domain streams, the
  secret-key encoding temporaries) with a store followed by `black_box`, not volatile writes. Like C, it does not
  clear the intermediate ideals, quaternions and points of key generation and signing.
- **Miri covers selected executions.** Successful key generation and signing, and so the copy-out of keys and
  signatures, take too long to interpret and were not run under Miri. CI interprets only the fast tests; the full KAT
  verification (51 minutes on an Apple M1) and the panic test (85 minutes) passed in a local run. The vendor
  `randombytes` and the JNI and cinterop glue are C, outside Miri; isolation is disabled to read a KAT entry; only the
  host target (`aarch64-apple-darwin` locally, `x86_64-unknown-linux-gnu` in CI) was interpreted.
- **Panics on undefined C behaviour.** Where C divides by zero or overflows a buffer under `NDEBUG`, Rust panics; the
  C ABI reports an error. Such cases were seen only far from signing-path sizes (ADR 0004, Risks).
- **Performance.** The port is 1.1–1.7× slower than the shipped C `arm64` build ([`README.md`](README.md#performance)),
  so the 10 % budget (P4) blocks a default switch independently of this review.

## Findings of the preliminary reviews

An independent agent and three language models (Gemini, Grok, GPT-6) reviewed this package before the expert review.
They are not the S6 review. Their findings and the resolutions on this branch:

| Finding | Resolution |
|---|---|
| The SHAKE256 PRNG domain streams were not cleared; the seed is recoverable from them | Sponge states clear themselves on drop, as C `prng_domain_clear` |
| The C ABI copied the seed (`[u8; 48]` is `Copy`) before clearing its own buffer | Seeds are lent by reference down to `Prng::new` |
| `sqisign_kmp_secure_clear(NULL, len)` looped `len` times | Returns at once, as the C glue; regression test |
| A hostile secret key could make signing loop for hours (negative response norm) | Signing fails at once on a negative norm (ADR 0004 item 15); regression test |
| KAT evidence wording: the coverage build checks only a prefix | Optimised builds check all 300 entries; wording corrected |
| Canonical-matrix entries were read with the top bit cleared; C reads them unsigned | Read unsigned; boundary test |
| One unresolved mutant in `partial_xgcd_lehmer` at the threshold | Killed by a threshold test against C (ADR 0004 item 4) |
| The shipped build branched on constant-time masks (`gf` add and subtract, `quat/lll` selection, more on `x86_64`) | Optimisation barriers as C `ct_barrier_*`; disassembly check in CI |
| Source-level branches in `ibz_extract_i64` and the 2x2 Gram step | Branch-free |
| dudect on the Cortex-A710 (after the carry-chain field kernels): field add and subtract faster on zero inputs, \|t\| up to 400. The AArch64 select optimisation branched on the carries for Android's generic tuning, attributed to `core` lines the branch check skipped | Select optimisation off for AArch64 ([`.cargo/config.toml`](.cargo/config.toml)); the check counts inlined `core` arithmetic for the gated line and runs on Android `arm64-v8a` |
| No measured timing analysis | dudect example with control and vendor C baseline, on an Apple M1 and 37 cores of 20 Android devices; results under Gaps |

## Recording the result

The outcome is recorded as a new version of ADR 0004: reviewer, date, commit reviewed, findings and their resolution,
and whether the Rust backend may be released. Switching the default further requires the conditions in ADR 0004,
Migration.
