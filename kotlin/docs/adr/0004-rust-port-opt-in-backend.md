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
   `src/gf/sat64/<set>/include/fp_generic_params.h`. The few curve constants verification needs (`BASIS_E0_PX`,
   `BASIS_E0_QX`, `p_cofactor_for_2f`) are source literals copied from the vendor `src/precomp/ref/<set>` SAT64
   tables and pinned by a differential test against the C globals. The large signing tables (quaternion orders,
   endomorphism and torsion data) are converted by a generator with a `--check` mode, never edited by hand (E6).
3. The port reproduces the C code as the Kotlin project builds it, `CMAKE_BUILD_TYPE=Release`, which defines `NDEBUG`:
   checks that exist only in `assert` or `#ifndef NDEBUG` blocks are not ported, and functions whose only failure
   path is such a check (for example `ec_curve_to_basis_2f_from_hint`) are infallible in Rust. Results C returns
   through a status flag and an out-parameter become `Option`; the C out-parameter on failure is not reproduced, as
   every C caller aborts on it. Where a C function reports failure but its caller continues on uninitialised data
   (in `hd`, `theta_chain_compute_and_eval*` when `gluing_start_chain` fails, and for chain lengths `n < 2`), the
   Rust function returns `None` instead of reproducing the undefined behaviour.
   Verification keeps the C acceptance rules: a non-canonical half of an encoded GF(p^2) element decodes to 0 (C
   `fp2_from_bytes` ignores its decode mask), and the top two bits of a response matrix entry are malleable for some
   signatures, as in C, because SQIsign is not strongly unforgeable (upstream SQISign/the-sqisign#18). Unlike C, the
   Rust API also rejects a public key of the wrong length.
4. Correctness evidence, in this order:
   - differential tests of every ported module against the vendor C `ref` build, compiled from `src/` into a test-only
     crate (`sqisign-reference`) and compared through the byte encodings (integers: bound and significant limbs);
   - the 300 NIST KAT entries in `KAT/`: verification of every signature, and byte-exact key generation and signing
     with the NIST AES-256 CTR-DRBG in test-only code (the production API takes no seed, as in ADR 0002);
   - malformed, truncated and boundary inputs for every decoder, and every single-bit flip of a KAT signature (T10);
   - a targeted mutation check of the decision points of every ported module. Survivors are either killed by a new
     test or recorded with the reason they are not: equivalent mutants (for example a different split of the doubling
     work in `hd` chain set-up, comparing challenge words that are always zero in verification, or the `mp`
     multiplication carry guard, whose bound only has to keep writes inside the limb array, or Lehmer step bounds in
     `mp` gcd, which only change how Euclid's steps are batched), or checks the tests
     cannot reach but that mirror the C code (for example the `tt2.t` half of the `hd` gluing check, or rejecting the
     singular public keys `A = ±2`, which fail later in verification anyway).

   Coverage (T3) is gated at 100% lines, functions and regions with `cargo llvm-cov` on the pinned stable toolchain.
   Rust branch coverage needs a nightly compiler; regions count both arms of every condition and stand in for it.
5. The Kotlin project selects the backend at build time with the Gradle property `sqisign.backend` (`c` default,
   `rust` opt-in). The Rust backend exports the same C ABI as `kotlin/native/include/sqisign_kmp.h`, so the JNI glue,
   cinterop definitions and the Kotlin API do not change.
6. The C backend stays the default and the only release backend until an expert review of the Rust port is recorded
   in a new version of this ADR.
7. SHAKE256 (Keccak-f[1600]) is ported in-crate rather than taken from a hashing crate, keeping the Rust core free of
   third-party dependencies (E3). It is tested against FIPS 202 vectors and against the vendor
   `src/common/generic/fips202.c`.
8. Integers (`mp`) keep the C `ibz_t` model instead of an arbitrary-precision crate: a two's-complement value over a
   fixed number of 64-bit limbs per parameter set (`IBZ_NLIMBS`: 30, 40, 55) with a bit bound that operations widen,
   wrapping at the capacity exactly like C. This keeps results, bounds and constant-time behaviour identical to the
   C code that signing is checked against. Where C has a variable-time and a constant-time function with identical
   results (`ibz_set_bound`/`ibz_set_bound_ct`, `ibz_bitsize`/`ibz_bitsize_ct`), Rust has one constant-time function,
   tested against both. Not ported: the `normalise` branch for `-2^(bitlen-1)`, which cannot execute; `ibz_swap`
   (Rust moves); and the string functions (`ibz_print`, `ibz_convert_to_str`, `ibz_size_in_base`,
   `ibz_set_from_str`), whose only non-debug use, parsing `QUAT_SQRT_P_HEX`, becomes a precomputed constant;
   and `ibz_mod_ui` and `ibz_pow`, which have no production callers. Division and square root (`ibz_div`,
   `ibz_sqrt_floor`) are variable-time in C too; Rust uses Knuth's algorithm D and integer Newton iteration instead
   of the C Burnikel-Ziegler and reciprocal methods. Both compute the same exact result, which the differential
   tests compare. The gcd functions (`ibz_gcd`, `ibz_xgcd`, `ibz_invmod`) use the same Lehmer
   double-digit algorithm as C, so the cofactors are identical; the C branches that cannot execute and the bound
   check on the smaller cofactor, which the larger one implies, are dropped. `ibz_legendre` loops forever in C when
   the operands share a factor; Rust returns 0, the Jacobi symbol. `ibz_invmat` truncates bounds in C; Rust
   reduces the entries modulo `2^max(e, 1)` first, which gives the same results. `ibz_invmod` and `ibz_invmat`
   return the value with a success flag, since callers use the value either way.

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
- The port also inherits C behaviour on inputs outside the documented domain, for example `pairing_dlog_2_tate`
  with `e = 1` can return logarithms that do not satisfy the relation. The differential tests pin this behaviour; the
  callers never use such inputs.
- Two backends double the platform test matrix while both exist.

## Migration

None for consumers: the C backend remains the default and the public API does not change. Switching the default to
Rust requires a new ADR version after the expert review, the full Kotlin test matrix on the Rust backend and a
benchmark comparison against the committed C baseline (P3/P4).
