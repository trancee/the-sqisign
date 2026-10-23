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
| 1 | Byte-exact key pairs and signatures for all 300 NIST KAT entries, seeded with the NIST CTR-DRBG | `crates/sqisign/tests/sign.rs`, `../KAT/` |
| 1 | Differential tests of every ported module against the vendor C `ref` build, on boundary and seeded random inputs, for all three parameter sets | `crates/sqisign/tests/*.rs`, `crates/sqisign-reference` |
| 1 | Targeted mutation checks of every module's decision points; survivors recorded with reasons, one unresolved | ADR 0004 item 4 |
| 1, 2 | 100 % line, function and region coverage of `crates/sqisign` | CI job `Format, lint, coverage` |
| 2 | Every single-bit flip of KAT signatures, wrong keys and curves, truncated and malformed inputs | `crates/sqisign/tests/verify_reject.rs`, `crates/sqisign-capi/tests/capi.rs` |
| 2 | `#![forbid(unsafe_code)]` | `crates/sqisign/src/lib.rs` |
| 1, 2 | The Kotlin test suites pass on the Rust backend: JVM on Linux, macOS and Windows, macOS, iOS Simulator, Android emulator and an `arm64-v8a` phone | `.github/workflows/kotlin.yml` job `rust-backend`, [`README.md`](README.md#status) |
| 3 | Code inspection only (see Gaps) | — |

## Reproducing

From `rust/`, with a C11 compiler that has `__int128` (Clang or GCC) for the reference crate:

```sh
cargo test --release                # KATs and differential tests (tens of minutes)
cargo clippy --all-targets -- -D warnings
cargo llvm-cov -p sqisign --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100
cargo run --release -p sqisign --example timing -- p324 all 20
```

From `kotlin/`, `./gradlew jvmTest -Psqisign.backend=rust` runs the Kotlin suites against the Rust backend.

## Gaps known before review

- **No measured timing analysis.** Claim 3 rests on inspection and on following the C control flow; ADR 0004 requires a
  measured analysis (for example dudect-style tests of the field and constant-time integer code on each target) as
  part of this review.
- **Compiler-dependent constant time.** Branch-free Rust source can still be compiled to branches; the generated code
  for `gf` and `mp/ct.rs` has not been inspected on every target.
- **Weaker clearing inside the core than in C.** The `sqisign` crate clears the PRNG root seed on drop, best effort
  without volatile writes (no `unsafe`). The derived SHAKE256 `Domain` streams are not cleared, while C clears them
  (`prng_domain_clear` in `keygen.c`, `sign.c`). Other intermediate secrets were not compared.
- **Panics on undefined C behaviour.** Where C divides by zero or overflows a buffer under `NDEBUG`, Rust panics; the
  C ABI reports an error. Such cases were seen only far from signing-path sizes (ADR 0004, Risks).
- **One unresolved mutant** in `partial_xgcd_lehmer` (ADR 0004 item 4).
- **Performance.** The port is 1.4–1.9× slower than the shipped C `arm64` build ([`README.md`](README.md#performance)),
  so the 10 % budget (P4) blocks a default switch independently of this review.

## Recording the result

The outcome is recorded as a new version of ADR 0004: reviewer, date, commit reviewed, findings and their resolution,
and whether the Rust backend may be released. Switching the default further requires the conditions in ADR 0004,
Migration.
