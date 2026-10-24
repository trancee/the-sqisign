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
| `crates/sqisign/src/gf` | 1,242 | GF(p), GF(p^2) in Montgomery form (C `sat64` backend) |
| `crates/sqisign/src/ec` | 1,802 | points, ladders, isogenies, bases, pairings |
| `crates/sqisign/src/hd` | 998 | theta structures, (2,2)-isogeny chains |
| `crates/sqisign/src/verify`, `shake.rs` | 584 | verification, encodings, SHAKE256 |
| `crates/sqisign/src/mp` | 1,868 | bounded integers (C `ibz_t`), constant-time layer, primality |
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
| 3 | Optimisation barriers on the constant-time masks, as C's `ct_barrier_*`; the shipped build (fat LTO, one codegen unit) has no conditional branch in the gated constant-time code of `gf`, `mp` and `quat/lll`, checked by disassembly on `aarch64`, `x86_64`, `armv7` and `i686` | [`scripts/ct_branches.py`](scripts/ct_branches.py), CI job `Constant-time branches` |
| 3 | dudect-style timing test (Welch's t, fixed against random inputs) of `gf` and the `ibz_ct_*` layer, with the vendor C field code as baseline, run on an Apple M1 and on a Cortex-A78 and Cortex-A55 Android phone (see Gaps) | `crates/sqisign/examples/dudect.rs` |

## Reproducing

From `rust/`, with a C11 compiler that has `__int128` (Clang or GCC) for the reference crate:

```sh
cargo test --release                # KATs and differential tests
cargo clippy --all-targets -- -D warnings
cargo llvm-cov -p sqisign --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100
cargo run --release -p sqisign --example timing -- p324 all 20
MIRIFLAGS=-Zmiri-disable-isolation cargo +nightly-2026-10-23 miri test -p sqisign-capi --test miri  # --include-ignored: 1-1.5 h per ignored test
python3 scripts/ct_branches.py      # add --target x86_64-unknown-linux-gnu, armv7-linux-androideabi, i686-linux-android
CARGO_PROFILE_RELEASE_LTO=fat CARGO_PROFILE_RELEASE_CODEGEN_UNITS=1 \
  cargo run --release -p sqisign --example dudect -- 100000
```

From `kotlin/`, `./gradlew jvmTest -Psqisign.backend=rust` runs the Kotlin suites against the Rust backend.

## Gaps known before review

- **Timing: signals in the field operations, shared with C.** The dudect example (100,000 samples per operation and
  parameter set, shipped profile) ran on an Apple M1 (macOS; also with `PSTATE.DIT` set, field tests only) and on a
  Nothing Phone (1) (Android 15): `arm64-v8a` pinned to a Cortex-A78 and to a Cortex-A55, `armeabi-v7a` on the A78. On
  64-bit targets it repeats the field tests on the vendor C `ref` build as a platform baseline. The `null` control
  (random against random) stayed below 4.5 everywhere (largest 2.27), so the statistics are sound.
  - `ibz_ct_*` (`ct_lt_mask`, `ct_highmul`, `ct_shift` with secret value or amount, `cswap`) and `fp select`: no
    uncropped signal on any core (largest 1.95); cropped-only flags of at most 57.0 (A55 `fp select`).
  - `fp add`, `sub`, `mul` (class 0 zero, or one of two fixed dense values): on the A78, every uncropped |t| is below
    4.5 for Rust and C, in 64-bit and 32-bit. On the M1 (with or without DIT) and the A55, Rust and C both show
    uncropped signals of the same size (largest 46.3 for Rust and 49.8 for C, both on the M1; on the A55, dense add
    and subtract up to 7.4 for Rust and 11.1 for C). On the A55, multiplying by zero is faster (Rust t = −26 to −49,
    C −44 to −49), and `fp2 inv` and `sqrt`, whose fixed inputs contain zero, inherit it (up to −68). The A78 and A55
    do not advertise DIT.
  - Cropped at the 95th percentile, most field tests flag on every core for both implementations, with either sign;
    the standard-deviation ratio of the classes printed next to it is mostly near 1.

  The field code has no secret-dependent branches (gated, below), and the vendor C, built branch-free from the same
  formulas, shows the same signals; their cause (operand-dependent timing in the cores, beyond what DIT covers) is not
  established. They are a property of the platforms that the port shares with C, not a regression against it. The
  example measures the field and `ibz_ct_*` operations, not the `ec`, `hd` and `quat/lll` code built on them, and was
  not run on `x86_64` or `i686`. Absence of a signal is not proof of constant time.
- **Branch check limited to gated code and branches.** `scripts/ct_branches.py` checks the files and functions listed in
  its `GATED` table on `aarch64`, `x86_64`, `armv7` and `i686` (the four Android ABIs; CI runs all four). On `armv7` the
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
- **Performance.** The port is 1.4–1.9× slower than the shipped C `arm64` build ([`README.md`](README.md#performance)),
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
| No measured timing analysis | dudect example with control and vendor C baseline, on an Apple M1 and an Android phone; results under Gaps |

## Recording the result

The outcome is recorded as a new version of ADR 0004: reviewer, date, commit reviewed, findings and their resolution,
and whether the Rust backend may be released. Switching the default further requires the conditions in ADR 0004,
Migration.
