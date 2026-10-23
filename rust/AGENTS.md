# AGENTS: Rust port

Refines the root [`AGENTS.md`](../AGENTS.md) for `rust/`. [`CONSTITUTION.md`](../CONSTITUTION.md) wins on conflict.
Notation R/X/D/O as defined there.

## PROFILE

- Product: Rust port of the vendor SQIsign C core (NIST round 3), byte-compatible with it; planned opt-in Kotlin backend.
  Design and evidence rules: [ADR 0004](../kotlin/docs/adr/0004-rust-port-opt-in-backend.md). C stays the release
  backend until an expert review (S6).
- Toolchain: pinned in [`rust-toolchain.toml`](rust-toolchain.toml) (rustup installs it on first use); a C11 compiler
  with `__int128` (Clang/GCC; X MSVC) for the reference crate; `cargo-llvm-cov` for coverage.
- Layout:
  - `crates/sqisign`: the port. `src/gf`: GF(p)/GF(p^2) (sat64 representation, `const fn` constants). Ported modules
    follow the vendor names (`ec`, `hd`, `verification`, `mp`, `quaternion`, `id2iso`, `signature`).
  - `crates/sqisign-reference`: test-only (`publish = false`) bindings to the vendor C `ref` build, compiled from
    `../src` by `build.rs` with the `cc` crate; symbols `sqisign_<level>_ref_<name>`.
  - `crates/sqisign/tests`: differential tests per module and parameter set; `tests/support`: deterministic RNG.
- Vendor sources (`../src`, `../include`, `../KAT`) are read-only inputs (root SCOPE).

## STEPS (port a module)

1. Read the vendor `ref` sources of the module and, for `gf`, the `sat64` backend (`../src/gf/sat64`).
2. Red: expose the C functions through `sqisign-reference`, then write differential tests comparing encodings on
   boundary and seeded random inputs for every parameter set (T1). Raw-representation invariants unreachable through
   decoding => unit tests in the module (`*_tests.rs`).
3. Implement; keep constant-time code branch-free on secret data; predicates return the C masks (`0` / `0xFFFF_FFFF`)
   unless the C function takes another convention (e.g. `fp2_mul_by_i` takes `0`/`1`).
4. Gates (VERIFY). Evidence that tests can fail: mutate the new code once and see a red test; record the result in the
   commit message or PR.
5. Docs: [`README.md`](README.md), ADR 0004 agree with code (O1/O2).

## VERIFY

Run from `rust/`.

| Gate | Command | CI job |
|---|---|---|
| Format | `cargo fmt --all --check` | Rust |
| Lint | `cargo clippy --all-targets -- -D warnings` | Rust |
| Tests | `cargo test` | Rust |
| Coverage | `cargo llvm-cov -p sqisign --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100` | Rust |
| Constant-time branches | `python3 scripts/ct_branches.py` (and `--target x86_64-unknown-linux-gnu`); new constant-time code => add to its `GATED` | Rust |
| Secret scan | `gitleaks git --redact --exit-code 1 ..` | Secret scan |

Coverage scope: `crates/sqisign` only; `sqisign-reference` is test infrastructure. Branch coverage needs nightly, so
regions stand in for it (ADR 0004). CI workflow: [`../.github/workflows/rust.yml`](../.github/workflows/rust.yml).

## CONVENTIONS

- Commit scope `rust` (Conventional Commits, G6).
- `#![forbid(unsafe_code)]` in `sqisign`; `unsafe` only in `sqisign-reference` FFI wrappers, each with a `SAFETY:`
  comment.
- Workspace lints: `clippy::all` denied, `missing_docs` warned (and denied by `-D warnings`). X `#[allow]` in
  production code (Q2).
- Files <= 300 lines (D9): split by concern (`fp.rs` arithmetic, `fp_codec.rs` encoding, `fp_pow.rs` exponentiation).
- Parameter types: `P324` (`p324_3`, level I), `P500` (`p500_27`, level III), `P664` (`p664_17`, level V).
