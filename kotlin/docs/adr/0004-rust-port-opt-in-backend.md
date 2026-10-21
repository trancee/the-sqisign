# ADR 0004: Rust port of the SQIsign core as an opt-in backend

- Status: accepted
- Version: 1.0
- Date: 2026-10-21
- Constitution: O3 (crypto), S6 (approved implementation), S7/T10 (conformance vectors), E3 (dependencies), C1 (same
  behavior on all platforms)
- Refines: [ADR 0001](0001-native-backed-kotlin-multiplatform-bridge.md) (C stays the default backend)

## Context

ADR 0001 keeps all cryptography in the vendor C code. Memory safety and maintenance of that code are the C code's. A
memory-safe implementation in Rust removes a class of bugs from the parsers and the signing path, and Rust builds for
every target of the Kotlin project (JVM hosts, Android ABIs, Apple targets) with one toolchain.

A new implementation of a cryptographic scheme needs a specification, expert review and conformance vectors (S6). Until
an expert review has accepted the Rust code, it must not replace the reviewed C code in a release.

## Decision

1. Port the vendor C (NIST round 3, primes `3·2^324 − 1`, `27·2^500 − 1`, `17·2^664 − 1`) module by module to Rust in
   the top-level `rust/` Cargo workspace: `gf`, `ec`, `hd`, verification first, then `mp`, `quaternion`, `id2iso`,
   signing. Keys and signatures are byte-compatible with the C code.
2. Field arithmetic follows the C `sat64` backend (the one the `arm64` build runs): Montgomery form over saturated
   64-bit limbs, partially reduced to `[0, 2^bits)`. The Montgomery constants (`R mod p`, `R^2 mod p`, `1/2`, `1/3`)
   are computed by `const fn`s from the prime shape `(N, C, T)` and unit-tested against the vendor
   `src/gf/sat64/<set>/include/fp_generic_params.h`. The precomputed curve and quaternion tables are converted from
   the vendor `src/precomp/ref/<set>` SAT64 tables by a generator with a `--check` mode, never edited by hand (E6).
3. Correctness evidence, in this order:
   - differential tests of every ported module against the vendor C `ref` build, compiled from `src/` into a test-only
     crate (`sqisign-reference`) and compared through the byte encodings;
   - the 300 NIST KAT entries in `KAT/`: verification of every signature, and byte-exact key generation and signing
     with the NIST AES-256 CTR-DRBG in test-only code (the production API takes no seed, as in ADR 0002);
   - malformed, truncated and boundary inputs for every decoder (T10).

   Coverage (T3) is gated at 100% lines, functions and regions with `cargo llvm-cov` on the pinned stable toolchain.
   Rust branch coverage needs a nightly compiler; regions count both arms of every condition and stand in for it.
4. The Kotlin project selects the backend at build time with the Gradle property `sqisign.backend` (`c` default,
   `rust` opt-in). The Rust backend exports the same C ABI as `kotlin/native/include/sqisign_kmp.h`, so the JNI glue,
   cinterop definitions and the Kotlin API do not change.
5. The C backend stays the default and the only release backend until an expert review of the Rust port is recorded
   in a new version of this ADR.

## Alternatives

- Depend on `anchorageoss/sqisign-rs`: not chosen. An external, unaudited implementation by a single author; the
  project wants an implementation it controls and can check against the vendor C module by module.
- Replace the C backend immediately: rejected, violates S6.
- Pure Kotlin implementation: rejected in ADR 0001 (performance, constant time on the JVM).

## Risks

- A port can reproduce test vectors and still differ from the C code on rare inputs. Differential tests on random and
  boundary inputs reduce this; they do not prove equivalence.
- Constant-time behavior of the Rust field code depends on the compiler. The verification and field paths use
  branch-free code; a measured timing analysis is part of the expert review.
- Signing is variable-time in the C code (lattice reduction); the port inherits that.
- Two backends double the platform test matrix while both exist.

## Migration

None for consumers: the C backend remains the default and the public API does not change. Switching the default to
Rust requires a new ADR version after the expert review, the full Kotlin test matrix on the Rust backend and a
benchmark comparison against the committed C baseline (P3/P4).
