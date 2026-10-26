# ADR 0004: Rust port of the SQIsign core as an opt-in backend

- Status: accepted
- Version: 1.1
- Date: 2026-10-23
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
     with the NIST AES-256 CTR-DRBG in test-only code (the production API takes no seed, as in ADR 0002); signing
     checks all 300 entries in optimised builds and the first 100, 20 and 10 in the unoptimised coverage build;
   - malformed, truncated and boundary inputs for every decoder, and every single-bit flip of a KAT signature (T10);
   - a targeted mutation check of the decision points of every ported module. Survivors are either killed by a new
     test or recorded with the reason they are not: equivalent mutants (for example a different split of the doubling
     work in `hd` chain set-up, comparing challenge words that are always zero in verification, or the `mp`
     multiplication carry guard, whose bound only has to keep writes inside the limb array, or Lehmer step bounds in
     `mp` gcd, which only change how Euclid's steps are batched, or the dividend `2^127 - 1` of the `mp`
     reciprocal seed, a prime, so it floors like `2^127 - 2` for every 64-bit divisor, or `mp` primality checks
     that only run after BPSW has decided, such as the drawn witness range or an extra Miller-Rabin squaring, or the
     `quat` matrix sign normalisation, whose comparison never meets its excluded midpoint, or the `qlapoty` swap of
     the Cornacchia pair, which its later parity normalisation undoes, or working bounds of `qlapoty` that only have
     to keep values inside the capacity), or
     checks the tests cannot reach but that mirror the C code (for example the `tt2.t` half of the `hd` gluing
     check, rejecting the singular public keys `A = ±2`, which fail later in verification anyway, or the `mp`
     Lucas-Selfridge search limit and shared-factor exit, which only a base-2 strong pseudoprime with every factor
     above the sieve bound could reach, or the `qlapoty` second-loop step and end conditions and the `z = 2`
     square-root case, which no sampled norm equation reached).
   - the `static` word-level steps of the C lattice reduction (`fast_log2_ratio`, `partial_gram_msb`,
     `partial_xgcd_msb`, `sizered_msb`, `partial_xgcd_lehmer`, `sizered_lehmer`, `LG_gaussian_inner`) are exported to
     the test crate only, by generated units that `#include` the unchanged vendor file, and compared with their Rust
     ports on inputs sized around every threshold; with property tests of `ct_shr_sat64` and `ct_isqrt_32` they
     killed 12 boundary survivors. The `quat` ideal, LLL, ideal-to-isogeny and signing survivors left are equivalent
     (an ideal of norm 1 is `O0` for any generator; `2x` never equals an odd norm; `p = 3 mod 4` fixes the residue of
     the sampled norm; `x == y` is impossible for an odd prime; ties of `ct_min`/`ct_max`; window offsets, extra
     shifts and right shifts that are zero on both sides of their boundary; a Gram stored lower-triangular with zeros
     above the diagonal; `i ^ j != i` for `j > 0`; a fifth Minkowski slot that never passes its test; the C
     `clz_c` stop, implied by `clz_a` once `a <= c`; the all-ones square-root window below `-48`; a const write the
     next one overwrites), working bounds with slack (value-preserving one-bit wider bounds in ideal equivalence,
     response sampling, Gram materialisation and secret-key decoding, a block Gram scaled two bits coarser, which
     still selects the same transforms, and Lagrange-Gauss outer iteration counts,
     whose proven worst cases the reductions finish inside), or unreached (a response norm exactly equal to the
     radius, and the signing retry when the auxiliary ideal norm is not coprime to the secret or challenge norm).
     `partial_xgcd_lehmer` at `rho == DIM2_THRESHOLD`, where only the word step's swap permission differs, survived
     1,000 threshold-sized samples because later outer iterations finish the reduction either way; a constructed
     input compared after a single outer iteration now kills it.

   Coverage (T3) is gated at 100% lines, functions and regions with `cargo llvm-cov` on the pinned stable toolchain.
   Rust branch coverage needs a nightly compiler; regions count both arms of every condition and stand in for it.
5. The Kotlin project selects the backend at build time with the Gradle property `sqisign.backend` (`c` default,
   `rust` opt-in), passed to `kotlin/native/CMakeLists.txt` as `SQISIGN_KMP_BACKEND`. The Rust backend
   (`kotlin/native/rust.cmake`) builds the `sqisign-capi` crate as a static library for the CMake target's Rust triple
   (fat LTO, one codegen unit) and links it where the C build links the vendor libraries. The crate exports the
   `sqisign_kmp_*` functions of `kotlin/native/include/sqisign_kmp.h` with the same status codes and length rules, so
   the JNI glue, the KAT glue, the cinterop definitions and the Kotlin API do not change. Its 48-byte key generation
   and signing seeds come from the vendor `randombytes`, linked from the same vendor sources as the C backend
   (`randombytes_system.c` for the shipped libraries, the NIST CTR-DRBG for the KAT libraries); this keeps the KAT
   seam of ADR 0002 and adds no randomness crate. A panic (for example a well-sized but malformed secret key, on
   which C has undefined behaviour) is caught at the ABI boundary and returned as `SQISIGN_KMP_ERROR`; secret seeds
   and key buffers are cleared with volatile writes there. This boundary holds the only `unsafe` code shipped; CI interprets
   its pointer reads, failure paths and clearing with Miri on a pinned nightly (`sqisign-capi/tests/miri.rs`).
6. The C backend stays the default and the only release backend until an expert review of the Rust port is recorded
   in a new version of this ADR. Scope, claims and evidence for that review:
   [`rust/REVIEW.md`](../../../rust/REVIEW.md).
7. SHAKE256 (Keccak-f[1600]) is ported in-crate rather than taken from a hashing crate, keeping the Rust core free of
   third-party dependencies (E3). It is tested against FIPS 202 vectors and against the vendor
   `src/common/generic/fips202.c`.
8. Integers (`mp`) keep the C `ibz_t` model instead of an arbitrary-precision crate: a two's-complement value over a
   fixed number of 64-bit limbs per parameter set (`IBZ_NLIMBS`: 30, 40, 55, also the storage size of each `Ibz`;
   only scratch buffers are sized for the largest set) with a bit bound that operations widen,
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
   return the value with a success flag, since callers use the value either way. The constant-time layer (`ibz_ct_*`) has one masked
   shift for `ibz_ct_shift`, `ibz_ct_shl`, `ibz_ct_highmul_p` and `ibz_ct_highmul_s` (C has a left-only and a
   right-only variant with the same results), tested against all four. Two C steps that cannot change a result
   are dropped: clearing the product sign for a zero operand, and widening bounds inside the `ibz_ct_fp_recip`
   Newton loop, whose products depend only on the values.
9. The SHAKE256 PRNG (`common/generic/prng.c`) has no global state: a `Prng` owns the 48-byte root seed and the
   default stream, and each `Domain` stream (`SHAKE256(seed || separator)`) is an owned value passed to the sampling
   functions, so the C unseeded and malformed-separator errors cannot occur and separators are an enum. The seed
   comes from the caller (C `randombytes`) by reference, so the caller's buffer is the only copy. The seed and the
   SHAKE256 states of the domain streams are cleared on drop, as C `prng_domain_clear`, best effort (a store
   followed by `black_box`) without `unsafe` volatile writes; the secret-key encoding clears its temporaries, as
   `encode_secret.c`. `ibz_rand_interval*` keep the C draw width, masking and rejection, so a seeded run consumes the same
   bytes and returns the same values; the rejection retry (probability about `2^-64`) is unit-tested on fixed
   bytes. The `sqisign-reference` crate links the vendor NIST CTR-DRBG `randombytes` (`common/ref`) instead of
   system randomness, so the C PRNG can be seeded reproducibly in the differential tests.
10. Primality and modular square roots (`ibz_probab_prime`, `ibz_sqrt_m1_mod_verified`, `ibz_sqrt_mod_p`) use a
    64-bit Montgomery context with fully reduced residues instead of the C 61-bit `modqx` radix with lazy residues:
    C reduces every residue it returns or compares, and the results depend only on the inputs and the PRNG bytes, so
    Rust follows the C steps (sieve to 509, Miller-Rabin to base 2, strong Lucas-Selfridge, then `reps - 24` drawn
    witnesses; Tonelli-Shanks with a drawn non-residue) and draws the same bytes. Rust also returns `false` where the
    C Montgomery context does not fit (`(bitsize + 63) / 61 >= MODQLIMBS`), a cutoff tested on primes on both sides.
    Failed square roots return `None` instead of the C partly written output. `ibz_pow_mod` and `ibz_sqrt_m1_mod`
    have no production callers and are not ported. Drawn witnesses need `self` 65 bits below capacity: C then
    overflows its sampling buffer under `NDEBUG`, Rust panics, and the tests stay inside the C domain.
11. The quaternion module (`src/quaternion`) is ported only where signing, key generation or the secret-key encoding
    reach it, following the C operation order so every result carries the C bound. Its vectors and matrices are
    fixed-size arrays of `Ibz` returned by value instead of the C output parameters. Where C leaves an output
    untouched on failure (`ibz_mat_2x2_inv_with_det_as_denom` on a singular matrix), Rust returns `None`. Functions
    that need a later layer (`ibz_cornacchia_prime` needs 2-dimensional LLL, `quat_alg_make_primitive` needs lattice
    membership) are ported with that layer. Functions only called from C assertions (`ibz_mat_4x4_is_triangular`)
    are not ported. The precomputed `QUATALG_PINFTY` and `MAXORD_O0` are built from the field prime and from
    `quat_lattice_O0_set`, which C precomputes with the same values and bounds; the tests compare them with the C data.
    The constant-time reductions (`lll/`) keep the C fixed-point precisions, working bounds and data-independent
    control flow, including the mask-based candidate selection and the bitonic sort. Outputs C computes but never
    reads (the Gram exponent and step count of the 2x2 Lehmer step) are dropped. The reductions are specified only
    for products of two `O0` ideals of bounded norm, the signing-path input, so the tests draw those inputs with the
    C generators (`ibz_generate_random_prime`, `quat_random_ideal_O0_given_prime_norm`, `quat_ideal_mul_O0`).
12. `O0` ideals (`ideal.c`, `quat_represent_integer`, `quat_lattice_sample_from_ball`, `quat_response_element`) take
    their randomness through a `Draw`, the caller's stream plus the default stream that C reaches implicitly
    (`ibz_probab_prime`, `ibz_sqrt_mod_p`, `PRNG_default_domain`), so seeded runs draw the same bytes as C.
    `quat_ideal_create_O0_inert_odd` writes its output only when its final gcd is 1; the vendor Gaussian gcd clamps
    remainder bounds to the divisor's and can miss a split factor, so this fails for some inputs (for example a `gen`
    with zero `j`-part), and Rust returns `None` for the unwritten ideal. Every C caller of
    `quat_ideal_small_equivalent_coprime` passes `equiv` aliasing `ideal`, so Rust keeps the input ideal there. The
    split norm of `quat_ideal_create_O0_odd` need not divide the norm, also at signing sizes (1 of 4 sampled
    `quat_random_ideal_O0_given_arbitrary_odd_norm` calls at the 324-bit prime); C asserts it does, and its `NDEBUG`
    build, which Rust follows, continues with the truncated quotient. Where that quotient is zero (seen for ideal norms
    far below `p`), C divides by zero; Rust panics. `quat_ideal_shortest_equivalent` is ported without its unused `gen`
    output (its only caller passes `NULL`), and checks that cannot fail are dropped: the `assert`-only
    `quat_alg_make_primitive` in `quat_ideal_create_O0_odd`, and zero-generator checks on elements whose sampled
    coordinates are at least 1.
13. The norm equation (`qlapoty.c`) draws only from the default stream, as C does through `ibz_rand_interval` and
    `ibz_probab_prime`. It keeps two C behaviours the comments do not describe: the `initialize_bounds` power loop
    squares, so it computes `(2 ± i)^(2^(f-1))`, and `quat_qlapoty_loop_one` with a short interval fixes the Gaussian
    factor and retries forever if that factor is rejected (as C does; the tests avoid moduli divisible by 5). Where C
    returns 0 (`quat_qlapoty_initialize_bounds`, `quat_qlapoty_loop_two`, `quat_qlapoty_normeq`, `quat_qlapoty`), Rust
    returns `None`. The port found a fault in the earlier Rust `ibz_cornacchia_prime`: it always swapped the reduced
    pair, but C swaps only when `x > y`, and a `qlapoty` input at the 664-bit prime reaches the unswapped case; it is
    now a regression input of the Cornacchia tests.

14. The ideal-to-isogeny translation (`id2iso.c`, `dim2id2iso.c`) is ported for the functions key generation and
    signing call. Its randomness comes from the default stream, as in C. Where C reports failure through a flag and
    its callers stop (`dim2id2iso_ideal_to_isogeny_qlapoty`, `dim2id2iso_arbitrary_isogeny_evaluation`), Rust
    returns `None`; where C aborts (a failed norm equation) or only asserts (a non-cyclic kernel ideal in
    `id2iso_kernel_dlogs_to_ideal_even`), Rust panics, which signing never reaches on a valid secret key.
15. Key generation and signing (`keygen.c`, `sign.c`, `encode_secret.c`, `sqisign.c`) take the 48-byte seed that C's
    `prng_seed` draws from `randombytes`, so the production API takes no seed only at the C ABI (item 5). With the
    seeds the NIST DRBG produces, they reproduce every key pair and signature of the 300 KAT entries byte for byte.
    C continues past some failures of negligible probability with unwritten outputs (undefined behaviour); Rust
    makes every such failure a signing failure (`None`), which the C ABI reports as `SQISIGN_KMP_ERROR`. Rust also
    fails signing when the response element has negative norm. The norm form of a valid secret ideal is positive
    definite, so this needs a malformed secret key; C then represents a huge auxiliary norm in about `aux_norm / p`
    attempts, which do not end in practice.

16. Constant-time masks pass through an optimisation barrier where C applies `ct_barrier_*` (`mp_ct.h`), and in the
    field add, subtract, select and swap masks, which C leaves to its compiler. The barrier is `core::hint::black_box`,
    the only one available without `unsafe`, so it is best effort; it costs about 6–10 % in key generation, signing and
    verification. The field add and subtract reduction masks are instead ORed with one `black_box(0)`, which is as
    opaque but keeps the stack round trip of `black_box` off the carry chain; the subtraction borrow mask keeps the
    direct barrier, since LLVM branches on the borrow behind an opaque zero on 32-bit targets. On AArch64 the
    backend's select optimisation is off (`rust/.cargo/config.toml`): with the generic tuning of Android it turned
    the carries of the field carry chains into branches, which dudect measured (|t| up to 400 on the Cortex-A710).
    The machine scheduler is off there too, for speed: it moved the products of the Montgomery multiplication ahead
    of the carry chains and spilled them to the stack.
    `rust/scripts/ct_branches.py` builds the shipped configuration (fat LTO, one codegen unit),
    disassembles it and fails on any conditional branch attributed to the constant-time code of `gf`, `mp` and
    `quat/lll`, counting standard library arithmetic (`carrying_add`) inlined there as that code; CI runs it on
    `aarch64` (macOS and Android), `x86_64`, `armv7` and `i686`. A dudect-style example (`examples/dudect.rs`)
    measures the field and `ibz_ct_*` operations; its results and limits are in
    [`rust/REVIEW.md`](../../../rust/REVIEW.md#gaps-known-before-review).

## Alternatives

- Depend on `anchorageoss/sqisign-rs`: not chosen. An external, unaudited implementation by a single author; the
  project wants an implementation it controls and can check against the vendor C module by module.
- Replace the C backend immediately: rejected, violates S6.
- Pure Kotlin implementation: rejected in ADR 0001 (performance, constant time on the JVM).

## Risks

- A port can reproduce test vectors and still differ from the C code on rare inputs. Differential tests on random and
  boundary inputs reduce this; they do not prove equivalence.
- Constant-time behavior of the Rust field code depends on the compiler. Barriers and the CI branch check (item 16)
  cover the four Android ABIs and the 64-bit desktop targets, not memory indices; the timing measurements cover an
  Apple M1 and 37 cores of 20 Android devices, where the field operations show input-dependent timing on some cores
  in Rust and in the vendor C alike. The expert review judges whether that suffices. The AArch64 code generation
  setting (item 16) is read from `rust/.cargo/config.toml`; a `RUSTFLAGS` variable replaces it, so a build that sets
  one must repeat it.
- Signing is variable-time in the C code (lattice reduction); the port inherits that.
- The port also inherits C behaviour on inputs outside the documented domain, for example `pairing_dlog_2_tate`
  with `e = 1` can return logarithms that do not satisfy the relation. The differential tests pin this behaviour; the
  callers never use such inputs.
- The vendor Gaussian gcd (`ibz_vec_2_gaussian_gcd`) clamps remainder bounds, so ideal construction can fail or skip
  a C assertion; the port follows the `NDEBUG` build and matches it in the tests, including at signing-path norm
  sizes. It panics only where C divides by zero, seen for small ideal norms; no such case was found at the
  signing-path sizes, which is evidence, not proof.
- The port takes 0.90–1.27× the time of the C `ref` build and 1.41–1.71× that of the shipped C `arm64` build in the JMH
  benchmarks, and 1.53–1.90× that of C `arm64` on an Android device
  ([`rust/README.md`](../../../rust/README.md#performance)), before the 6–10 % of the barriers (item 16). With the
  barriers, the carry-chain field kernels, the interleaved Montgomery multiplication, the opaque-zero add/subtract
  masks, the unreduced Fp2 sums, the faster primality test and without the machine scheduler it takes 1.00–1.39× the
  time of C `arm64` on a
  Cortex-A710 core, so it would still fail the 10 % budget (P4) today.
- Two backends double the platform test matrix while both exist.

## Migration

None for consumers: the C backend remains the default and the public API does not change. Switching the default to
Rust requires a new ADR version after the expert review, the full Kotlin test matrix on the Rust backend and a
benchmark comparison against the committed C baseline (P3/P4).
