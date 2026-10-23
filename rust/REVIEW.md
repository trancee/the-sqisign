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
| 1, 2 | The Kotlin test suites pass on the Rust backend: JVM on Linux, macOS and Windows, macOS, iOS Simulator, Android emulator and an `arm64-v8a` phone | `.github/workflows/kotlin.yml` job `rust-backend`, [`README.md`](README.md#status) |
| 3 | Optimisation barriers on the constant-time masks, as C's `ct_barrier_*`; the shipped build (fat LTO, one codegen unit) has no conditional branch in the gated constant-time code of `gf`, `mp` and `quat/lll`, checked by disassembly on `aarch64` and `x86_64` | [`scripts/ct_branches.py`](scripts/ct_branches.py), CI job `Constant-time branches` |
| 3 | dudect-style timing test (Welch's t, fixed against random inputs) of `gf` and the `ibz_ct_*` layer, run on an Apple M1 (see Gaps) | `crates/sqisign/examples/dudect.rs` |

## Reproducing

From `rust/`, with a C11 compiler that has `__int128` (Clang or GCC) for the reference crate:

```sh
cargo test --release                # KATs and differential tests
cargo clippy --all-targets -- -D warnings
cargo llvm-cov -p sqisign --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100
cargo run --release -p sqisign --example timing -- p324 all 20
python3 scripts/ct_branches.py      # add --target x86_64-unknown-linux-gnu for the other architecture
CARGO_PROFILE_RELEASE_LTO=fat CARGO_PROFILE_RELEASE_CODEGEN_UNITS=1 \
  cargo run --release -p sqisign --example dudect -- 100000
```

From `kotlin/`, `./gradlew jvmTest -Psqisign.backend=rust` runs the Kotlin suites against the Rust backend.

## Gaps known before review

- **Timing measured on one machine only.** The dudect example was run on an Apple M1 (macOS, shipped profile, three runs
  of 100,000 samples per operation and parameter set). Uncropped, every |t| stayed below 4.5 (largest 2.84, `fp sub`).
  Cropped at the 95th percentile, three operations exceeded it: `fp mul` on `P324_3` (class 0 = zero) in 2 of 3 runs
  (7.52, 7.00; the third 3.95), `fp add` on `P664_17` once (6.13), and `ibz ct_shift` with a secret shift amount in 3 of
  9 runs (largest 6.92). None of these paths branches or indexes memory on secret data: `gf/fp.rs` is gated (below), and
  the disassembly of `shift_words` (`mp/ct.rs`) branches only on the public length, word index and stage, the amount
  reaching only masks and register shift counts. The cause of these cropped-only signals is not known. On the M1, a
  single fixed input repeated through a batch ran measurably faster through branch-free code, so the harness alternates
  two fixed values (for the shift amount this halved the signal against a fixed amount of 0). The example was not run on
  `x86_64`, on an Android device or on 32-bit targets, and it measures the field and `ibz_ct_*` operations, not the
  `ec`, `hd` and `quat/lll` code built on them. Absence of a signal is not proof of constant time; the reviewer may want
  longer runs on the release targets.
- **Branch check limited to 64-bit targets and gated code.** `scripts/ct_branches.py` checks the files and functions
  listed in its `GATED` table on `aarch64` and `x86_64`; 32-bit Android ABIs (`armeabi-v7a`, `x86`) are not analysed,
  and memory indices are not checked. `ec` and `hd` are not gated themselves: their secret-dependent choices go
  through `Fp::select` and `Fp::cswap`, which are. `black_box` is the barrier because the crate forbids `unsafe`
  (C uses `volatile` or inline assembly); it is best effort, so the check runs in CI on every change. The barriers
  cost about 6–10 % in key generation, signing and verification (`timing` example, `P324_3`, Apple M1).
- **Clearing is best effort.** The core clears what C clears (the PRNG seed and SHAKE256 domain streams, the
  secret-key encoding temporaries) with a store followed by `black_box`, not volatile writes. Like C, it does not
  clear the intermediate ideals, quaternions and points of key generation and signing.
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
| No measured timing analysis | dudect example; results under Gaps |

## Recording the result

The outcome is recorded as a new version of ADR 0004: reviewer, date, commit reviewed, findings and their resolution,
and whether the Rust backend may be released. Switching the default further requires the conditions in ADR 0004,
Migration.
